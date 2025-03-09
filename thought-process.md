# Thought Process and Design Considerations

## Overview

This document outlines the design and implementation choices made for the high-performance REST service capable of processing 10K+ requests per second, with unique ID tracking and endpoint notification capabilities.

## Core Requirements Analysis

1. GET endpoint `/api/verve/accept` accepting:
   - Mandatory integer `id` parameter
   - Optional string `endpoint` parameter
   - Returns "ok" or "failed"

2. One-minute tracking window:
   - Count unique requests based on ID parameter
   - Log the count every minute

3. Conditional endpoint notification:
   - When endpoint is provided, make GET/POST request with count data
   - Log HTTP status code of response

## High-Level Architecture

The implementation uses Spring Boot with the following components:

1. **VerveController**: Handles incoming HTTP requests
2. **RequestTracker**: Manages request deduplication and counting
3. **EndpointNotifier**: Handles external endpoint notifications

## Performance Considerations

To achieve 10K+ requests per second:

1. **Non-blocking operations**:
   - Asynchronous endpoint notifications
   - Minimal processing in the request path

2. **Efficient data structures**:
   - `ConcurrentHashMap` for thread-safe operations
   - `ConcurrentHashMap.newKeySet()` for unique ID tracking

3. **Resource optimization**:
   - Connection pooling for HTTP clients
   - Thread pool sizing based on available processors

## Extension 1: POST Instead of GET

For the endpoint notification:
- Implemented POST request with JSON payload
- Created a simple data structure `RequestData` to hold unique count
- Using RestTemplate for HTTP communication

## Extension 2: Distributed Deduplication

To handle deduplication with multiple service instances:

1. **Conceptual approach**: 
   - In a production environment, replace the in-memory structures with a distributed cache (Redis)
   - This would ensure atomic operations across instances
   - For demonstration purposes, the code includes comments indicating where Redis would be integrated

2. **Implementation considerations**:
   - Minute-based keys to partition data
   - Atomic operations using Redis SET operations

## Extension 3: Distributed Streaming

Instead of just logging:

1. **Kafka integration**:
   - Implemented a Kafka producer
   - Sends unique ID counts to a topic named "verve-unique-counts"
   - Uses minute timestamp as the record key

2. **Error handling**:
   - Fail-safe design that continues operation even if Kafka is unavailable
   - Logs errors but doesn't affect the primary request path

## Additional Optimizations

1. **Memory efficiency**:
   - Automatic cleanup of old minute data
   - Time-based partitioning to limit memory growth

2. **Scalability**:
   - Stateless design allows horizontal scaling
   - Independent scaling of processing and notification components

3. **Resilience**:
   - Circuit breaking for external endpoint calls
   - Graceful degradation if dependencies fail

## Testing Approach

For comprehensive testing, I would implement:

1. Unit tests for each component
2. Integration tests for component interactions
3. Load tests to verify 10K+ req/sec capability
4. Chaos tests to verify resilience to failures

## Deployment Considerations

The application can be:
1. Packaged as an executable JAR
2. Containerized with Docker for easy deployment
3. Deployed behind a load balancer for horizontal scaling

## Future Improvements

1. Circuit breaker implementation for endpoint notifications
2. Rate limiting to protect downstream systems
3. Metrics collection for operational insights
4. Dynamic configuration for runtime tuning


## How to Run

1.Build the application:
mvn clean package

Run as JAR:
java -jar target/verve-service-1.0.0.jar

Run with Docker:
docker build -t verve-service .
docker run -p 8080:8080 verve-service


Testing the Service
Test the service with curl:
curl "http://localhost:8080/api/verve/accept?id=123"
curl "http://localhost:8080/api/verve/accept?id=123&endpoint=http://example.com/callback"
The load test included can verify the service meets the 10K requests/second requirement.
