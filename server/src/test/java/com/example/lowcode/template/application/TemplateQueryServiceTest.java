package com.example.lowcode.template.application;

import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateQueryServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void searchNormalizesPublicCriteriaAndReturnsCardMetadataWithoutSchema() {
        FakeTemplateRepository repository = new FakeTemplateRepository();
        TemplateQueryService service = new TemplateQueryService(repository);

        TemplatePage<TemplateQueryService.TemplateSummary> page = service.searchPublished(
            new TemplateSearchCriteria(" 夏日 ", "marketing", "promotion", 1, 24)
        );

        assertThat(repository.lastCriteria).isEqualTo(
            new TemplateSearchCriteria("夏日", "marketing", "promotion", 1, 24)
        );
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(24);
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).singleElement().satisfies(template -> {
            assertThat(template.id()).isEqualTo(1001L);
            assertThat(template.coverAssetId()).isNull();
            assertThat(template.coverUrl()).isNull();
            assertThat(template.categoryCode()).isEqualTo("marketing");
            assertThat(template.tagCodes()).containsExactly("promotion");
            assertThat(template.publishedAt()).isEqualTo(Instant.parse("2026-08-17T00:00:00Z"));
        });
    }

    @Test
    void searchRejectsInvalidPageAndUnavailableCategoryOrTag() {
        TemplateQueryService service = new TemplateQueryService(new FakeTemplateRepository());

        assertThatThrownBy(() -> service.searchPublished(
            new TemplateSearchCriteria(null, null, null, 0, 24)
        )).isInstanceOf(BusinessException.class)
            .satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.searchPublished(
            new TemplateSearchCriteria(null, "marketing!", null, 1, 24)
        )).isInstanceOf(BusinessException.class)
            .hasMessageContaining("分类");
        assertThatThrownBy(() -> service.searchPublished(
            new TemplateSearchCriteria(null, "disabled", null, 1, 24)
        )).isInstanceOf(BusinessException.class)
            .hasMessageContaining("分类");
        assertThatThrownBy(() -> service.searchPublished(
            new TemplateSearchCriteria(null, null, "unknown", 1, 24)
        )).isInstanceOf(BusinessException.class)
            .hasMessageContaining("标签");
    }

    @Test
    void listsOnlyPublishedCategories() {
        TemplateQueryService service = new TemplateQueryService(new FakeTemplateRepository());

        assertThat(service.listPublishedCategories()).containsExactly(
            new TemplateQueryService.TemplateCategoryView("marketing", "营销推广", null)
        );
    }

    @Test
    void detailReturnsPublishedTemplateSchemaAndFields() throws Exception {
        TemplateQueryService service = new TemplateQueryService(new FakeTemplateRepository());

        TemplateQueryService.TemplateDetail template = service.findPublishedById(1001L);

        assertThat(template.schema().path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(template.fields()).extracting(TemplateQueryService.TemplateFieldView::required)
            .containsOnly(true);
    }

    @Test
    void disabledOrMissingTemplateIsNotExposed() {
        TemplateQueryService service = new TemplateQueryService(new FakeTemplateRepository());

        assertThatThrownBy(() -> service.findPublishedById(1002L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("模板");
    }

    private final class FakeTemplateRepository implements TemplateRepository {
        private final TemplateQueryService.TemplateSummary summary = new TemplateQueryService.TemplateSummary(
            1001L,
            "朋友圈促销",
            1080,
            1440,
            null,
            null,
            "marketing",
            List.of("promotion"),
            Instant.parse("2026-08-17T00:00:00Z")
        );
        private TemplateSearchCriteria lastCriteria;

        @Override
        public TemplatePage<TemplateQueryService.TemplateSummary> searchPublished(TemplateSearchCriteria criteria) {
            lastCriteria = criteria;
            return new TemplatePage<>(List.of(summary), criteria.page(), criteria.pageSize(), 1L);
        }

        @Override
        public List<TemplateQueryService.TemplateCategoryView> findPublishedCategories() {
            return List.of(new TemplateQueryService.TemplateCategoryView("marketing", "营销推广", null));
        }

        @Override
        public boolean hasPublishedCategory(String code) {
            return "marketing".equals(code);
        }

        @Override
        public boolean hasPublishedTag(String code) {
            return "promotion".equals(code);
        }

        @Override
        public Optional<TemplateQueryService.TemplateDetail> findPublishedById(long templateId) {
            if (templateId != summary.id()) {
                return Optional.empty();
            }
            return Optional.of(new TemplateQueryService.TemplateDetail(
                summary.id(),
                summary.name(),
                summary.width(),
                summary.height(),
                summary.coverAssetId(),
                schema(),
                fields()
            ));
        }

        private List<TemplateQueryService.TemplateFieldView> fields() {
            return List.of(
                new TemplateQueryService.TemplateFieldView("productName", "商品名称", "TEXT", true, ""),
                new TemplateQueryService.TemplateFieldView("productImage", "商品图片", "IMAGE", true, "")
            );
        }

        private JsonNode schema() {
            try {
                return objectMapper.readTree("""
                    {"schemaVersion":1,"canvas":{"width":1080,"height":1440},
                     "pages":[{"id":"page-1","elements":[]}]}
                    """);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
