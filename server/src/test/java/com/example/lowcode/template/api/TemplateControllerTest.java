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
    void anonymousUsersCanSearchPublishedTemplateCardsAndRejectInvalidFilters() throws Exception {
        mockMvc.perform(get("/api/v1/templates")
                .queryParam("keyword", "促销")
                .queryParam("categoryCode", "marketing")
                .queryParam("tagCode", "promotion"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(24))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(1001))
            .andExpect(jsonPath("$.data.items[0].categoryCode").value("marketing"))
            .andExpect(jsonPath("$.data.items[0].tagCodes[0]").value("promotion"))
            .andExpect(jsonPath("$.data.items[0].fields").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].schema").doesNotExist());

        mockMvc.perform(get("/api/v1/templates").queryParam("pageSize", "49"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/templates").queryParam("categoryCode", "unknown"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/templates").queryParam("tagCode", "unknown"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void anonymousUsersCanListPublishedTemplateCategories() throws Exception {
        mockMvc.perform(get("/api/v1/template-categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data[0].code").value("marketing"))
            .andExpect(jsonPath("$.data[0].name").value("营销推广"))
            .andExpect(jsonPath("$.data[0].parentCode").doesNotExist());
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
