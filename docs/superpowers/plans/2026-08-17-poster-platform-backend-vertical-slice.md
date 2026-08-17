# Poster Platform Backend Vertical Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable Java backend vertical slice for phone-code login, template-based design creation, optimistic versioned JSON saving, and safe MinIO image upload.

**Architecture:** A single Spring Boot modular monolith lives in `server/`. Feature packages own their controllers, application services, domain models, repository ports, and MyBatis/MinIO adapters. MySQL stores immutable design versions and upload sessions; Spring Security JWT carries user and tenant identity; Schema validation happens before every persisted design change.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Security OAuth2 Resource Server, MyBatis-Plus 3.5.17, MySQL 8.4, Flyway, MinIO Java SDK 8.6.0, springdoc-openapi 2.9.0, JUnit 5, Testcontainers 1.21.4, Docker Compose.

---

## Scope

This plan implements one end-to-end backend slice:

```text
phone code login
  -> list published templates
  -> create design from template
  -> read design version 1
  -> save valid DesignSchema as version 2
  -> reject a stale baseVersion with HTTP 409
  -> request a presigned image upload
  -> complete and validate the uploaded image
```

The Vue editor, browser export, Redis caching, RabbitMQ export worker, refresh-token persistence, WeChat login, AI Patch, realtime collaboration, and billing each require separate implementation plans.

## Locked File Structure

```text
server/
|-- pom.xml
|-- mvnw
|-- mvnw.cmd
|-- .mvn/wrapper/maven-wrapper.properties
|-- src/main/java/com/example/lowcode/
|   |-- LowCodeApplication.java
|   |-- common/
|   |   |-- api/ApiResponse.java
|   |   |-- api/ErrorResponse.java
|   |   |-- exception/BusinessException.java
|   |   |-- exception/ErrorCode.java
|   |   |-- exception/GlobalExceptionHandler.java
|   |   `-- web/TraceIdFilter.java
|   |-- auth/
|   |   |-- api/AuthController.java
|   |   |-- application/AuthService.java
|   |   |-- application/AuthRepository.java
|   |   |-- application/VerificationCodeVerifier.java
|   |   |-- domain/User.java
|   |   |-- infrastructure/LocalVerificationCodeVerifier.java
|   |   |-- infrastructure/MyBatisAuthRepository.java
|   |   |-- infrastructure/UserMapper.java
|   |   |-- security/CurrentUser.java
|   |   |-- security/JwtTokenService.java
|   |   `-- security/SecurityConfig.java
|   |-- template/
|   |   |-- api/TemplateController.java
|   |   |-- application/TemplateQueryService.java
|   |   |-- application/TemplateRepository.java
|   |   |-- infrastructure/MyBatisTemplateRepository.java
|   |   `-- infrastructure/DesignTemplateMapper.java
|   |-- design/
|   |   |-- api/DesignController.java
|   |   |-- application/DesignService.java
|   |   |-- application/DesignRepository.java
|   |   |-- domain/DesignSchemaValidator.java
|   |   |-- domain/DesignDocument.java
|   |   |-- infrastructure/MyBatisDesignRepository.java
|   |   |-- infrastructure/DesignDocumentMapper.java
|   |   |-- infrastructure/DesignPermissionMapper.java
|   |   `-- infrastructure/DesignVersionMapper.java
|   `-- asset/
|       |-- api/AssetController.java
|       |-- application/AssetService.java
|       |-- application/AssetRepository.java
|       |-- application/StorageGateway.java
|       |-- domain/ImageInspector.java
|       |-- infrastructure/MyBatisAssetRepository.java
|       |-- infrastructure/AssetMapper.java
|       |-- infrastructure/AssetUploadSessionMapper.java
|       `-- infrastructure/MinioStorageGateway.java
|-- src/main/resources/
|   |-- application.yml
|   |-- application-local.yml
|   `-- db/migration/V1__backend_vertical_slice.sql
|-- src/test/java/com/example/lowcode/
|   |-- common/api/ApiResponseTest.java
|   |-- auth/application/AuthServiceTest.java
|   |-- design/domain/DesignSchemaValidatorTest.java
|   |-- design/application/DesignServiceTest.java
|   |-- asset/application/AssetServiceTest.java
|   `-- integration/BackendVerticalSliceIT.java
|-- src/test/resources/application-test.yml
infra/
`-- docker-compose.yml
README.md
```

## Cross-Task Type Contract

The following records are nested public records in the named application service unless a separate file is listed. Tests import them from that service. Do not create competing DTOs with different field names.

```java
// auth/security/CurrentUser.java
public record CurrentUser(long userId, long tenantId) {}

// AuthService
public record LoginCommand(String phone, String verificationCode) {}
public record LoginResult(String accessToken, String tokenType, long expiresIn,
                          long userId, long tenantId) {}

