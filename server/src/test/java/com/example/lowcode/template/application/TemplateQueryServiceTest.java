package com.example.lowcode.template.application;

import com.example.lowcode.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateQueryServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listReturnsPublishedTemplateMetadataAndEditableFieldsWithoutSchema() {
        TemplateQueryService service = new TemplateQueryService(new FakeTemplateRepository());

        List<TemplateQueryService.TemplateSummary> templates = service.listPublished();

        assertThat(templates).singleElement().satisfies(template -> {
            assertThat(template.id()).isEqualTo(1001L);
            assertThat(template.name()).isEqualTo("朋友圈促销");
            assertThat(template.width()).isEqualTo(1080);
            assertThat(template.height()).isEqualTo(1440);
            assertThat(template.coverAssetId()).isNull();
            assertThat(template.fields()).extracting(TemplateQueryService.TemplateFieldView::fieldKey)
                .containsExactly("productName", "productImage");
        });
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
            fields()
        );

        @Override
        public List<TemplateQueryService.TemplateSummary> findPublished() {
            return List.of(summary);
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
                summary.fields()
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
