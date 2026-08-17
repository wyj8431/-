package com.example.lowcode.design.application;

import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.design.domain.DesignSchemaValidator;
import com.example.lowcode.template.application.TemplateQueryService;
import com.example.lowcode.template.application.TemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DesignService {
    private static final int MAX_NAME_LENGTH = 128;

    private final TemplateRepository templateRepository;
    private final DesignRepository designRepository;
    private final DesignSchemaValidator designSchemaValidator;
    private final ObjectMapper objectMapper;

    public DesignService(
        TemplateRepository templateRepository,
        DesignRepository designRepository,
        DesignSchemaValidator designSchemaValidator,
        ObjectMapper objectMapper
    ) {
        this.templateRepository = templateRepository;
        this.designRepository = designRepository;
        this.designSchemaValidator = designSchemaValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DesignView create(CurrentUser currentUser, CreateDesignCommand command) {
        String name = validateName(command.name());
        TemplateQueryService.TemplateDetail template = templateRepository.findPublishedById(command.templateId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模板不存在或未发布"));
        JsonNode schema = template.schema().deepCopy();
        designSchemaValidator.validate(schema);

        long documentId = designRepository.insertDocument(
            currentUser.tenantId(),
            currentUser.userId(),
            template.id(),
            name,
            template.width(),
            template.height(),
            objectMapper.valueToTree(template.fields())
        );
        designRepository.insertVersion(currentUser.tenantId(), documentId, 1, schema, currentUser.userId());
        designRepository.insertPermission(currentUser.tenantId(), documentId, currentUser.userId(), "OWNER");

        return new DesignView(
            documentId,
            template.id(),
            name,
            template.width(),
            template.height(),
            1,
            schema,
            Instant.now()
        );
    }

    @Transactional
    public DesignView save(CurrentUser currentUser, long documentId, SaveDesignCommand command) {
        if (command.baseVersion() < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "基础版本号无效");
        }
        designSchemaValidator.validate(command.schema());
        requireEditPermission(currentUser, documentId);

        if (!designRepository.advanceVersion(currentUser.tenantId(), documentId, command.baseVersion())) {
            throw new BusinessException(ErrorCode.DESIGN_VERSION_CONFLICT, "设计稿已被更新，请重新加载");
        }
        designRepository.insertVersion(
            currentUser.tenantId(),
            documentId,
            command.baseVersion() + 1,
            command.schema().deepCopy(),
            currentUser.userId()
        );
        return toView(requireViewableSnapshot(currentUser, documentId));
    }

    public DesignView findById(CurrentUser currentUser, long documentId) {
        return toView(requireViewableSnapshot(currentUser, documentId));
    }

    public List<VersionView> findVersions(CurrentUser currentUser, long documentId) {
        requireViewableSnapshot(currentUser, documentId);
        return designRepository.findVersions(currentUser.tenantId(), documentId);
    }

    private void requireEditPermission(CurrentUser currentUser, long documentId) {
        if (designRepository.canEdit(currentUser.tenantId(), documentId, currentUser.userId())) {
            return;
        }
        if (designRepository.canView(currentUser.tenantId(), documentId, currentUser.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无编辑权限");
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "设计稿不存在");
    }

    private DesignRepository.DesignSnapshot requireViewableSnapshot(CurrentUser currentUser, long documentId) {
        if (!designRepository.canView(currentUser.tenantId(), documentId, currentUser.userId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设计稿不存在");
        }
        return designRepository.findSnapshot(currentUser.tenantId(), documentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "设计稿不存在"));
    }

    private DesignView toView(DesignRepository.DesignSnapshot snapshot) {
        return new DesignView(
            snapshot.id(),
            snapshot.templateId(),
            snapshot.name(),
            snapshot.width(),
            snapshot.height(),
            snapshot.currentVersion(),
            snapshot.schema(),
            snapshot.updatedAt()
        );
    }

    private String validateName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "设计稿名称无效");
        }
        return normalized;
    }

    public record CreateDesignCommand(long templateId, String name) {
    }

    public record SaveDesignCommand(int baseVersion, JsonNode schema) {
    }

    public record DesignView(
        long id,
        long templateId,
        String name,
        int width,
        int height,
        int currentVersion,
        JsonNode schema,
        Instant updatedAt
    ) {
    }

    public record VersionView(long id, int versionNo, long createdBy, Instant createdAt) {
    }
}