// TemplateQueryService
public record TemplateFieldView(String fieldKey, String label, String fieldType,
                                boolean required, String defaultValue) {}
public record TemplateSummary(long id, String name, int width, int height,
                              Long coverAssetId, List<TemplateFieldView> fields) {}
public record TemplateDetail(long id, String name, int width, int height,
                             Long coverAssetId, JsonNode schema,
                             List<TemplateFieldView> fields) {}

// DesignService
public record CreateDesignCommand(long templateId, String name) {}
public record SaveDesignCommand(int baseVersion, JsonNode schema) {}
public record DesignView(long id, long templateId, String name, int width, int height,
                         int currentVersion, JsonNode schema, Instant updatedAt) {}
public record VersionView(long id, int versionNo, long createdBy, Instant createdAt) {}

// AssetService
public record PresignCommand(String fileName, String mimeType, long fileSize,
                             String sha256) {}
public record PresignResult(long uploadSessionId, String objectKey, URI uploadUrl,
                            Instant expiresAt) {}
public record AssetView(long id, String fileName, String objectKey, String mimeType,
                        long fileSize, String sha256, int width, int height) {}
```

Application services depend on these repository ports, not on MyBatis mappers:

```java
// AuthRepository.java
public interface AuthRepository {
    Optional<UserIdentity> findByPhone(String phone);
    UserIdentity createUserWithDefaultTenant(String phone);

    record UserIdentity(long userId, long tenantId, String phone) {}
}

// TemplateRepository.java
public interface TemplateRepository {
    List<TemplateQueryService.TemplateSummary> findPublished();
    Optional<TemplateQueryService.TemplateDetail> findPublishedById(long templateId);
}

// DesignRepository.java
public interface DesignRepository {
    long insertDocument(long tenantId, long ownerId, long templateId, String name,
                        int width, int height, int currentVersion);
    void insertVersion(long documentId, int versionNo, JsonNode schema, long createdBy);
    void insertPermission(long documentId, long userId, String role);
    Optional<DesignSnapshot> findSnapshot(long tenantId, long documentId);
    boolean canEdit(long tenantId, long documentId, long userId);
    boolean advanceVersion(long tenantId, long documentId, int baseVersion);
    List<DesignService.VersionView> findVersions(long tenantId, long documentId);

    record DesignSnapshot(long id, long templateId, String name, int width, int height,
                          int currentVersion, JsonNode schema, Instant updatedAt) {}
}

// AssetRepository.java
public interface AssetRepository {
    long insertUploadSession(long tenantId, long userId, String fileName, String mimeType,
                             long fileSize, String sha256, String objectKey, Instant expiresAt);
    Optional<UploadSession> findPendingSession(long tenantId, long userId, long sessionId);
    long completeSessionAndCreateAsset(UploadSession session, VerifiedImage image);
    void rejectSession(long sessionId, String reason);

    record UploadSession(long id, long tenantId, long userId, String fileName,
                         String mimeType, long fileSize, String sha256,
                         String objectKey, Instant expiresAt) {}
    record VerifiedImage(byte[] bytes, String mimeType, long fileSize, String sha256,
                         int width, int height) {}
}

// StorageGateway.java
public interface StorageGateway {
    URI presignPut(String objectKey, String contentType, Duration ttl);
    byte[] readBounded(String objectKey, long maxBytes);
    void delete(String objectKey);
}
```

`MyBatisAuthRepository`, `MyBatisTemplateRepository`, `MyBatisDesignRepository`, and `MyBatisAssetRepository` implement these ports and are the only classes in their modules allowed to coordinate multiple Mapper interfaces.

## Task 1: Bootstrap the Java 21 Application

**Files:**

- Create: `server/pom.xml`
- Create: `server/.mvn/wrapper/maven-wrapper.properties`
- Create: `server/mvnw`
- Create: `server/mvnw.cmd`
- Create: `server/src/test/java/com/example/lowcode/LowCodeApplicationTest.java`
- Create: `server/src/main/java/com/example/lowcode/LowCodeApplication.java`
- Create: `server/src/main/resources/application.yml`

- [ ] **Step 1: Create Maven configuration and wrapper**

Use Spring Boot `3.5.16` and Java 21. `pom.xml` must include these production dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
  </dependency>
  <dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.17</version>
  </dependency>
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
  </dependency>
  <dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
  </dependency>
  <dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.6.0</version>
  </dependency>
  <dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.9.0</version>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>1.21.4</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.21.4</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

Set the wrapper distribution URL to:

```properties
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar
```

- [ ] **Step 2: Write the failing application test**

```java
package com.example.lowcode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LowCodeApplicationTest {
    @Test
    void applicationEntryPointExists() {
        assertThat(LowCodeApplication.class).isNotNull();
    }
}
```

- [ ] **Step 3: Run the test and verify RED**

Run from `server/`:

```powershell
.\mvnw.cmd -Dtest=LowCodeApplicationTest test
```

Expected: compilation fails because `LowCodeApplication` does not exist.

- [ ] **Step 4: Add the minimal application entry point**

```java
package com.example.lowcode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LowCodeApplication {
    public static void main(String[] args) {
        SpringApplication.run(LowCodeApplication.class, args);
    }
}
```

Do not configure a package-wide `@MapperScan`. Each concrete MyBatis mapper interface must use
`@Mapper` so repository ports are never discovered as mapper candidates.

Configure `application.yml` with environment-backed datasource, MinIO and JWT values. Do not provide a production JWT default:

```yaml
spring:
  application:
    name: lowcode-server
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/lowcode?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${DB_USERNAME:lowcode}
    password: ${DB_PASSWORD:lowcode}
  flyway:
    enabled: true
  jackson:
    default-property-inclusion: non_null

