package com.example.lowcode.template.api;

import com.example.lowcode.auth.support.TestAuthConfiguration;
import com.example.lowcode.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
class TemplateControllerTest extends MySqlIntegrationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousUsersCanListPublishedTemplateMetadataWithoutSchema() throws Exception {
        mockMvc.perform(get("/api/v1/templates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data[0].id").value(1001))
            .andExpect(jsonPath("$.data[0].fields[0].fieldKey").value("productName"))
            .andExpect(jsonPath("$.data[0].schema").doesNotExist());
    }

    @Test
    void anonymousUsersCanReadPublishedTemplateDetailButNotUnknownTemplate() throws Exception {
        mockMvc.perform(get("/api/v1/templates/1001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.schema.schemaVersion").value(1))
            .andExpect(jsonPath("$.data.fields[1].fieldKey").value("productImage"));

        mockMvc.perform(get("/api/v1/templates/1002"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
