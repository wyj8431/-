package com.example.lowcode.integration;

import com.example.lowcode.asset.application.StorageFailureException;
import com.example.lowcode.asset.application.StorageGateway;
import com.example.lowcode.auth.support.TestAuthConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestAuthConfiguration.class, BackendVerticalSliceIT.InMemoryStorageConfiguration.class})
class BackendVerticalSliceIT extends MySqlIntegrationTestSupport {
    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryStorageGateway storageGateway;

    @LocalServerPort
    private int port;

    @Test
    void runsTheAuthenticatedDesignAndUploadFlowAcrossRealHttpBoundaries() throws Exception {
        String ownerToken = login("13900000001");

        ResponseEntity<JsonNode> templates = exchange(HttpMethod.GET, "/api/v1/templates", null, null);
        assertThat(templates.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(templates.getBody().at("/data/0/id").asLong()).isEqualTo(1001L);

        ResponseEntity<JsonNode> created = exchange(
            HttpMethod.POST,
            "/api/v1/designs",
            ownerToken,
            objectMapper.createObjectNode().put("templateId", 1001).put("name", "E2E promotion")
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long documentId = created.getBody().at("/data/id").asLong();
        assertThat(created.getBody().at("/data/currentVersion").asInt()).isEqualTo(1);

        ResponseEntity<JsonNode> loaded = exchange(HttpMethod.GET, "/api/v1/designs/" + documentId, ownerToken, null);
        assertThat(loaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode schema = loaded.getBody().at("/data/schema");
        assertThat(schema.path("schemaVersion").asInt()).isEqualTo(1);

        ResponseEntity<JsonNode> saved = exchange(
            HttpMethod.PATCH,
            "/api/v1/designs/" + documentId,
            ownerToken,
            objectMapper.createObjectNode().put("baseVersion", 1).set("schema", schema)
        );
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody().at("/data/currentVersion").asInt()).isEqualTo(2);

        ResponseEntity<JsonNode> stale = exchange(
            HttpMethod.PATCH,
            "/api/v1/designs/" + documentId,
            ownerToken,
            objectMapper.createObjectNode().put("baseVersion", 1).set("schema", schema)
        );
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().path("code").asText()).isEqualTo("DESIGN_VERSION_CONFLICT");

        byte[] png = pngBytes();
        ResponseEntity<JsonNode> presigned = exchange(
            HttpMethod.POST,
            "/api/v1/assets/presign",
            ownerToken,
            objectMapper.createObjectNode()
                .put("fileName", "offer.png")
                .put("mimeType", "image/png")
                .put("fileSize", png.length)
                .put("sha256", sha256(png))
        );
        assertThat(presigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        long sessionId = presigned.getBody().at("/data/sessionId").asLong();
        String temporaryObjectKey = presigned.getBody().at("/data/objectKey").asText();
        storageGateway.put(temporaryObjectKey, png);

        ResponseEntity<JsonNode> completed = exchange(
            HttpMethod.POST,
            "/api/v1/assets/complete",
            ownerToken,
            objectMapper.createObjectNode().put("sessionId", sessionId)
        );
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().at("/data/mimeType").asText()).isEqualTo("image/png");
        assertThat(completed.getBody().at("/data/width").asInt()).isEqualTo(2);
        assertThat(completed.getBody().at("/data/height").asInt()).isEqualTo(3);
        assertThat(storageGateway.contains(temporaryObjectKey)).isFalse();

        String otherTenantToken = login("13900000002");
        ResponseEntity<JsonNode> crossTenantRead = exchange(
            HttpMethod.GET,
            "/api/v1/designs/" + documentId,
            otherTenantToken,
            null
        );
        assertThat(crossTenantRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(crossTenantRead.getBody().path("code").asText()).isEqualTo("NOT_FOUND");
    }

    private String login(String phone) {
        ResponseEntity<JsonNode> response = exchange(
            HttpMethod.POST,
            "/api/v1/auth/login",
            null,
            objectMapper.createObjectNode().put("phone", phone).put("verificationCode", "123456")
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().at("/data/accessToken").asText();
    }

    private ResponseEntity<JsonNode> exchange(HttpMethod method, String path, String bearerToken, JsonNode body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(
            "http://localhost:" + port + path,
            method,
            new HttpEntity<>(body, headers),
            JsonNode.class
        );
    }

    private static byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class InMemoryStorageConfiguration {
        @Bean
        @Primary
        InMemoryStorageGateway inMemoryStorageGateway() {
            return new InMemoryStorageGateway();
        }
    }

    static class InMemoryStorageGateway implements StorageGateway {
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        @Override
        public String presignPut(String objectKey, String mimeType, Duration expiresIn) {
            return "https://storage.test/put/" + objectKey;
        }

        @Override
        public byte[] readBounded(String objectKey, long maxBytes) {
            byte[] bytes = objects.get(objectKey);
            if (bytes == null) {
                throw new StorageFailureException("object is missing");
            }
            int length = (int) Math.min(bytes.length, maxBytes);
            byte[] bounded = new byte[length];
            System.arraycopy(bytes, 0, bounded, 0, length);
            return bounded;
        }

        @Override
        public void promote(String temporaryObjectKey, String finalObjectKey) {
            byte[] bytes = objects.get(temporaryObjectKey);
            if (bytes == null) {
                throw new StorageFailureException("object is missing");
            }
            objects.put(finalObjectKey, bytes.clone());
        }

        @Override
        public void delete(String objectKey) {
            objects.remove(objectKey);
        }

        void put(String objectKey, byte[] bytes) {
            objects.put(objectKey, bytes.clone());
        }

        boolean contains(String objectKey) {
            return objects.containsKey(objectKey);
        }
    }
}
