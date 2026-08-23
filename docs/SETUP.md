# Setup Instructions

## Prerequisites
- Java 17
- Maven
- Docker Desktop (running)

## Steps

1. Clone/open the project in your IDE.
2. Ensure Docker Desktop is running.
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```
   or click Run in your IDE.

   This automatically:
   - Starts a MySQL 8.0 container via `docker-compose.yml`
   - Creates the `urlshortener` database
   - Runs `schema.sql` to create tables (`url_mapping`, `click_event`)
   - Starts the Spring Boot application on port `8080`

4. Verify it's running:
   ```bash
   curl -X POST http://localhost:8080/api/urls \
     -H "Content-Type: application/json" \
     -d '{"originalUrl": "https://example.com"}'
   ```
   Should return a JSON response with a `shortUrl`.

## Running Tests
```bash
mvn test
```
Tests run against an in-memory H2 database (`application-test.properties`) — no Docker/MySQL dependency required for tests.

## Configuration

Key properties (`application.properties`):
```properties
app.base-url=http://localhost:8080/
app.rate-limit.max-requests-per-minute=20
```

## Stopping

```bash
docker-compose down       # stop container, keep data
docker-compose down -v    # stop container, wipe data (fresh schema.sql re-run on next start)
```
