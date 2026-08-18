package com.example.lowcode.home.api;

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
class HomeControllerTest extends MySqlIntegrationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousUsersCanReadPublicDiscoveryHome() throws Exception {
        mockMvc.perform(get("/api/v1/home"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.trendingTags[0].code").value("promotion"))
            .andExpect(jsonPath("$.data.featuredTemplates[0].id").value(1001))
            .andExpect(jsonPath("$.data.editorialScenes[0].code").value("summer-promotion"));
    }
}
