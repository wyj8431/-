# All+poster

All+poster is a small-business poster workbench. The current vertical slice covers the Java 21/Spring Boot backend and the Vue 3 template-discovery workbench: public template browsing, search, detail, responsive layout, login intent recovery, authenticated design creation, revocable browser sessions, and the first tenant administration shell.

## P0 Workbench

The P0 workbench phase covers home and template discovery. The editor, free canvas, membership, collaboration, customer service, AI, and video routes remain intentionally unavailable.

## P1 Session And Admin

P1 adds an in-memory access-token session backed by an HttpOnly `poster_refresh_token` cookie. The browser restores a valid session through `/api/v1/auth/refresh`; a failed request receives one refresh-and-retry attempt, while failed refreshes clear the local identity. Login, refresh, logout, replay detection, and tenant-role changes are audited server-side.

Phone verification remains the only sign-in flow. A successfully verified new phone number is registered automatically with a default tenant and `ADMIN` membership, so there is no separate registration page or API.

Tenant membership roles are `ADMIN`, `OPERATOR`, and `USER`. Signed-in administrators and operators can open **团队管理** from the account menu and enter `/admin`; a normal user navigating there sees an in-page 403 state. The administration shell now links to the overview, member and role management, template operations, template categories, template tags, and the paginated audit-event list. Administrators can change existing templates, categories, and tags between `DRAFT`, `PUBLISHED`, and `DISABLED`; operators have read-only access. Each status change is audited. Audit CSV export is available. Template creation/editing, tag CRUD, covers, assets, and the WeChat sign-in adapter remain incomplete.

Run the frontend from `poster-client/`:

```powershell
cd poster-client
npm install
npm run dev
```

The Vite dev server is available at `http://localhost:5173` and proxies `/api` to the local backend at `http://localhost:8080`.

Frontend verification:

```powershell
npm run test:unit
npm run build
npm run test:e2e
```

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