app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-ttl: PT15M
  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    bucket: ${MINIO_BUCKET:lowcode-assets}

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 5: Verify GREEN**

Run: `.\mvnw.cmd -Dtest=LowCodeApplicationTest test`

Expected: one passing test and Maven exit code 0.

- [ ] **Step 6: Commit**

```powershell
git add server
git commit -m "build: bootstrap Spring Boot server"
```

## Task 2: Establish API and Error Contracts

**Files:**

- Create: `server/src/test/java/com/example/lowcode/common/api/ApiResponseTest.java`
- Create: `server/src/main/java/com/example/lowcode/common/api/ApiResponse.java`
- Create: `server/src/main/java/com/example/lowcode/common/api/ErrorResponse.java`
- Create: `server/src/main/java/com/example/lowcode/common/exception/ErrorCode.java`
- Create: `server/src/main/java/com/example/lowcode/common/exception/BusinessException.java`
- Create: `server/src/main/java/com/example/lowcode/common/exception/GlobalExceptionHandler.java`
- Create: `server/src/main/java/com/example/lowcode/common/web/TraceIdFilter.java`

- [ ] **Step 1: Write failing response contract tests**

```java
package com.example.lowcode.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {
    @Test
    void successCarriesCodeDataAndTraceId() {
        ApiResponse<String> response = ApiResponse.success("saved", "trace-1");
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("saved");
        assertThat(response.traceId()).isEqualTo("trace-1");
    }
}
```

- [ ] **Step 2: Run and verify RED**

Run: `.\mvnw.cmd -Dtest=ApiResponseTest test`

Expected: compilation fails because `ApiResponse` is missing.

- [ ] **Step 3: Implement the contracts**

```java
package com.example.lowcode.common.api;

public record ApiResponse<T>(String code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("OK", "success", data, traceId);
    }
}
```

