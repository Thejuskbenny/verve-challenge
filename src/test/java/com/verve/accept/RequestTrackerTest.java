package com.verve.accept;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RequestTrackerTest {

    @Mock
    private KafkaProducer<String, String> kafkaProducer;

    private RequestTracker requestTracker;
    private ConcurrentMap<String, Set<Integer>> minutelyUniqueIds;
    private String testMinuteKey = "12345";

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        requestTracker = new RequestTracker(kafkaProducer);

        // Set the current minute key for testing
        Field minuteKeyField = RequestTracker.class.getDeclaredField("currentMinuteKey");
        minuteKeyField.setAccessible(true);
        minuteKeyField.set(requestTracker, testMinuteKey);

        // Access the internal map for verification
        Field mapField = RequestTracker.class.getDeclaredField("minutelyUniqueIds");
        mapField.setAccessible(true);
        minutelyUniqueIds = (ConcurrentMap<String, Set<Integer>>) mapField.get(requestTracker);

        // Initialize minutely map
        minutelyUniqueIds.put(testMinuteKey, ConcurrentHashMap.newKeySet());
    }

    @Test
    void shouldTrackUniqueRequests() {
        // Given
        int id1 = 123;
        int id2 = 456;
        int id3 = 123; // Duplicate

        // When
        requestTracker.trackRequest(id1);
        requestTracker.trackRequest(id2);
        requestTracker.trackRequest(id3);

        // Then
        Set<Integer> uniqueIds = minutelyUniqueIds.get(testMinuteKey);
        assertEquals(2, uniqueIds.size());
        assertEquals(2, requestTracker.getCurrentUniqueCount());
    }

    @Test
    void shouldSendUniqueCountToKafka() throws Exception {
        // Given
        String previousMinuteKey = String.valueOf(Long.parseLong(testMinuteKey) - 1);
        Set<Integer> previousMinuteIds = ConcurrentHashMap.newKeySet();
        previousMinuteIds.add(1);
        previousMinuteIds.add(2);
        previousMinuteIds.add(3);
        minutelyUniqueIds.put(previousMinuteKey, previousMinuteIds);

        // When
        requestTracker.logUniqueRequestsCount();

        // Then
        verify(kafkaProducer, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void shouldCleanupOldData() {
        // Given
        String previousMinuteKey = String.valueOf(Long.parseLong(testMinuteKey) - 1);
        Set<Integer> previousMinuteIds = ConcurrentHashMap.newKeySet();
        previousMinuteIds.add(1);
        minutelyUniqueIds.put(previousMinuteKey, previousMinuteIds);

        // When
        requestTracker.logUniqueRequestsCount();

        // Then
        assertEquals(false, minutelyUniqueIds.containsKey(previousMinuteKey));
    }
}
