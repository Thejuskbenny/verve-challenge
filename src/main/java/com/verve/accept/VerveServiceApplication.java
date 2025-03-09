package com.verve.accept;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;

@SpringBootApplication
@EnableScheduling
public class VerveServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VerveServiceApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public KafkaProducer<String, String> kafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        return new KafkaProducer<>(props);
    }
}

@RestController
class VerveController {

    private static final Logger logger = LoggerFactory.getLogger(VerveController.class);
    private final RequestTracker requestTracker;
    private final EndpointNotifier endpointNotifier;

    @Autowired
    public VerveController(RequestTracker requestTracker, EndpointNotifier endpointNotifier) {
        this.requestTracker = requestTracker;
        this.endpointNotifier = endpointNotifier;
    }

    @GetMapping("/api/verve/accept")
    public String acceptRequest(@RequestParam("id") int id,
                                @RequestParam(value = "endpoint", required = false) String endpoint) {
        try {
            requestTracker.trackRequest(id);

            if (endpoint != null && !endpoint.isEmpty()) {
                endpointNotifier.scheduleEndpointCall(endpoint);
            }

            return "ok";
        } catch (Exception e) {
            logger.error("Error processing request with id: {}", id, e);
            return "failed";
        }
    }
}

@Component
class RequestTracker {

    private static final Logger logger = LoggerFactory.getLogger(RequestTracker.class);

    // Using Redis or other distributed cache in a real environment for Extension 2
    private final ConcurrentMap<String, Set<Integer>> minutelyUniqueIds = new ConcurrentHashMap<>();
    private final KafkaProducer<String, String> kafkaProducer;
    private volatile String currentMinuteKey;

    @Autowired
    public RequestTracker(KafkaProducer<String, String> kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
        this.currentMinuteKey = generateMinuteKey();
        initializeCurrentMinute();
    }

    @PostConstruct
    public void init() {
        // Schedule a task to update the current minute key
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::updateMinuteKey, 1, 1, TimeUnit.MINUTES);
    }

    private void updateMinuteKey() {
        String newMinuteKey = generateMinuteKey();
        if (!newMinuteKey.equals(currentMinuteKey)) {
            currentMinuteKey = newMinuteKey;
            initializeCurrentMinute();
        }
    }

    private void initializeCurrentMinute() {
        minutelyUniqueIds.putIfAbsent(currentMinuteKey, ConcurrentHashMap.newKeySet());
    }

    private String generateMinuteKey() {
        long currentTimeMillis = System.currentTimeMillis();
        return String.valueOf(currentTimeMillis / (60 * 1000));
    }

    public void trackRequest(int id) {
        minutelyUniqueIds.get(currentMinuteKey).add(id);
    }

    public int getCurrentUniqueCount() {
        return minutelyUniqueIds.get(currentMinuteKey).size();
    }

    @Scheduled(fixedRate = 60000) // Run every minute
    public void logUniqueRequestsCount() {
        String previousMinuteKey = String.valueOf(Long.parseLong(currentMinuteKey) - 1);
        Set<Integer> uniqueIds = minutelyUniqueIds.get(previousMinuteKey);

        if (uniqueIds != null) {
            int uniqueCount = uniqueIds.size();

            // Log to file (Extension 1)
            logger.info("Unique requests in the previous minute: {}", uniqueCount);

            // Send to Kafka (Extension 3)
            sendToKafka(uniqueCount);

            // Clean up old data
            minutelyUniqueIds.remove(previousMinuteKey);
        }
    }

    private void sendToKafka(int count) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    "verve-unique-counts",
                    currentMinuteKey,
                    String.valueOf(count)
            );
            kafkaProducer.send(record);
            logger.info("Sent unique count {} to Kafka", count);
        } catch (Exception e) {
            logger.error("Failed to send data to Kafka", e);
        }
    }
}

@Component
class EndpointNotifier {

    private static final Logger logger = LoggerFactory.getLogger(EndpointNotifier.class);

    private final RequestTracker requestTracker;
    private final RestTemplate restTemplate;
    private final ScheduledExecutorService scheduler;

    @Autowired
    public EndpointNotifier(RequestTracker requestTracker, RestTemplate restTemplate) {
        this.requestTracker = requestTracker;
        this.restTemplate = restTemplate;
        this.scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public void scheduleEndpointCall(String endpoint) {
        // Debounce and batch requests to the same endpoint
        scheduler.schedule(() -> notifyEndpoint(endpoint), 100, TimeUnit.MILLISECONDS);
    }

    // Extension 1: POST request instead of GET
    private void notifyEndpoint(String endpoint) {
        try {
            int uniqueCount = requestTracker.getCurrentUniqueCount();

            // Create POST request with JSON payload
            RequestData requestData = new RequestData(uniqueCount);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint,
                    requestData,
                    String.class
            );

            HttpStatus statusCode = (HttpStatus) response.getStatusCode();
            logger.info("Notification to endpoint {} returned status: {}", endpoint, statusCode);
        } catch (Exception e) {
            logger.error("Failed to notify endpoint {}", endpoint, e);
        }
    }

    // Data structure for POST request
    static class RequestData {
        private final int uniqueCount;

        public RequestData(int uniqueCount) {
            this.uniqueCount = uniqueCount;
        }

        public int getUniqueCount() {
            return uniqueCount;
        }
    }
}