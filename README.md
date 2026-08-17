# Poster Platform Backend

Java 21 and Spring Boot backend for the poster-design MVP. The current vertical slice covers phone-code login, published templates, versioned design documents, and validated image uploads.

## Local Run

Prerequisites: Docker Desktop, Docker Compose, and JDK 21.

From the repository root:

```powershell
docker compose -f infra/docker-compose.yml up -d --wait mysql redis minio rabbitmq
docker compose -f infra/docker-compose.yml up -d minio-init
docker compose -f infra/docker-compose.yml wait minio-init
$env:SPRING_PROFILES_ACTIVE='local'
$env:JWT_SECRET='local-development-secret-at-least-32-bytes'
cd server
.\mvnw.cmd spring-boot:run
```

The first command waits until MySQL, Redis, MinIO, and RabbitMQ are ready. The second and third commands create the local `lowcode-assets` bucket and wait for that one-shot job to finish successfully. It is normal for `minio-init` to show `Exited (0)` afterwards.

The local phone verification code is `123456` unless `LOCAL_VERIFICATION_CODE` is set.

## Local URLs

- API documentation: `http://localhost:8080/swagger-ui/index.html`
- Health: `http://localhost:8080/actuator/health`
- MinIO console: `http://localhost:9001`
- RabbitMQ console: `http://localhost:15672`

Local infrastructure credentials in `infra/docker-compose.yml` are development-only. Do not reuse them outside a local machine.

## Verification

Run the test suite from `server/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Dtest=DatabaseMigrationIT test
.\mvnw.cmd clean verify
```

Run these commands with JDK 21. If `java -version` points elsewhere, set `JAVA_HOME` to the JDK 21 installation before invoking Maven. `DatabaseMigrationIT` is intentionally invoked explicitly because Maven Surefire does not discover `*IT` classes in its default test pattern.

To stop local services while preserving local data:

```powershell
docker compose -f infra/docker-compose.yml down
```