Define `ErrorCode` with HTTP mappings for `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `DESIGN_VERSION_CONFLICT`, `INVALID_SCHEMA`, `INVALID_UPLOAD`, and `INTERNAL_ERROR`. `GlobalExceptionHandler` must map `BusinessException`, validation failures, malformed JSON, and unexpected exceptions into `ErrorResponse(code, message, traceId, details)` without returning stack traces.

`TraceIdFilter` must accept a valid incoming `X-Trace-Id` or generate a UUID, store it as a request attribute, return it as a response header, and clear MDC in `finally`.

- [ ] **Step 4: Verify GREEN**

Run: `.\mvnw.cmd -Dtest=ApiResponseTest test`

Expected: one passing test.

- [ ] **Step 5: Commit**

```powershell
git add server/src/main/java/com/example/lowcode/common server/src/test/java/com/example/lowcode/common
git commit -m "feat: add stable API error contracts"
```

## Task 3: Implement DesignSchema Validation

**Files:**

- Create: `server/src/test/java/com/example/lowcode/design/domain/DesignSchemaValidatorTest.java`
- Create: `server/src/main/java/com/example/lowcode/design/domain/DesignSchemaValidator.java`

- [ ] **Step 1: Write failing validator tests**

Cover one valid schema and three failures:

```java
package com.example.lowcode.design.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignSchemaValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DesignSchemaValidator validator = new DesignSchemaValidator();

    @Test
    void acceptsKnownElementsWithUniqueIds() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"schemaVersion":1,"canvas":{"width":1080,"height":1440,"background":"#ffffff"},
             "pages":[{"id":"page-1","elements":[
               {"id":"text-1","type":"text","transform":{"x":0,"y":0,"width":300,"height":80,"rotate":0},
                "props":{"text":"sale"},"visible":true,"locked":false,"zIndex":1}]}]}
            """);
        validator.validate(schema);
    }

    @Test
    void rejectsUnknownElementType() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"schemaVersion":1,"canvas":{"width":1080,"height":1440},
             "pages":[{"id":"page-1","elements":[{"id":"x","type":"script","props":{}}]}]}
            """);
        assertThatThrownBy(() -> validator.validate(schema))
            .hasMessageContaining("UNKNOWN_ELEMENT_TYPE");
    }

    @Test
    void rejectsDuplicateElementIds() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"schemaVersion":1,"canvas":{"width":1080,"height":1440},
             "pages":[{"id":"page-1","elements":[
               {"id":"same","type":"text","props":{}},{"id":"same","type":"image","props":{}}]}]}
            """);
        assertThatThrownBy(() -> validator.validate(schema))
            .hasMessageContaining("DUPLICATE_ELEMENT_ID");
    }

    @Test
    void rejectsExternalOrJavascriptUrls() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"schemaVersion":1,"canvas":{"width":1080,"height":1440},
             "pages":[{"id":"page-1","elements":[
               {"id":"image-1","type":"image","props":{"src":"javascript:alert(1)"}}]}]}
            """);
        assertThatThrownBy(() -> validator.validate(schema))
            .hasMessageContaining("UNSAFE_RESOURCE_URL");
    }
}
```

- [ ] **Step 2: Run and verify RED**

Run: `.\mvnw.cmd -Dtest=DesignSchemaValidatorTest test`

Expected: compilation fails because the validator is missing.

- [ ] **Step 3: Implement minimal recursive validation**

`DesignSchemaValidator` must enforce:

```java
private static final Set<String> ALLOWED_TYPES = Set.of("text", "image", "rect", "icon");
private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
private static final int MAX_PAGES = 20;
private static final int MAX_ELEMENTS_PER_PAGE = 500;
```

It must require `schemaVersion == 1`, positive canvas dimensions no larger than 10,000, unique page IDs, unique element IDs per page, allowed element types, finite numeric transform values, and resource URLs beginning with `asset://` or an empty value. It must reject any textual value beginning with `javascript:` and any property key beginning with `on` followed by an uppercase letter.

- [ ] **Step 4: Verify GREEN**

Run: `.\mvnw.cmd -Dtest=DesignSchemaValidatorTest test`

Expected: four passing tests.

- [ ] **Step 5: Commit**

```powershell
git add server/src/main/java/com/example/lowcode/design server/src/test/java/com/example/lowcode/design
git commit -m "feat: validate versioned design schemas"
```

## Task 4: Create the MySQL Schema and Test Infrastructure

**Files:**

- Create: `server/src/main/resources/db/migration/V1__backend_vertical_slice.sql`
- Create: `server/src/test/resources/application-test.yml`
- Create: `server/src/test/java/com/example/lowcode/integration/MySqlIntegrationTestSupport.java`
- Create: `server/src/test/java/com/example/lowcode/integration/DatabaseMigrationIT.java`
- Create: `infra/docker-compose.yml`

- [ ] **Step 1: Write a failing Flyway integration test**

Create `MySqlIntegrationTestSupport` with a static `MySQLContainer<?>` using `mysql:8.4`, `@DynamicPropertySource`, and an integration test that queries `information_schema.tables` for these exact tables:

```text
sys_user
sys_tenant
sys_tenant_member
design_template
template_field
design_document
design_version
design_permission
asset_upload_session
asset
```

- [ ] **Step 2: Run and verify RED**

Run: `.\mvnw.cmd -Dtest=DatabaseMigrationIT test`

Expected: context or assertion failure because the Flyway migration is absent.

- [ ] **Step 3: Add the migration**

The SQL must use `BIGINT` auto-increment primary keys, `JSON` for Schema documents, `DATETIME(6)` timestamps, and these required constraints:

```sql
UNIQUE KEY uk_tenant_user (tenant_id, user_id),
UNIQUE KEY uk_template_field (template_id, field_key),
UNIQUE KEY uk_document_version (document_id, version_no),
UNIQUE KEY uk_document_user_permission (document_id, user_id),
UNIQUE KEY uk_asset_object_key (object_key),
KEY idx_design_tenant_updated (tenant_id, updated_at),
KEY idx_design_owner_updated (owner_id, updated_at),
KEY idx_upload_status_expiry (status, expires_at)
```

Seed one global published template with ID `1001`, size 1080x1440, one editable text element, one image element, and matching `template_field` rows for `productName` and `productImage`.

`asset_upload_session` must contain expected file name, MIME, size, SHA-256, object key, expiry and status. Status columns use checked VARCHAR values instead of database-specific ENUM so application and migration tests stay explicit.

