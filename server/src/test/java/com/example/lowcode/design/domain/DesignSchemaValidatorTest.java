package com.example.lowcode.design.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
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

        assertThatCode(() -> validator.validate(schema)).doesNotThrowAnyException();
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
