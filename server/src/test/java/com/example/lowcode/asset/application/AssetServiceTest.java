package com.example.lowcode.asset.application;

import com.example.lowcode.asset.domain.ImageInspector;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-17T09:00:00Z");
    private static final CurrentUser TENANT_ONE_USER = new CurrentUser(7L, 11L);
    private static final CurrentUser TENANT_ONE_OTHER_USER = new CurrentUser(9L, 11L);
    private static final CurrentUser TENANT_TWO_USER = new CurrentUser(8L, 12L);

    @Test
    void presignsTenantScopedObjectKeyUsingStorageGateway() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] png = pngBytes(12, 8, Color.RED);

        AssetService.PresignResult result = service.presign(
            TENANT_ONE_USER,
            command("campaign.png", "image/png", png)
        );

        assertThat(result.objectKey()).matches("tenant/11/asset/2026/08/upload/[a-f0-9-]+\\.png");
        assertThat(result.uploadUrl()).isEqualTo("https://storage.test/put/" + result.objectKey());
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(storage.lastPresignObjectKey).isEqualTo(result.objectKey());
        assertThat(repository.sessions).hasSize(1);
    }

    @Test
    void completesValidPngAndCreatesAssetWithVerifiedDimensions() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] png = pngBytes(12, 8, Color.BLUE);
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("campaign.png", "image/png", png)
        );
        storage.put(presign.objectKey(), png);

        AssetService.AssetView asset = service.complete(
            TENANT_ONE_USER,
            new AssetService.CompleteCommand(presign.sessionId())
        );

        assertThat(asset.mimeType()).isEqualTo("image/png");
        assertThat(asset.width()).isEqualTo(12);
        assertThat(asset.height()).isEqualTo(8);
        assertThat(asset.fileSize()).isEqualTo(png.length);
        assertThat(repository.assets).hasSize(1);
        assertThat(repository.sessions.get(presign.sessionId()).status()).isEqualTo("COMPLETED");
    }

    @Test
    void completesByPromotingTemporaryUploadToDistinctFinalAssetKey() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] png = pngBytes(12, 8, Color.BLUE);
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("campaign.png", "image/png", png)
        );
        storage.put(presign.objectKey(), png);

        AssetService.AssetView asset = service.complete(
            TENANT_ONE_USER,
            new AssetService.CompleteCommand(presign.sessionId())
        );

        assertThat(asset.objectKey()).matches("tenant/11/asset/2026/08/[a-f0-9-]+\\.png");
        assertThat(asset.objectKey()).isNotEqualTo(presign.objectKey());
        assertThat(storage.objects).containsKey(asset.objectKey());
        assertThat(storage.objects).doesNotContainKey(presign.objectKey());
    }

    @Test
    void rejectsPromotionWhenTemporaryObjectChangesAfterInitialValidation() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] expected = pngBytes(12, 8, Color.BLUE);
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("campaign.png", "image/png", expected)
        );
        storage.put(presign.objectKey(), expected);
        storage.replaceDuringPromotion(pngBytes(12, 8, Color.RED));

        assertThatThrownBy(() -> service.complete(
            TENANT_ONE_USER,
            new AssetService.CompleteCommand(presign.sessionId())
        )).isInstanceOf(BusinessException.class);
        assertThat(repository.assets).isEmpty();
        assertThat(repository.sessions.get(presign.sessionId()).status()).isEqualTo("REJECTED");
        assertThat(storage.objects).doesNotContainKey(presign.objectKey());
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void deletesTemporaryAndFinalObjectsWhenCompletedTransactionRollsBack() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] png = pngBytes(12, 8, Color.BLUE);
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("campaign.png", "image/png", png)
        );
        storage.put(presign.objectKey(), png);

        new TransactionTemplate(new TestTransactionManager()).executeWithoutResult(status -> {
            service.complete(TENANT_ONE_USER, new AssetService.CompleteCommand(presign.sessionId()));
            status.setRollbackOnly();
        });

        assertThat(storage.objects).isEmpty();
    }

    @Test
    void deletesRejectedTemporaryObjectAfterRejectionTransactionCommits() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] spoofed = "not an image".getBytes();
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("looks-like-image.png", "image/png", spoofed)
        );
        storage.put(presign.objectKey(), spoofed);

        new TransactionTemplate(new TestTransactionManager()).executeWithoutResult(status ->
            assertThatThrownBy(() -> service.complete(
                TENANT_ONE_USER,
                new AssetService.CompleteCommand(presign.sessionId())
            )).isInstanceOf(BusinessException.class)
        );

        assertThat(repository.sessions.get(presign.sessionId()).status()).isEqualTo("REJECTED");
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void completesValidWebpWithVerifiedDimensions() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] webp = webpBytes();
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("pixel.webp", "image/webp", webp)
        );
        storage.put(presign.objectKey(), webp);

        AssetService.AssetView asset = service.complete(
            TENANT_ONE_USER,
            new AssetService.CompleteCommand(presign.sessionId())
        );

        assertThat(asset.mimeType()).isEqualTo("image/webp");
        assertThat(asset.width()).isEqualTo(1);
        assertThat(asset.height()).isEqualTo(1);
        assertThat(repository.assets).hasSize(1);
    }

    @Test
    void rejectsSpoofedPngUploadAndDoesNotCreateAsset() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] spoofed = "not an image".getBytes();
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("looks-like-image.png", "image/png", spoofed)
        );
        storage.put(presign.objectKey(), spoofed);

        assertThatThrownBy(() -> service.complete(
            TENANT_ONE_USER,
            new AssetService.CompleteCommand(presign.sessionId())
        )).isInstanceOf(BusinessException.class);
        assertThat(repository.assets).isEmpty();
        assertThat(repository.sessions.get(presign.sessionId()).status()).isEqualTo("REJECTED");
        assertThat(storage.objects).doesNotContainKey(presign.objectKey());
    }

    @Test
    void rejectsDeclaredMimeTypeThatDoesNotMatchDecodedImage() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] png = pngBytes(9, 4, Color.ORANGE);
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("mislabelled.jpg", "image/jpeg", png)
        );
        storage.put(presign.objectKey(), png);

        assertThatThrownBy(() -> service.complete(
            TENANT_ONE_USER,
            new AssetService.CompleteCommand(presign.sessionId())
        )).isInstanceOf(BusinessException.class);
        assertThat(repository.assets).isEmpty();
        assertThat(repository.sessions.get(presign.sessionId()).status()).isEqualTo("REJECTED");
        assertThat(storage.objects).doesNotContainKey(presign.objectKey());
    }

    @Test
    void rejectsHashMismatchAndDeletesObject() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] expected = pngBytes(4, 4, Color.YELLOW);
        byte[] uploaded = pngBytes(4, 4, Color.BLACK);
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("photo.png", "image/png", expected)
        );
        storage.put(presign.objectKey(), uploaded);

        assertThatThrownBy(() -> service.complete(
            TENANT_ONE_USER,
            new AssetService.CompleteCommand(presign.sessionId())
        )).isInstanceOf(BusinessException.class);
        assertThat(repository.assets).isEmpty();
        assertThat(repository.sessions.get(presign.sessionId()).status()).isEqualTo("REJECTED");
        assertThat(storage.objects).doesNotContainKey(presign.objectKey());
    }

    @Test
    void rejectsExpiredAndRepeatedCompletion() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        byte[] png = pngBytes(6, 6, Color.MAGENTA);
        AssetService initialService = service(repository, storage, NOW);
        AssetService.PresignResult expired = initialService.presign(
            TENANT_ONE_USER,
            command("expired.png", "image/png", png)
        );
        storage.put(expired.objectKey(), png);

        AssetService expiredService = service(repository, storage, NOW.plus(Duration.ofMinutes(16)));
        assertThatThrownBy(() -> expiredService.complete(
            TENANT_ONE_USER,
            new AssetService.CompleteCommand(expired.sessionId())
        )).isInstanceOf(BusinessException.class);
        assertThat(repository.sessions.get(expired.sessionId()).status()).isEqualTo("EXPIRED");

        AssetService.PresignResult completed = initialService.presign(
            TENANT_ONE_USER,
            command("once.png", "image/png", png)
        );
        storage.put(completed.objectKey(), png);
        initialService.complete(TENANT_ONE_USER, new AssetService.CompleteCommand(completed.sessionId()));

        assertThatThrownBy(() -> initialService.complete(
            TENANT_ONE_USER,
            new AssetService.CompleteCommand(completed.sessionId())
        )).isInstanceOf(BusinessException.class);
        assertThat(repository.assets).hasSize(1);
    }

    @Test
    void rejectsCrossTenantCompletionWithoutDeletingOtherTenantObject() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] png = pngBytes(5, 7, Color.CYAN);
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("private.png", "image/png", png)
        );
        storage.put(presign.objectKey(), png);

        assertThatThrownBy(() -> service.complete(
            TENANT_TWO_USER,
            new AssetService.CompleteCommand(presign.sessionId())
        )).isInstanceOf(BusinessException.class);
        assertThat(repository.assets).isEmpty();
        assertThat(storage.objects).containsKey(presign.objectKey());
    }

    @Test
    void rejectsCompletionByAnotherUserInTheSameTenant() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] png = pngBytes(5, 7, Color.CYAN);
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("private.png", "image/png", png)
        );
        storage.put(presign.objectKey(), png);

        assertThatThrownBy(() -> service.complete(
            TENANT_ONE_OTHER_USER,
            new AssetService.CompleteCommand(presign.sessionId())
        )).isInstanceOf(BusinessException.class);
        assertThat(repository.assets).isEmpty();
        assertThat(storage.objects).containsKey(presign.objectKey());
    }

    @Test
    void rejectsObjectsOverTenMiB() {
        FakeAssetRepository repository = new FakeAssetRepository();
        FakeStorageGateway storage = new FakeStorageGateway();
        AssetService service = service(repository, storage, NOW);
        byte[] declared = new byte[10 * 1024 * 1024];
        Arrays.fill(declared, (byte) 1);
        AssetService.PresignResult presign = service.presign(
            TENANT_ONE_USER,
            command("large.png", "image/png", declared)
        );
        storage.put(presign.objectKey(), new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.complete(
            TENANT_ONE_USER,
            new AssetService.CompleteCommand(presign.sessionId())
        )).isInstanceOf(BusinessException.class);
        assertThat(repository.assets).isEmpty();
        assertThat(repository.sessions.get(presign.sessionId()).status()).isEqualTo("REJECTED");
    }

    private AssetService service(FakeAssetRepository repository, FakeStorageGateway storage, Instant now) {
        return new AssetService(
            repository,
            storage,
            new ImageInspector(),
            Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private AssetService.PresignCommand command(String fileName, String mimeType, byte[] content) {
        return new AssetService.PresignCommand(fileName, mimeType, content.length, sha256(content));
    }

    private static byte[] pngBytes(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color.getRGB());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG writer is unavailable");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] webpBytes() {
        return Base64.getDecoder().decode(
            "UklGRlYAAABXRUJQVlA4IDoAAADwAgCdASoBAAEAAEcIhYWIhYSIAgICdaoD+AP6Ag1NGAD+/vNYf/5gZt2KO//mBv/80F4SW6//zLwASUNNVAgAAAB0ZXN0MXgxAA=="
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class FakeStorageGateway implements StorageGateway {
        private final Map<String, byte[]> objects = new HashMap<>();
        private String lastPresignObjectKey;
        private byte[] replacementDuringPromotion;

        @Override
        public String presignPut(String objectKey, String mimeType, Duration expiresIn) {
            lastPresignObjectKey = objectKey;
            return "https://storage.test/put/" + objectKey;
        }

        @Override
        public byte[] readBounded(String objectKey, long maxBytes) {
            byte[] value = objects.get(objectKey);
            if (value == null) {
                throw new StorageFailureException("object is missing");
            }
            return Arrays.copyOf(value, (int) Math.min(value.length, maxBytes));
        }

        @Override
        public void promote(String temporaryObjectKey, String finalObjectKey) {
            if (replacementDuringPromotion != null) {
                objects.put(temporaryObjectKey, replacementDuringPromotion);
            }
            byte[] value = objects.get(temporaryObjectKey);
            if (value == null) {
                throw new StorageFailureException("temporary object is missing");
            }
            objects.put(finalObjectKey, Arrays.copyOf(value, value.length));
        }

        @Override
        public void delete(String objectKey) {
            objects.remove(objectKey);
        }

        void put(String objectKey, byte[] bytes) {
            objects.put(objectKey, bytes);
        }

        void replaceDuringPromotion(byte[] bytes) {
            replacementDuringPromotion = bytes;
        }
    }

    private static final class FakeAssetRepository implements AssetRepository {
        private long nextSessionId = 1L;
        private long nextAssetId = 100L;
        private final Map<Long, SessionState> sessions = new HashMap<>();
        private final List<AssetRepository.AssetRecord> assets = new ArrayList<>();

        @Override
        public long createUploadSession(AssetRepository.NewUploadSession session) {
            long id = nextSessionId++;
            sessions.put(id, new SessionState(id, session, "PENDING"));
            return id;
        }

        @Override
        public Optional<AssetRepository.UploadSession> findPendingSession(long tenantId, long userId, long sessionId) {
            SessionState session = sessions.get(sessionId);
            if (session == null
                || session.newSession.tenantId() != tenantId
                || session.newSession.userId() != userId
                || !"PENDING".equals(session.status)) {
                return Optional.empty();
            }
            return Optional.of(session.toUploadSession());
        }

        @Override
        public boolean markCompleted(long tenantId, long sessionId, Instant completedAt) {
            SessionState session = sessions.get(sessionId);
            if (session == null || session.newSession.tenantId() != tenantId || !"PENDING".equals(session.status)) {
                return false;
            }
            session.status = "COMPLETED";
            return true;
        }

        @Override
        public boolean markRejected(long tenantId, long sessionId, String reason) {
            SessionState session = sessions.get(sessionId);
            if (session == null || session.newSession.tenantId() != tenantId || !"PENDING".equals(session.status)) {
                return false;
            }
            session.status = "REJECTED";
            return true;
        }

        @Override
        public boolean markExpired(long tenantId, long sessionId, Instant now) {
            SessionState session = sessions.get(sessionId);
            if (session == null || session.newSession.tenantId() != tenantId || !"PENDING".equals(session.status)) {
                return false;
            }
            session.status = "EXPIRED";
            return true;
        }

        @Override
        public long createAsset(AssetRepository.NewAsset asset) {
            long id = nextAssetId++;
            assets.add(new AssetRepository.AssetRecord(
                id,
                asset.tenantId(),
                asset.ownerId(),
                asset.uploadSessionId(),
                asset.objectKey(),
                asset.fileName(),
                asset.mimeType(),
                asset.fileSize(),
                asset.sha256(),
                asset.width(),
                asset.height(),
                NOW
            ));
            return id;
        }

        private static final class SessionState {
            private final long id;
            private final AssetRepository.NewUploadSession newSession;
            private String status;

            private SessionState(long id, AssetRepository.NewUploadSession newSession, String status) {
                this.id = id;
                this.newSession = newSession;
                this.status = status;
            }

            private AssetRepository.UploadSession toUploadSession() {
                return new AssetRepository.UploadSession(
                    id,
                    newSession.tenantId(),
                    newSession.userId(),
                    newSession.fileName(),
                    newSession.mimeType(),
                    newSession.expectedFileSize(),
                    newSession.sha256(),
                    newSession.objectKey(),
                    newSession.expiresAt()
                );
            }

            private String status() {
                return status;
            }
        }
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