- [ ] **Step 4: Add local infrastructure**

`infra/docker-compose.yml` must define:

```text
mysql      port 3306, database/user/password lowcode
redis      port 6379
minio      ports 9000/9001, bucket initialized by minio-init
rabbitmq   ports 5672/15672
```

Use named volumes and health checks. Do not put production credentials in the file; the values are local-only and documented as such.

- [ ] **Step 5: Verify GREEN**

Run: `.\mvnw.cmd -Dtest=DatabaseMigrationIT test`

Expected: migration succeeds and all ten tables are present.

- [ ] **Step 6: Commit**

```powershell
git add server/src/main/resources server/src/test infra
git commit -m "feat: add MySQL schema and local infrastructure"
```

## Task 5: Add Phone-Code Login and JWT Security

**Files:**

- Create: `server/src/test/java/com/example/lowcode/auth/application/AuthServiceTest.java`
- Create: `server/src/main/java/com/example/lowcode/auth/api/AuthController.java`
- Create: `server/src/main/java/com/example/lowcode/auth/application/AuthService.java`
- Create: `server/src/main/java/com/example/lowcode/auth/application/AuthRepository.java`
- Create: `server/src/main/java/com/example/lowcode/auth/application/VerificationCodeVerifier.java`
- Create: `server/src/main/java/com/example/lowcode/auth/domain/User.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/UserMapper.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/TenantMapper.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/TenantMemberMapper.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/MyBatisAuthRepository.java`
- Create: `server/src/main/java/com/example/lowcode/auth/infrastructure/LocalVerificationCodeVerifier.java`
- Create: `server/src/main/java/com/example/lowcode/auth/security/CurrentUser.java`
- Create: `server/src/main/java/com/example/lowcode/auth/security/JwtTokenService.java`
- Create: `server/src/main/java/com/example/lowcode/auth/security/SecurityConfig.java`

- [ ] **Step 1: Write failing authentication tests**

Use in-memory fake repository adapters and a verifier that accepts only `123456`. Test:

```java
@Test
void firstSuccessfulLoginCreatesUserDefaultTenantAndToken() {
    LoginResult result = service.login(new LoginCommand("13800000000", "123456"));
    assertThat(result.accessToken()).isNotBlank();
    assertThat(result.userId()).isPositive();
    assertThat(result.tenantId()).isPositive();
}

@Test
void wrongCodeDoesNotCreateUser() {
    assertThatThrownBy(() -> service.login(new LoginCommand("13800000000", "000000")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("验证码");
    assertThat(userRepository.count()).isZero();
}
```

- [ ] **Step 2: Run and verify RED**

Run: `.\mvnw.cmd -Dtest=AuthServiceTest test`

Expected: compilation fails because auth services and ports are missing.

- [ ] **Step 3: Implement transactional login**

`POST /api/v1/auth/login` request:

```json
{"phone":"13800000000","verificationCode":"123456"}
```

Behavior:

1. Normalize and validate an 11-digit mainland China phone number.
2. Verify the code before any database write.
3. Find the user or create user, default tenant, and OWNER membership in one transaction.
4. Issue an HS256 JWT with `sub=userId`, `tenantId`, `jti`, `iat`, and `exp`.
5. Return `accessToken`, `tokenType=Bearer`, `expiresIn=900`, `userId`, and `tenantId`.

`LocalVerificationCodeVerifier` exists only under the `local` Spring profile and accepts `${LOCAL_VERIFICATION_CODE:123456}`. No non-local profile may create this bean.

Security rules:

```text
permitAll: /api/v1/auth/login, GET /api/v1/templates/**, /actuator/health
authenticated: all other /api/**
deny by default: any unmatched request
stateless sessions; CSRF disabled only because the API uses Bearer tokens
```

- [ ] **Step 4: Verify GREEN and security behavior**

Run: `.\mvnw.cmd -Dtest=AuthServiceTest test`

Expected: both tests pass.

Add a MockMvc security test proving an unauthenticated `POST /api/v1/designs` returns 401.

- [ ] **Step 5: Commit**

```powershell
git add server/src/main/java/com/example/lowcode/auth server/src/test/java/com/example/lowcode/auth
git commit -m "feat: add phone-code JWT login"
```

## Task 6: Expose Published Templates

**Files:**

- Create: `server/src/test/java/com/example/lowcode/template/application/TemplateQueryServiceTest.java`
- Create: `server/src/main/java/com/example/lowcode/template/api/TemplateController.java`
- Create: `server/src/main/java/com/example/lowcode/template/application/TemplateQueryService.java`
- Create: `server/src/main/java/com/example/lowcode/template/application/TemplateRepository.java`
- Create: `server/src/main/java/com/example/lowcode/template/infrastructure/MyBatisTemplateRepository.java`
- Create: `server/src/main/java/com/example/lowcode/template/infrastructure/DesignTemplateMapper.java`
- Create: `server/src/main/java/com/example/lowcode/template/infrastructure/TemplateFieldMapper.java`

