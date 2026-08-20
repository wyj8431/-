package com.example.lowcode.template.application;

import com.example.lowcode.asset.application.ObjectTooLargeException;
import com.example.lowcode.asset.application.StorageFailureException;
import com.example.lowcode.asset.application.StorageGateway;
import com.example.lowcode.asset.domain.ImageInspector;
import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.auth.application.AuthRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TemplateCoverAdminService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateCoverAdminService.class);
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Duration SESSION_TTL = Duration.ofMinutes(15);
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "DISABLED");

    private final AuthRepository authRepository;
    private final TemplateCoverAdminRepository repository;
    private final StorageGateway storageGateway;
    private final ImageInspector imageInspector;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Autowired
    public TemplateCoverAdminService(
        AuthRepository authRepository,
        TemplateCoverAdminRepository repository,
        StorageGateway storageGateway,
        ImageInspector imageInspector,
        AuditLogService auditLogService
    ) {
        this(authRepository, repository, storageGateway, imageInspector, auditLogService, Clock.systemUTC());
    }

    public TemplateCoverAdminService(
        AuthRepository authRepository,
        TemplateCoverAdminRepository repository,
        StorageGateway storageGateway,
        ImageInspector imageInspector,
        AuditLogService auditLogService,
        Clock clock
    ) {
        this.authRepository = authRepository;
        this.repository = repository;
        this.storageGateway = storageGateway;
        this.imageInspector = imageInspector;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    public PresignResult presign(CurrentUser currentUser, PresignCommand command) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        ValidatedUpload upload = validatePresign(command);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(SESSION_TTL);
        String objectKey = temporaryObjectKey(upload.mimeType(), now);
        long sessionId = repository.createUploadSession(new TemplateCoverAdminRepository.NewUploadSession(
            currentUser.tenantId(), currentUser.userId(), upload.fileName(), upload.mimeType(),
            upload.fileSize(), upload.sha256(), objectKey, expiresAt
        ));
        try {
            String uploadUrl = storageGateway.presignPut(objectKey, upload.mimeType(), SESSION_TTL);
            return new PresignResult(sessionId, objectKey, uploadUrl, expiresAt);
        } catch (StorageFailureException exception) {
            repository.markRejected(sessionId, "无法创建上传地址");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法创建上传地址");
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public CoverView complete(CurrentUser currentUser, CompleteCommand command) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        if (command == null || command.sessionId() < 1) {
            throw invalidUpload("上传会话无效");
        }
        TemplateCoverAdminRepository.UploadSession session = repository
            .findPendingSession(currentUser.tenantId(), currentUser.userId(), command.sessionId())
            .orElseThrow(() -> invalidUpload("上传会话不存在或已完成"));
        Instant now = clock.instant();
        if (!session.expiresAt().isAfter(now)) {
            reject(session, "上传会话已过期");
        }

        byte[] bytes;
        try {
            bytes = storageGateway.readBounded(session.objectKey(), MAX_FILE_SIZE + 1);
        } catch (ObjectTooLargeException exception) {
            reject(session, "上传文件超过 10 MiB 限制");
            throw new IllegalStateException("unreachable");
        } catch (StorageFailureException exception) {
            reject(session, "上传对象不存在或无法读取");
            throw new IllegalStateException("unreachable");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_SIZE) {
            reject(session, "上传文件大小无效");
        }
        if (bytes.length != session.expectedFileSize()) {
            reject(session, "上传文件大小与预期不一致");
        }
        String sha256 = sha256(bytes);
        if (!sha256.equals(session.sha256())) {
            reject(session, "上传文件哈希不匹配");
        }
        ImageInspector.ImageInfo image;
        try {
            image = imageInspector.inspect(bytes);
        } catch (ImageInspector.InvalidImageException exception) {
            reject(session, exception.getMessage());
            throw new IllegalStateException("unreachable");
        }
        if (!image.mimeType().equals(session.mimeType())) {
            reject(session, "上传文件 MIME 类型与实际格式不一致");
        }

        String finalObjectKey = finalObjectKey(image.mimeType(), now);
        boolean cleanupRegistered = registerPromotionCleanup(session.objectKey(), finalObjectKey);
        try {
            storageGateway.promote(session.objectKey(), finalObjectKey);
            byte[] promoted = storageGateway.readBounded(finalObjectKey, MAX_FILE_SIZE + 1);
            if (promoted == null || promoted.length != session.expectedFileSize()
                || !sha256(promoted).equals(session.sha256())) {
                reject(session, "提升后的上传文件与已验证内容不一致", finalObjectKey);
                throw invalidUpload("提升后的上传文件与已验证内容不一致");
            }
            if (!repository.markCompleted(session.id(), now)) {
                deleteQuietly(finalObjectKey);
                throw invalidUpload("上传会话已完成、过期或不可用");
            }
            long assetId = repository.createAsset(new TemplateCoverAdminRepository.NewAsset(
                session.id(), finalObjectKey, image.mimeType(), bytes.length, sha256, image.width(), image.height()
            ));
            if (!cleanupRegistered) {
                deleteQuietly(session.objectKey());
            }
            CoverView view = new CoverView(assetId, finalObjectKey, image.mimeType(), bytes.length, sha256,
                image.width(), image.height(), "DRAFT", now, now);
            auditLogService.record(new AuditLogService.AuditEvent(
                actor.userId(), currentUser.tenantId(), "TEMPLATE_COVER_ASSET_CREATE", "TEMPLATE_COVER_ASSET",
                Long.toString(assetId), AuditLogService.Outcome.SUCCESS, null,
                Map.of("mimeType", image.mimeType(), "width", image.width(), "height", image.height(), "status", "DRAFT")
            ));
            return view;
        } catch (BusinessException exception) {
            if (!cleanupRegistered) {
                deleteQuietly(session.objectKey());
                deleteQuietly(finalObjectKey);
            }
            throw exception;
        } catch (RuntimeException exception) {
            if (!cleanupRegistered) {
                deleteQuietly(session.objectKey());
                deleteQuietly(finalObjectKey);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<CoverView> list(CurrentUser currentUser, String requestedStatus) {
        requireManager(currentUser);
        String status = normalizeStatus(requestedStatus, true);
        return repository.findAll(status).stream().map(this::toView).toList();
    }

    @Transactional
    public CoverView changeStatus(CurrentUser currentUser, long coverAssetId, String requestedStatus) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        String status = normalizeStatus(requestedStatus, false);
        TemplateCoverAdminRepository.CoverAssetRecord cover = findCover(coverAssetId);
        if (status.equals(cover.status())) return toView(cover);
        if (!repository.updateStatus(coverAssetId, status)) throw new BusinessException(ErrorCode.NOT_FOUND, "模板封面不存在");
        auditLogService.record(new AuditLogService.AuditEvent(
            actor.userId(), currentUser.tenantId(), "TEMPLATE_COVER_ASSET_STATUS_CHANGE", "TEMPLATE_COVER_ASSET",
            Long.toString(coverAssetId), AuditLogService.Outcome.SUCCESS, null,
            Map.of("fromStatus", cover.status(), "toStatus", status)
        ));
        return new CoverView(cover.id(), cover.objectKey(), cover.mimeType(), cover.fileSize(), cover.sha256(),
            cover.width(), cover.height(), status, cover.createdAt(), clock.instant());
    }

    @Transactional
    public void delete(CurrentUser currentUser, long coverAssetId) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        TemplateCoverAdminRepository.CoverAssetRecord cover = findCover(coverAssetId);
        if (repository.countReferences(coverAssetId) > 0) throw new BusinessException(ErrorCode.TEMPLATE_COVER_IN_USE);
        if (!repository.deleteById(coverAssetId)) throw new BusinessException(ErrorCode.NOT_FOUND, "模板封面不存在");
        deleteAfterCommit(cover.objectKey());
        auditLogService.record(new AuditLogService.AuditEvent(
            actor.userId(), currentUser.tenantId(), "TEMPLATE_COVER_ASSET_DELETE", "TEMPLATE_COVER_ASSET",
            Long.toString(coverAssetId), AuditLogService.Outcome.SUCCESS, null,
            metadata("objectKey", cover.objectKey(), "status", cover.status())
        ));
    }

    @Transactional
    public BindingResult bindTemplateCover(CurrentUser currentUser, long templateId, Long coverAssetId) {
        AuthRepository.UserIdentity actor = requireAdministrator(currentUser);
        if (templateId < 1 || (coverAssetId != null && coverAssetId < 1)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板或封面编号无效");
        }
        Long previous = repository.findTemplateCoverId(templateId).orElse(null);
        if (coverAssetId != null && !coverAssetId.equals(previous)) {
            TemplateCoverAdminRepository.CoverAssetRecord cover = findCover(coverAssetId);
            if (!"PUBLISHED".equals(cover.status())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只有已发布封面才能绑定模板");
            }
        }
        if (!repository.updateTemplateCover(templateId, coverAssetId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        auditLogService.record(new AuditLogService.AuditEvent(
            actor.userId(), currentUser.tenantId(), "TEMPLATE_COVER_BIND", "DESIGN_TEMPLATE",
            Long.toString(templateId), AuditLogService.Outcome.SUCCESS, null,
            metadata("fromCoverAssetId", previous, "toCoverAssetId", coverAssetId)
        ));
        return new BindingResult(templateId, coverAssetId);
    }

    private TemplateCoverAdminRepository.CoverAssetRecord findCover(long id) {
        if (id < 1) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板封面编号无效");
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模板封面不存在"));
    }

    private AuthRepository.UserIdentity requireManager(CurrentUser currentUser) {
        AuthRepository.UserIdentity actor = authRepository.findByUserAndTenant(currentUser.userId(), currentUser.tenantId())
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效"));
        if (!"ACTIVE".equals(actor.userStatus()) || !"ACTIVE".equals(actor.tenantStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号无效");
        }
        if (!"ADMIN".equals(actor.tenantRole()) && !"OPERATOR".equals(actor.tenantRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无管理员权限");
        }
        return actor;
    }

    private AuthRepository.UserIdentity requireAdministrator(CurrentUser currentUser) {
        AuthRepository.UserIdentity actor = requireManager(currentUser);
        if (!"ADMIN".equals(actor.tenantRole())) throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可管理模板封面");
        return actor;
    }

    private ValidatedUpload validatePresign(PresignCommand command) {
        if (command == null) throw invalidUpload("上传参数无效");
        String fileName = command.fileName() == null ? "" : command.fileName().trim();
        if (fileName.isEmpty() || fileName.length() > 255 || fileName.chars().anyMatch(Character::isISOControl)) {
            throw invalidUpload("文件名无效");
        }
        String mimeType = command.mimeType() == null ? "" : command.mimeType().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("image/jpeg", "image/png", "image/webp").contains(mimeType)) throw invalidUpload("仅支持 JPEG、PNG 或 WebP 图片");
        if (command.fileSize() < 1 || command.fileSize() > MAX_FILE_SIZE) throw invalidUpload("文件大小必须在 1B 到 10MiB 之间");
        if (command.sha256() == null || !command.sha256().matches("[0-9a-f]{64}")) throw invalidUpload("SHA-256 必须为 64 位小写十六进制");
        return new ValidatedUpload(fileName, mimeType, command.fileSize(), command.sha256());
    }

    private String normalizeStatus(String value, boolean allowEmpty) {
        if (value == null || value.isBlank()) {
            if (allowEmpty) return null;
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板封面状态无效");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板封面状态无效");
        return normalized;
    }

    private String temporaryObjectKey(String mimeType, Instant now) {
        return objectKey("upload", mimeType, now);
    }

    private String finalObjectKey(String mimeType, Instant now) {
        return objectKey("", mimeType, now);
    }

    private String objectKey(String segment, String mimeType, Instant now) {
        ZonedDateTime timestamp = now.atZone(ZoneOffset.UTC);
        String prefix = segment.isBlank() ? "platform/template-cover" : "platform/template-cover/upload";
        return "%s/%04d/%02d/%s.%s".formatted(prefix, timestamp.getYear(), timestamp.getMonthValue(), UUID.randomUUID(), extensionFor(mimeType));
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw invalidUpload("图片格式无效");
        };
    }

    private void reject(TemplateCoverAdminRepository.UploadSession session, String reason) {
        reject(session, reason, null);
        throw invalidUpload(reason);
    }

    private void reject(TemplateCoverAdminRepository.UploadSession session, String reason, String extraObjectKey) {
        if (repository.markRejected(session.id(), truncate(reason))) {
            deleteAfterCommit(session.objectKey());
            if (extraObjectKey != null) deleteAfterCommit(extraObjectKey);
        }
    }

    private boolean registerPromotionCleanup(String temporaryObjectKey, String finalObjectKey) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) return false;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                deleteQuietly(temporaryObjectKey);
                if (status != STATUS_COMMITTED) deleteQuietly(finalObjectKey);
            }
        });
        return true;
    }

    private void deleteAfterCommit(String objectKey) {
        if (objectKey == null) return;
        if (TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deleteQuietly(objectKey); }
            });
        } else {
            deleteQuietly(objectKey);
        }
    }

    private void deleteQuietly(String objectKey) {
        try { storageGateway.delete(objectKey); }
        catch (StorageFailureException exception) { LOGGER.warn("Could not delete template cover object, objectKey={}", objectKey, exception); }
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private BusinessException invalidUpload(String message) { return new BusinessException(ErrorCode.INVALID_UPLOAD, message); }
    private String truncate(String reason) { return reason == null ? "上传失败" : reason.substring(0, Math.min(255, reason.length())); }

    private CoverView toView(TemplateCoverAdminRepository.CoverAssetRecord cover) {
        return new CoverView(cover.id(), cover.objectKey(), cover.mimeType(), cover.fileSize(), cover.sha256(), cover.width(), cover.height(), cover.status(), cover.createdAt(), cover.updatedAt());
    }

    private Map<String, Object> metadata(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    public record PresignCommand(String fileName, String mimeType, long fileSize, String sha256) {}
    public record CompleteCommand(long sessionId) {}
    public record PresignResult(long sessionId, String objectKey, String uploadUrl, Instant expiresAt) {}
    public record CoverView(long id, String objectKey, String mimeType, long fileSize, String sha256, int width, int height, String status, Instant createdAt, Instant updatedAt) {}
    public record BindingResult(long templateId, Long coverAssetId) {}
    private record ValidatedUpload(String fileName, String mimeType, long fileSize, String sha256) {}
}
