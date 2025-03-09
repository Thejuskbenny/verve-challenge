package com.verve.accept.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class EndpointNotifier {

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