- [ ] **Step 1: Write failing query tests**

Test that only `PUBLISHED` templates appear, disabled templates return `NOT_FOUND`, and the response includes fields without exposing internal audit columns.

Expected response shape:

```json
{
  "id": 1001,
  "name": "朋友圈促销",
  "width": 1080,
  "height": 1440,
  "coverAssetId": null,
  "fields": [
    {"fieldKey":"productName","label":"商品名称","fieldType":"TEXT","required":true},
    {"fieldKey":"productImage","label":"商品图片","fieldType":"IMAGE","required":true}
  ]
}
```

- [ ] **Step 2: Run and verify RED**

Run: `.\mvnw.cmd -Dtest=TemplateQueryServiceTest test`

Expected: compilation fails because the template query service is missing.

- [ ] **Step 3: Implement list and detail APIs**

Implement:

```text
GET /api/v1/templates
GET /api/v1/templates/{id}
```

The list endpoint returns metadata and fields but omits the full `schema_json`. The detail endpoint returns the validated Schema required to render a preview. Use explicit response DTOs.

- [ ] **Step 4: Verify GREEN**

Run: `.\mvnw.cmd -Dtest=TemplateQueryServiceTest test`

Expected: all template tests pass.

- [ ] **Step 5: Commit**

```powershell
git add server/src/main/java/com/example/lowcode/template server/src/test/java/com/example/lowcode/template
git commit -m "feat: expose published templates"
```

## Task 7: Create and Version Designs

**Files:**

- Create: `server/src/test/java/com/example/lowcode/design/application/DesignServiceTest.java`
- Create: `server/src/main/java/com/example/lowcode/design/api/DesignController.java`
- Create: `server/src/main/java/com/example/lowcode/design/application/DesignService.java`
- Create: `server/src/main/java/com/example/lowcode/design/application/DesignRepository.java`
- Create: `server/src/main/java/com/example/lowcode/design/domain/DesignDocument.java`
- Create: `server/src/main/java/com/example/lowcode/design/infrastructure/DesignDocumentMapper.java`
- Create: `server/src/main/java/com/example/lowcode/design/infrastructure/DesignVersionMapper.java`
- Create: `server/src/main/java/com/example/lowcode/design/infrastructure/DesignPermissionMapper.java`
- Create: `server/src/main/java/com/example/lowcode/design/infrastructure/MyBatisDesignRepository.java`

- [ ] **Step 1: Write failing creation and version tests**

Test these behaviors with fake repositories:

```java
@Test
void createsVersionOneByCopyingPublishedTemplate() {
    DesignView view = service.create(currentUser, new CreateDesignCommand(1001L, "周末促销"));
    assertThat(view.currentVersion()).isEqualTo(1);
    assertThat(view.schema().path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(permissionRepository.role(view.id(), currentUser.userId())).isEqualTo("OWNER");
}

@Test
void savesNextImmutableVersion() {
    DesignView updated = service.save(currentUser, designId, new SaveDesignCommand(1, validSchema));
    assertThat(updated.currentVersion()).isEqualTo(2);
    assertThat(versionRepository.versions(designId)).extracting("versionNo").containsExactly(1, 2);
}

@Test
void staleVersionIsRejectedWithoutWriting() {
    service.save(currentUser, designId, new SaveDesignCommand(1, validSchema));
    assertThatThrownBy(() -> service.save(currentUser, designId, new SaveDesignCommand(1, validSchema)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已被更新");
    assertThat(versionRepository.versions(designId)).hasSize(2);
}
```

- [ ] **Step 2: Run and verify RED**

Run: `.\mvnw.cmd -Dtest=DesignServiceTest test`

Expected: compilation fails because design services and repository ports are absent.

- [ ] **Step 3: Implement transactional create and save**

Implement:

```text
POST  /api/v1/designs
GET   /api/v1/designs/{id}
PATCH /api/v1/designs/{id}
GET   /api/v1/designs/{id}/versions
```

Create behavior:

1. Read a published template.
2. Validate the copied Schema.
3. Insert `design_document` with `current_version=1`.
4. Insert immutable version 1.
5. Insert OWNER permission.

Save behavior:

1. Require OWNER or EDITOR in the same tenant.
2. Validate Schema before writing.
3. Execute a conditional update:

```sql
UPDATE design_document
SET current_version = current_version + 1, updated_at = CURRENT_TIMESTAMP(6)
WHERE id = #{id}
  AND tenant_id = #{tenantId}
  AND current_version = #{baseVersion}
  AND status <> 'DELETED'
```

