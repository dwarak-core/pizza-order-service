# Pizza Order Service

A pizza restaurant order builder and order management service. Customers can assemble orders, watch the total update in real time, submit orders, and track their status through the restaurant's workflow.

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | HTML, CSS, Vanilla JavaScript |
| Backend | Java 21, Spring Boot 3.x, REST API |
| Database | SQLite (via Spring Data JPA + Hibernate) |
| Build | Gradle (Kotlin DSL) |
| Containerization | Docker, Docker Compose |
| Testing | JUnit 5, Spring Boot Test |

## How to Run

### Prerequisites

- Java 21+
- Docker & Docker Compose (optional)

### Run Locally

```bash
./gradlew bootRun
```

The application will start at `http://localhost:8080`.

### Run with Docker

```bash
docker compose up --build
```

The application will be available at `http://localhost:8080`.

### Run Tests

```bash
./gradlew test
```

## API Documentation

*Coming in Phase 3.*

## Assumptions

*This section will be updated as development progresses and ambiguities are resolved.*

## Design Write-Up

*Coming in Phase 6.*

## Test Coverage

*Coming in Phase 3.*

## AI Usage Note

*Coming in Phase 6.*

