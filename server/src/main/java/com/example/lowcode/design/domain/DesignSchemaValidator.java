package com.example.lowcode.design.domain;

import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class DesignSchemaValidator {
    private static final Set<String> ALLOWED_TYPES = Set.of("text", "image", "rect", "icon");
    private static final Set<String> RESOURCE_PROPERTIES = Set.of("src", "url", "href");
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PAGES = 20;
    private static final int MAX_ELEMENTS_PER_PAGE = 500;
    private static final int MAX_CANVAS_DIMENSION = 10_000;
    private static final String[] TRANSFORM_PROPERTIES = {"x", "y", "width", "height", "rotate"};

    public void validate(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            reject("SCHEMA_MUST_BE_OBJECT");
        }
        if (schema.toString().getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            reject("SCHEMA_TOO_LARGE");
        }
        if (schema.path("schemaVersion").asInt(-1) != 1) {
            reject("UNSUPPORTED_SCHEMA_VERSION");
        }

        validateCanvas(schema.path("canvas"));
        validatePages(schema.path("pages"));
    }

    private void validateCanvas(JsonNode canvas) {
        if (!canvas.isObject()) {
            reject("CANVAS_MISSING");
        }
        validateCanvasDimension(canvas, "width");
        validateCanvasDimension(canvas, "height");
    }

    private void validateCanvasDimension(JsonNode canvas, String field) {
        JsonNode value = canvas.get(field);
        if (value == null || !value.canConvertToInt() || value.intValue() <= 0
            || value.intValue() > MAX_CANVAS_DIMENSION) {
            reject("INVALID_CANVAS_" + field.toUpperCase(Locale.ROOT));
        }
    }

    private void validatePages(JsonNode pages) {
        if (!pages.isArray() || pages.size() == 0 || pages.size() > MAX_PAGES) {
            reject("INVALID_PAGES");
        }

        Set<String> pageIds = new HashSet<>();
        for (JsonNode page : pages) {
            if (!page.isObject()) {
                reject("PAGE_MUST_BE_OBJECT");
            }
            String pageId = requiredString(page.get("id"), "PAGE_ID_MISSING");
            if (!pageIds.add(pageId)) {
                reject("DUPLICATE_PAGE_ID");
            }
            validateElements(page.path("elements"));
        }
    }

    private void validateElements(JsonNode elements) {
        if (!elements.isArray() || elements.size() > MAX_ELEMENTS_PER_PAGE) {
            reject("INVALID_ELEMENTS");
        }

        Set<String> elementIds = new HashSet<>();
        for (JsonNode element : elements) {
            if (!element.isObject()) {
                reject("ELEMENT_MUST_BE_OBJECT");
            }
            String elementId = requiredString(element.get("id"), "ELEMENT_ID_MISSING");
            if (!elementIds.add(elementId)) {
                reject("DUPLICATE_ELEMENT_ID");
            }
            String type = requiredString(element.get("type"), "ELEMENT_TYPE_MISSING");
            if (!ALLOWED_TYPES.contains(type)) {
                reject("UNKNOWN_ELEMENT_TYPE");
            }
        }

        for (JsonNode element : elements) {
            validateProperties(element.path("props"));
            validateTransform(element.path("transform"));
        }
    }

    private void validateProperties(JsonNode properties) {
        if (!properties.isObject()) {
            reject("PROPS_MUST_BE_OBJECT");
        }
        validatePropertyNode(properties, null);
    }

    private void validatePropertyNode(JsonNode node, String propertyName) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isEventProperty(field.getKey())) {
                    reject("UNSAFE_EVENT_PROPERTY");
                }
                validatePropertyNode(field.getValue(), field.getKey());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                validatePropertyNode(item, propertyName);
            }
            return;
        }
        if (!node.isTextual()) {
            return;
        }

        String value = node.asText();
        if (value.toLowerCase(Locale.ROOT).startsWith("javascript:")) {
            reject("UNSAFE_RESOURCE_URL");
        }
        if (propertyName != null && RESOURCE_PROPERTIES.contains(propertyName.toLowerCase(Locale.ROOT))
            && !value.isBlank() && !value.startsWith("asset://")) {
            reject("UNSAFE_RESOURCE_URL");
        }
    }

    private void validateTransform(JsonNode transform) {
        if (!transform.isObject()) {
            reject("TRANSFORM_MISSING");
        }
        for (String property : TRANSFORM_PROPERTIES) {
            JsonNode value = transform.get(property);
            if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) {
                reject("INVALID_TRANSFORM_" + property.toUpperCase(Locale.ROOT));
            }
        }
    }

    private boolean isEventProperty(String propertyName) {
        return propertyName.length() > 2 && propertyName.startsWith("on")
            && Character.isUpperCase(propertyName.charAt(2));
    }

    private String requiredString(JsonNode node, String reason) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            reject(reason);
        }
        return node.asText();
    }

    private void reject(String reason) {
        throw new BusinessException(ErrorCode.INVALID_SCHEMA, "INVALID_SCHEMA: " + reason);
    }
}