4. If affected rows are zero, return `DESIGN_VERSION_CONFLICT` with HTTP 409.
5. Insert the new immutable version inside the same transaction.

Never determine access from a resource ID alone. Every read and write includes `tenant_id`, followed by permission evaluation.

- [ ] **Step 4: Verify GREEN**

Run: `.\mvnw.cmd -Dtest=DesignServiceTest test`

Expected: creation, version increment and stale-write tests pass.

- [ ] **Step 5: Commit**

```powershell
git add server/src/main/java/com/example/lowcode/design server/src/test/java/com/example/lowcode/design
git commit -m "feat: create and version design documents"
```

## Task 8: Implement Safe MinIO Image Upload

**Files:**

- Create: `server/src/test/java/com/example/lowcode/asset/application/AssetServiceTest.java`
- Create: `server/src/main/java/com/example/lowcode/asset/api/AssetController.java`
- Create: `server/src/main/java/com/example/lowcode/asset/application/AssetService.java`
- Create: `server/src/main/java/com/example/lowcode/asset/application/AssetRepository.java`
- Create: `server/src/main/java/com/example/lowcode/asset/application/StorageGateway.java`
- Create: `server/src/main/java/com/example/lowcode/asset/domain/ImageInspector.java`
- Create: `server/src/main/java/com/example/lowcode/asset/infrastructure/AssetMapper.java`
- Create: `server/src/main/java/com/example/lowcode/asset/infrastructure/AssetUploadSessionMapper.java`
- Create: `server/src/main/java/com/example/lowcode/asset/infrastructure/MyBatisAssetRepository.java`
- Create: `server/src/main/java/com/example/lowcode/asset/infrastructure/MinioStorageGateway.java`

- [ ] **Step 1: Write failing upload service tests**

Use a fake `StorageGateway` and test:

```java
@Test
void createsTenantScopedPresignedUpload() {
    PresignResult result = service.presign(user, new PresignCommand(
        "product.png", "image/png", 1024, sha256));
    assertThat(result.objectKey()).startsWith("tenant/" + user.tenantId() + "/asset/");
    assertThat(result.uploadUrl()).startsWith("https://storage.test/");
}

@Test
void completesValidPngAndCreatesAsset() {
    fakeStorage.put(objectKey, validPngBytes());
    AssetView asset = service.complete(user, uploadSessionId);
    assertThat(asset.mimeType()).isEqualTo("image/png");
    assertThat(asset.width()).isPositive();
    assertThat(asset.height()).isPositive();
}

@Test
void rejectsExtensionSpoofingAndDoesNotCreateAsset() {
    fakeStorage.put(objectKey, "not an image".getBytes(UTF_8));
    assertThatThrownBy(() -> service.complete(user, uploadSessionId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("图片格式");
    assertThat(assetRepository.count()).isZero();
}
```

The test class creates real PNG bytes rather than mocking image decoding:

```java
private static byte[] validPngBytes() throws IOException {
    BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
}

private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
}
```

- [ ] **Step 2: Run and verify RED**

Run: `.\mvnw.cmd -Dtest=AssetServiceTest test`

Expected: compilation fails because asset services are missing.

- [ ] **Step 3: Implement upload sessions and validation**

Implement:

```text
POST /api/v1/assets/presign
POST /api/v1/assets/complete
```

Rules:

- Allow only JPEG, PNG and WebP.
- Maximum expected and actual size is 10 MiB.
- Generate object keys server-side: `tenant/{tenantId}/asset/{yyyy}/{MM}/{id}.{ext}`.
- Upload sessions expire after 15 minutes and can complete once.
- Download at most 10 MiB + 1 byte for validation; reject larger objects.
- Check file magic, decode dimensions with ImageIO, and reject dimensions over 20,000 pixels per side.
- Compute SHA-256 from actual bytes and compare with the expected lowercase hexadecimal value.
- Insert the asset and mark the session `COMPLETED` in one transaction.
- On validation failure, mark the session `REJECTED` and request object deletion after transaction completion.

`StorageGateway` exposes only `presignPut`, `readBounded`, and `delete`; controllers cannot access `MinioClient` directly.

- [ ] **Step 4: Verify GREEN**

Run: `.\mvnw.cmd -Dtest=AssetServiceTest test`

Expected: presign, valid PNG, and spoofed-file tests pass.

- [ ] **Step 5: Commit**

```powershell
git add server/src/main/java/com/example/lowcode/asset server/src/test/java/com/example/lowcode/asset
git commit -m "feat: add validated MinIO image uploads"
```

## Task 9: Prove the Complete API Slice

**Files:**

- Create: `server/src/test/java/com/example/lowcode/integration/BackendVerticalSliceIT.java`
- Modify: `server/src/test/resources/application-test.yml`

