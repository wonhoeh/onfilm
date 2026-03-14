# Onfilm

Onfilm is a Spring Boot application for profile management, filmography editing, movie metadata management, and media upload workflows including asynchronous encoding.

## Tech Stack

- Java 17
- Spring Boot 3.3
- Spring Data JPA
- Spring Security
- H2 for local development
- MySQL for production
- AWS S3 for object storage
- Apache Kafka for media encode job dispatch

## Main Features

- Authentication with access token and refresh token rotation
- Profile and filmography management
- Movie, trailer, and thumbnail upload
- Presigned URL based S3 direct upload
- Kafka based asynchronous media encoding job flow
- Job status polling API for frontend completion tracking

## Run Local

Requirements:

- Java 17
- Gradle wrapper

Run:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Local profile defaults:

- DB: H2 in-memory
- File storage: local
- Kafka producer: disabled unless `spring.kafka.bootstrap-servers` is configured

## Profiles

### dev

- H2 in-memory database
- local file storage at `./local-storage`
- public file base URL: `http://localhost:8080/files`

### prod

- MySQL
- S3 object storage
- Kafka producer enabled with `spring.kafka.bootstrap-servers`

## Required Environment Variables For Production

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `KAFKA_PRODUCER_IP`
- `S3_BUCKET`
- `S3_REGION`
- `S3_ACCESS_KEY`
- `S3_SECRET_KEY`
- `S3_PUBLIC_BASE_URL`

## Authentication

- Access token is sent via `Authorization: Bearer <token>`
- Refresh token is stored in an HttpOnly cookie named `refresh_token`
- Refresh token rotation is enforced on `/auth/refresh`
- Refresh cookie is non-secure in `dev` and secure in `prod`

## Media Upload Flow

1. Frontend requests a presigned upload URL.
2. Frontend uploads the raw file directly to S3.
3. Frontend calls the complete API.
4. Server stores a `REQUESTED` encode job and publishes a Kafka message.
5. Consumer processes the encode request and stores the result.
6. Frontend polls `GET /api/media-jobs/{jobId}` until the job finishes.

## Documentation

- Consumer development spec: [docs/consumer-dev-spec.md](docs/consumer-dev-spec.md)

## Current Notes

- Asynchronous media encoding is designed around Kafka job dispatch.
- Detailed consumer-side implementation rules, preset design, and target key design are documented in `docs/consumer-dev-spec.md`.
