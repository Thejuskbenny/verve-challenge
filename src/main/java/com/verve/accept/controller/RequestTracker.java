package com.verve.accept.controller;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.*;

@Component
public class RequestTracker {

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