- [ ] **Step 1: Write the failing end-to-end integration test**

Use `@SpringBootTest(webEnvironment = RANDOM_PORT)`, Testcontainers MySQL, the `test` profile, and a deterministic fake `StorageGateway`. The test must execute real HTTP calls in this order:

```text
POST /api/v1/auth/login                  -> 200 and Bearer token
GET  /api/v1/templates                   -> contains template 1001
POST /api/v1/designs                     -> version 1
GET  /api/v1/designs/{id}                -> copied schema
PATCH /api/v1/designs/{id} baseVersion=1 -> version 2
PATCH /api/v1/designs/{id} baseVersion=1 -> 409
POST /api/v1/assets/presign              -> tenant-scoped object key
POST /api/v1/assets/complete             -> validated asset metadata
```

Add a second user in another tenant and assert `GET /api/v1/designs/{id}` returns 404 rather than revealing that the resource exists.

- [ ] **Step 2: Run and verify RED**

Run: `.\mvnw.cmd -Dtest=BackendVerticalSliceIT test`

Expected: the test fails at the first incomplete integration point, not because Docker is unavailable or configuration is malformed.

- [ ] **Step 3: Add only the missing wiring**

Wire repository implementations, transaction annotations, test verification-code verifier, test JWT secret, and fake storage bean. Do not add unrelated APIs.

- [ ] **Step 4: Verify GREEN**

Run: `.\mvnw.cmd -Dtest=BackendVerticalSliceIT test`

Expected: the full sequence passes, the stale update is 409, and cross-tenant access is 404.

- [ ] **Step 5: Run the full test suite**

Run: `.\mvnw.cmd test`

Expected: Maven exit code 0, zero failed tests, zero test errors.

- [ ] **Step 6: Commit**

```powershell
git add server
git commit -m "test: verify backend vertical slice"
```

## Task 10: Document and Smoke-Test Local Operation

**Files:**

- Create: `README.md`
- Modify: `server/src/main/resources/application-local.yml`

- [ ] **Step 1: Document exact local commands**

`README.md` must contain:

```powershell
docker compose -f infra/docker-compose.yml up -d --wait mysql redis minio rabbitmq
docker compose -f infra/docker-compose.yml up -d minio-init
docker compose -f infra/docker-compose.yml wait minio-init
$env:SPRING_PROFILES_ACTIVE='local'
$env:JWT_SECRET='local-development-secret-at-least-32-bytes'
cd server
.\mvnw.cmd spring-boot:run
```

Document local verification code `123456`, Swagger UI at `http://localhost:8080/swagger-ui/index.html`, health at `http://localhost:8080/actuator/health`, MinIO console at `http://localhost:9001`, and RabbitMQ console at `http://localhost:15672`.

- [ ] **Step 2: Build the production artifact**

Run from `server/`:

```powershell
.\mvnw.cmd clean verify
```

Expected: `BUILD SUCCESS` and `server/target/lowcode-server-0.1.0-SNAPSHOT.jar` exists.

- [ ] **Step 3: Start infrastructure and application**

Run the documented Docker Compose and Spring Boot commands. Wait for health to report `UP`.

- [ ] **Step 4: Perform HTTP smoke checks**

Use the local verification code to obtain a token, then call template list, design create, design save and upload presign. Record response status codes and stop if any response differs from the integration test contract.

- [ ] **Step 5: Inspect logs and security output**

Verify logs contain trace IDs and do not contain JWT values, verification codes, MinIO secrets, presigned URLs or stack traces for expected 4xx responses.

- [ ] **Step 6: Stop local infrastructure**

Run:

```powershell
docker compose -f infra/docker-compose.yml down
```

Do not add `-v`; local database and object data should remain recoverable.

- [ ] **Step 7: Commit**

```powershell
git add README.md server/src/main/resources/application-local.yml
git commit -m "docs: add backend local runbook"
```

## Final Verification Checklist

- [ ] `git diff --check` reports no whitespace errors.
- [ ] `.\mvnw.cmd clean verify` exits 0.
- [ ] All unit and integration tests pass with zero failures and zero errors.
- [ ] Flyway creates the exact MVP tables and seed template.
- [ ] Anonymous design creation returns 401.
- [ ] Cross-tenant design reads return 404.
- [ ] Invalid Schema never creates a new version.
- [ ] Stale `baseVersion` returns 409 and never creates a version.
- [ ] Spoofed images never create an asset.
- [ ] OpenAPI lists only the implemented MVP endpoints.
- [ ] The application health endpoint reports `UP` with local infrastructure running.
- [ ] `git status --short` contains no generated secrets, uploaded images, database files or Maven `target/` output.
