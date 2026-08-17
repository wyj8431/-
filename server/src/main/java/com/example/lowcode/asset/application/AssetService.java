package com.example.lowcode.asset.application;

import com.example.lowcode.asset.domain.ImageInspector;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AssetService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetService.class);
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Duration SESSION_TTL = Duration.ofMinutes(15);

    private final AssetRepository assetRepository;
    private final StorageGateway storageGateway;
    private final ImageInspector imageInspector;
    private final Clock clock;

    @Autowired
    public AssetService(
        AssetRepository assetRepository,
        StorageGateway storageGateway,
        ImageInspector imageInspector
    ) {
        this(assetRepository, storageGateway, imageInspector, Clock.systemUTC());
    }

    public AssetService(
        AssetRepository assetRepository,
        StorageGateway storageGateway,
        ImageInspector imageInspector,
        Clock clock
    ) {
        this.assetRepository = assetRepository;
        this.storageGateway = storageGateway;
        this.imageInspector = imageInspector;
        this.clock = clock;
    }

    public PresignResult presign(CurrentUser currentUser, PresignCommand command) {
        ValidatedUpload validated = validatePresign(command);
        Instant expiresAt = clock.instant().plus(SESSION_TTL);
        String objectKey = temporaryObjectKey(currentUser.tenantId(), validated.extension(), clock.instant());
        long sessionId = assetRepository.createUploadSession(new AssetRepository.NewUploadSession(
            currentUser.tenantId(),
            currentUser.userId(),
            validated.fileName(),
            validated.mimeType(),
            validated.fileSize(),
            validated.sha256(),
            objectKey,
            expiresAt
        ));
        try {
            String uploadUrl = storageGateway.presignPut(objectKey, validated.mimeType(), SESSION_TTL);
            return new PresignResult(sessionId, objectKey, uploadUrl, expiresAt);
        } catch (StorageFailureException exception) {
            assetRepository.markRejected(currentUser.tenantId(), sessionId, "无法创建上传地址");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法创建上传地址");
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AssetView complete(CurrentUser currentUser, CompleteCommand command) {
        if (command == null || command.sessionId() < 1) {
            throw invalidUpload("上传会话无效");
        }

        AssetRepository.UploadSession session = assetRepository
            .findPendingSession(currentUser.tenantId(), currentUser.userId(), command.sessionId())
            .orElseThrow(() -> invalidUpload("上传会话不存在或已完成"));
        Instant now = clock.instant();
        if (!session.expiresAt().isAfter(now)) {
            expireAndThrow(currentUser, session, now);
        }

        byte[] bytes;
        try {
            bytes = storageGateway.readBounded(session.objectKey(), MAX_FILE_SIZE + 1);
        } catch (ObjectTooLargeException exception) {
            rejectAndThrow(currentUser, session, "上传文件超过 10 MiB 限制");
            throw new IllegalStateException("unreachable");
        } catch (StorageFailureException exception) {
            rejectAndThrow(currentUser, session, "上传对象不存在或无法读取");
            throw new IllegalStateException("unreachable");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_SIZE) {
            rejectAndThrow(currentUser, session, "上传文件大小无效");
        }
        if (bytes.length != session.expectedFileSize()) {
            rejectAndThrow(currentUser, session, "上传文件大小与预期不一致");
        }

        String actualSha256 = sha256(bytes);
        if (!actualSha256.equals(session.sha256())) {
            rejectAndThrow(currentUser, session, "上传文件哈希不匹配");
        }

        ImageInspector.ImageInfo image;
        try {
            image = imageInspector.inspect(bytes);
        } catch (ImageInspector.InvalidImageException exception) {
            rejectAndThrow(currentUser, session, exception.getMessage());
            throw new IllegalStateException("unreachable");
        }
        if (!image.mimeType().equals(session.mimeType())) {
            rejectAndThrow(currentUser, session, "上传文件 MIME 类型与实际格式不一致");
        }

        String finalObjectKey = finalObjectKey(currentUser.tenantId(), extensionFor(image.mimeType()), now);
        boolean transactional = registerPromotionCleanup(session.objectKey(), finalObjectKey);
        try {
            storageGateway.promote(session.objectKey(), finalObjectKey);
            byte[] promotedBytes = storageGateway.readBounded(finalObjectKey, MAX_FILE_SIZE + 1);
            if (promotedBytes == null
                || promotedBytes.length != session.expectedFileSize()
                || !sha256(promotedBytes).equals(session.sha256())) {
                rejectAndThrow(
                    currentUser,
                    session,
                    "提升后的上传文件与已验证内容不一致",
                    finalObjectKey
                );
            }
            if (!assetRepository.markCompleted(currentUser.tenantId(), session.id(), now)) {
                deleteQuietly(finalObjectKey);
                throw invalidUpload("上传会话已完成、过期或不可用");
            }
            long assetId = assetRepository.createAsset(new AssetRepository.NewAsset(
                currentUser.tenantId(),
                currentUser.userId(),
                session.id(),
                finalObjectKey,
                session.fileName(),
                image.mimeType(),
                bytes.length,
                actualSha256,
                image.width(),
                image.height()
            ));
            if (!transactional) {
                deleteQuietly(session.objectKey());
            }
            return new AssetView(
                assetId,
                finalObjectKey,
                image.mimeType(),
                bytes.length,
                actualSha256,
                image.width(),
                image.height()
            );
        } catch (RuntimeException exception) {
            if (!transactional) {
                deleteQuietly(session.objectKey());
                deleteQuietly(finalObjectKey);
            }
            throw exception;
        }
    }

    private ValidatedUpload validatePresign(PresignCommand command) {
        if (command == null) {
            throw invalidUpload("上传参数无效");
        }
        String fileName = command.fileName() == null ? "" : command.fileName().trim();
        if (fileName.isEmpty() || fileName.length() > 255 || hasControlCharacter(fileName)) {
            throw invalidUpload("文件名无效");
        }
        String extension = extensionFor(command.mimeType());
        if (command.fileSize() < 1 || command.fileSize() > MAX_FILE_SIZE) {
            throw invalidUpload("文件大小必须在 1B 到 10MiB 之间");
        }
        if (command.sha256() == null || !command.sha256().matches("[0-9a-f]{64}")) {
            throw invalidUpload("SHA-256 必须为 64 位小写十六进制");
        }
        return new ValidatedUpload(fileName, command.mimeType(), command.fileSize(), command.sha256(), extension);
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw invalidUpload("仅支持 JPEG、PNG 或 WebP 图片");
        };
    }

    private String temporaryObjectKey(long tenantId, String extension, Instant now) {
        ZonedDateTime timestamp = now.atZone(ZoneOffset.UTC);
        return "tenant/%d/asset/%04d/%02d/upload/%s.%s".formatted(
            tenantId,
            timestamp.getYear(),
            timestamp.getMonthValue(),
            UUID.randomUUID(),
            extension
        );
    }

    private String finalObjectKey(long tenantId, String extension, Instant now) {
        ZonedDateTime timestamp = now.atZone(ZoneOffset.UTC);
        return "tenant/%d/asset/%04d/%02d/%s.%s".formatted(
            tenantId,
            timestamp.getYear(),
            timestamp.getMonthValue(),
            UUID.randomUUID(),
            extension
        );
    }

    private void rejectAndThrow(CurrentUser currentUser, AssetRepository.UploadSession session, String reason) {
        rejectAndThrow(currentUser, session, reason, null);
    }

    private void rejectAndThrow(
        CurrentUser currentUser,
        AssetRepository.UploadSession session,
        String reason,
        String additionalObjectKey
    ) {
        if (assetRepository.markRejected(currentUser.tenantId(), session.id(), truncate(reason))) {
            deleteAfterCommit(session.objectKey());
            if (additionalObjectKey != null) {
                deleteAfterCommit(additionalObjectKey);
            }
        }
        throw invalidUpload(reason);
    }

    private void expireAndThrow(CurrentUser currentUser, AssetRepository.UploadSession session, Instant now) {
        if (assetRepository.markExpired(currentUser.tenantId(), session.id(), now)) {
            deleteAfterCommit(session.objectKey());
        }
        throw invalidUpload("上传会话已过期");
    }

    private void deleteAfterCommit(String objectKey) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(objectKey);
                }
            });
            return;
        }
        deleteQuietly(objectKey);
    }

    private boolean registerPromotionCleanup(String temporaryObjectKey, String finalObjectKey) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                deleteQuietly(temporaryObjectKey);
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(finalObjectKey);
                }
            }
        });
        return true;
    }

    private void deleteQuietly(String objectKey) {
        try {
            storageGateway.delete(objectKey);
        } catch (StorageFailureException exception) {
            LOGGER.warn("Could not delete rejected upload object, objectKey={}", objectKey, exception);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private String truncate(String reason) {
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
    }

    private BusinessException invalidUpload(String message) {
        return new BusinessException(ErrorCode.INVALID_UPLOAD, message);
    }

    public record PresignCommand(String fileName, String mimeType, long fileSize, String sha256) {
    }

    public record CompleteCommand(long sessionId) {
    }

    public record PresignResult(long sessionId, String objectKey, String uploadUrl, Instant expiresAt) {
    }

    public record AssetView(
        long id,
        String objectKey,
        String mimeType,
        long fileSize,
        String sha256,
        int width,
        int height
    ) {
    }

    private record ValidatedUpload(String fileName, String mimeType, long fileSize, String sha256, String extension) {
    }
}
