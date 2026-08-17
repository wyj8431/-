package com.example.lowcode.integration;

import com.example.lowcode.design.domain.DesignSchemaValidator;
import com.example.lowcode.auth.support.TestAuthConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
class DatabaseMigrationIT extends MySqlIntegrationTestSupport {
    private static final List<String> EXPECTED_TABLES = List.of(
        "sys_user",
        "sys_tenant",
        "sys_tenant_member",
        "design_template",
        "template_field",
        "design_document",
        "design_version",
        "design_permission",
        "asset_upload_session",
        "asset"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesEveryMvpTable() {
        Integer tableCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            Integer.class,
            EXPECTED_TABLES.toArray()
        );

        assertThat(tableCount).isEqualTo(EXPECTED_TABLES.size());
    }

    @Test
    void seedTemplateContainsValidSchemaAndMatchingEditableFields() throws Exception {
        String schemaJson = jdbcTemplate.queryForObject(
            "SELECT schema_json FROM design_template WHERE id = 1001",
            String.class
        );
        JsonNode schema = new ObjectMapper().readTree(schemaJson);
        new DesignSchemaValidator().validate(schema);

        Set<String> elementIds = schema.path("pages").get(0).path("elements").valueStream()
            .map(element -> element.path("id").asText())
            .collect(Collectors.toSet());
        List<TemplateFieldRow> fields = jdbcTemplate.query(
            """
                SELECT field_key, element_id, field_type, is_required, default_value
                FROM template_field
                WHERE template_id = 1001
                ORDER BY field_key
                """,
            (resultSet, rowNumber) -> new TemplateFieldRow(
                resultSet.getString("field_key"),
                resultSet.getString("element_id"),
                resultSet.getString("field_type"),
                resultSet.getBoolean("is_required"),
                resultSet.getString("default_value")
            )
        );

        assertThat(elementIds).containsExactlyInAnyOrder("text-product-name", "image-product-image");
        assertThat(schema.at("/pages/0/elements/1/props/src").asText()).isEmpty();
        assertThat(fields).containsExactlyInAnyOrder(
            new TemplateFieldRow("productImage", "image-product-image", "IMAGE", true, ""),
            new TemplateFieldRow("productName", "text-product-name", "TEXT", true, "")
        );
        assertThat(fields).allSatisfy(field -> assertThat(elementIds).contains(field.elementId()));
    }

    @Test
    void migrationDefinesRequiredIndexesAndTenantForeignKeys() {
        assertThat(indexNames("sys_user")).contains("uk_user_phone");
        assertThat(indexNames("design_document"))
            .contains("uk_document_id_tenant", "idx_design_tenant_updated", "idx_design_owner_updated");
        assertThat(indexNames("asset")).contains("uk_asset_object_key");
        assertThat(indexNames("asset_upload_session")).contains("idx_upload_status_expiry");
        assertThat(foreignKeyNames("design_version")).contains("fk_version_document_tenant");
        assertThat(foreignKeyNames("design_permission"))
            .contains("fk_permission_document_tenant", "fk_permission_tenant_member");
    }

    private List<String> indexNames(String tableName) {
        return jdbcTemplate.queryForList(
            """
                SELECT DISTINCT index_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ?
                """,
            String.class,
            tableName
        );
    }

    private List<String> foreignKeyNames(String tableName) {
        return jdbcTemplate.queryForList(
            """
                SELECT constraint_name
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE() AND table_name = ?
                """,
            String.class,
            tableName
        );
    }

    private record TemplateFieldRow(
        String fieldKey,
        String elementId,
        String fieldType,
        boolean required,
        String defaultValue
    ) {
    }
}
