package com.verve.accept;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Disabled("Run manually for load testing")
public class LoadTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private final Random random = new Random();

    @Test
    public void shouldHandle10KRequestsPerSecond() throws InterruptedException {
        // Given
        int targetRequests = 10_000;
        int threadCount = 100;
        int requestsPerThread = targetRequests / threadCount;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Long> responseTimes = new ArrayList<>();

        // When
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        int id = random.nextInt(1000);
                        long requestStart = System.nanoTime();

                        ResponseEntity<String> response = restTemplate.getForEntity(
                                "http://localhost:" + port + "/api/verve/accept?id=" + id,
                                String.class);

                        long requestEnd = System.nanoTime();
                        long requestTime = (requestEnd - requestStart) / 1_000_000; // ms

                        assertEquals(200, response.getStatusCodeValue());
                        assertEquals("ok", response.getBody());

                        synchronized (responseTimes) {
                            responseTimes.add(requestTime);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Calculate statistics
        double requestsPerSecond = (targetRequests * 1000.0) / totalTime;
        double averageResponseTime = responseTimes.stream()
                .mapToLong(Long::valueOf)
                .average()
                .orElse(0);

        // Then
        assertTrue(completed, "Test didn't complete within expected time");
        assertTrue(requestsPerSecond >= 10_000,
                "Performance below target: " + requestsPerSecond + " req/s");

        System.out.println("==== Load Test Results ====");
        System.out.println("Total requests: " + targetRequests);
        System.out.println("Total time: " + totalTime + " ms");
        System.out.println("Throughput: " + requestsPerSecond + " req/s");
        System.out.println("Average response time: " + averageResponseTime + " ms");

        executor.shutdown();
    }
}
