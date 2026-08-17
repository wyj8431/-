package com.example.lowcode.design.application;

import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.design.domain.DesignSchemaValidator;
import com.example.lowcode.template.application.TemplateQueryService;
import com.example.lowcode.template.application.TemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CurrentUser currentUser = new CurrentUser(7L, 11L);
    private final FakeDesignRepository designRepository = new FakeDesignRepository();
    private final DesignService service = new DesignService(
        new FakeTemplateRepository(),
        designRepository,
        new DesignSchemaValidator(),
        objectMapper
    );

    @Test
    void createsVersionOneByCopyingPublishedTemplate() {
        DesignService.DesignView view = service.create(
            currentUser,
            new DesignService.CreateDesignCommand(1001L, "周末促销")
        );

        assertThat(view.currentVersion()).isEqualTo(1);
        assertThat(view.schema().path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(designRepository.role(view.id(), currentUser.userId())).isEqualTo("OWNER");
        assertThat(designRepository.versions(view.id())).extracting(DesignService.VersionView::versionNo)
            .containsExactly(1);
    }

    @Test
    void savesNextImmutableVersion() throws Exception {
        DesignService.DesignView created = service.create(
            currentUser,
            new DesignService.CreateDesignCommand(1001L, "周末促销")
        );

        DesignService.DesignView updated = service.save(
            currentUser,
            created.id(),
            new DesignService.SaveDesignCommand(1, updatedSchema())
        );

        assertThat(updated.currentVersion()).isEqualTo(2);
        assertThat(designRepository.versions(created.id())).extracting(DesignService.VersionView::versionNo)
            .containsExactly(1, 2);
    }

    @Test
    void staleVersionIsRejectedWithoutWriting() throws Exception {
        DesignService.DesignView created = service.create(
            currentUser,
            new DesignService.CreateDesignCommand(1001L, "周末促销")
        );
        service.save(currentUser, created.id(), new DesignService.SaveDesignCommand(1, updatedSchema()));

        assertThatThrownBy(() -> service.save(
            currentUser,
            created.id(),
            new DesignService.SaveDesignCommand(1, updatedSchema())
        )).isInstanceOf(BusinessException.class)
            .hasMessageContaining("已被更新");
        assertThat(designRepository.versions(created.id())).hasSize(2);
    }

    @Test
    void viewerCannotWriteNewVersion() throws Exception {
        DesignService.DesignView created = service.create(
            currentUser,
            new DesignService.CreateDesignCommand(1001L, "周末促销")
        );
        CurrentUser viewer = new CurrentUser(8L, currentUser.tenantId());
        designRepository.grant(created.id(), viewer.userId(), "VIEWER");

        assertThatThrownBy(() -> service.save(
            viewer,
            created.id(),
            new DesignService.SaveDesignCommand(1, updatedSchema())
        )).isInstanceOf(BusinessException.class)
            .hasMessageContaining("编辑");
    }

    private JsonNode updatedSchema() throws Exception {
        return objectMapper.readTree("""
            {"schemaVersion":1,"canvas":{"width":1080,"height":1440,"background":"#ffffff"},
             "pages":[{"id":"page-1","elements":[
               {"id":"text-product-name","type":"text",
                "transform":{"x":96,"y":180,"width":888,"height":96,"rotate":0},
                "props":{"text":"限时优惠"},"visible":true,"locked":false,"zIndex":1}
             ]}]}
            """);
    }

    private final class FakeTemplateRepository implements TemplateRepository {
        @Override
        public List<TemplateQueryService.TemplateSummary> findPublished() {
            return List.of();
        }

        @Override
        public Optional<TemplateQueryService.TemplateDetail> findPublishedById(long templateId) {
            if (templateId != 1001L) {
                return Optional.empty();
            }
            try {
                return Optional.of(new TemplateQueryService.TemplateDetail(
                    1001L,
                    "朋友圈促销",
                    1080,
                    1440,
                    null,
                    objectMapper.readTree("""
                        {"schemaVersion":1,"canvas":{"width":1080,"height":1440,"background":"#ffffff"},
                         "pages":[{"id":"page-1","elements":[
                           {"id":"text-product-name","type":"text",
                            "transform":{"x":96,"y":180,"width":888,"height":96,"rotate":0},
                            "props":{"text":"商品名称"},"visible":true,"locked":false,"zIndex":1}
                         ]}]}
                        """),
                    List.of(new TemplateQueryService.TemplateFieldView(
                        "productName", "商品名称", "TEXT", true, ""
                    ))
                ));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class FakeDesignRepository implements DesignRepository {
        private long nextId = 2001L;
        private final Map<Long, DocumentState> documents = new HashMap<>();
        private final Map<Long, List<DesignService.VersionView>> versions = new HashMap<>();
        private final Map<Long, Map<Long, String>> permissions = new HashMap<>();

        @Override
        public long insertDocument(
            long tenantId,
            long ownerId,
            long templateId,
            String name,
            int width,
            int height,
            JsonNode templateFieldSnapshot
        ) {
            long id = nextId++;
            documents.put(id, new DocumentState(id, tenantId, templateId, name, width, height, 1, null, Instant.now()));
            return id;
        }

        @Override
        public void insertVersion(long tenantId, long documentId, int versionNo, JsonNode schema, long createdBy) {
            DocumentState document = documents.get(documentId);
            document.schema = schema.deepCopy();
            document.updatedAt = Instant.now();
            versions.computeIfAbsent(documentId, ignored -> new ArrayList<>())
                .add(new DesignService.VersionView(documentId * 10 + versionNo, versionNo, createdBy, document.updatedAt));
        }

        @Override
        public void insertPermission(long tenantId, long documentId, long userId, String role) {
            grant(documentId, userId, role);
        }

        @Override
        public Optional<DesignRepository.DesignSnapshot> findSnapshot(long tenantId, long documentId) {
            DocumentState document = documents.get(documentId);
            if (document == null || document.tenantId != tenantId || document.schema == null) {
                return Optional.empty();
            }
            return Optional.of(new DesignRepository.DesignSnapshot(
                document.id,
                document.templateId,
                document.name,
                document.width,
                document.height,
                document.currentVersion,
                document.schema.deepCopy(),
                document.updatedAt
            ));
        }

        @Override
        public boolean canView(long tenantId, long documentId, long userId) {
            return documents.containsKey(documentId)
                && documents.get(documentId).tenantId == tenantId
                && permissions.getOrDefault(documentId, Map.of()).containsKey(userId);
        }

        @Override
        public boolean canEdit(long tenantId, long documentId, long userId) {
            String role = permissions.getOrDefault(documentId, Map.of()).get(userId);
            return documents.containsKey(documentId)
                && documents.get(documentId).tenantId == tenantId
                && ("OWNER".equals(role) || "EDITOR".equals(role));
        }

        @Override
        public boolean advanceVersion(long tenantId, long documentId, int baseVersion) {
            DocumentState document = documents.get(documentId);
            if (document == null || document.tenantId != tenantId || document.currentVersion != baseVersion) {
                return false;
            }
            document.currentVersion++;
            document.updatedAt = Instant.now();
            return true;
        }

        @Override
        public List<DesignService.VersionView> findVersions(long tenantId, long documentId) {
            return List.copyOf(versions.getOrDefault(documentId, List.of()));
        }

        void grant(long documentId, long userId, String role) {
            permissions.computeIfAbsent(documentId, ignored -> new HashMap<>()).put(userId, role);
        }

        String role(long documentId, long userId) {
            return permissions.getOrDefault(documentId, Map.of()).get(userId);
        }

        List<DesignService.VersionView> versions(long documentId) {
            return List.copyOf(versions.getOrDefault(documentId, List.of()));
        }

        private static final class DocumentState {
            private final long id;
            private final long tenantId;
            private final long templateId;
            private final String name;
            private final int width;
            private final int height;
            private int currentVersion;
            private JsonNode schema;
            private Instant updatedAt;

            private DocumentState(
                long id,
                long tenantId,
                long templateId,
                String name,
                int width,
                int height,
                int currentVersion,
                JsonNode schema,
                Instant updatedAt
            ) {
                this.id = id;
                this.tenantId = tenantId;
                this.templateId = templateId;
                this.name = name;
                this.width = width;
                this.height = height;
                this.currentVersion = currentVersion;
                this.schema = schema;
                this.updatedAt = updatedAt;
            }
        }
    }
}
