## How to Run

Build the application:
mvn clean package

Run as JAR:
java -jar target/accept-1.0.0.jar

Run with Docker:
docker build -t verve-service .
docker run -p 8080:8080 verve-service

Testing the Service

curl "http://localhost:8080/api/verve/accept?id=123"
curl "http://localhost:8080/api/verve/accept?id=123&endpoint=http://example.com/callback"
The load test included can verify the service meets the 10K requests/second requirement.
