package com.example.lowcode.auth.api;

import com.example.lowcode.auth.support.TestAuthConfiguration;
import com.example.lowcode.auth.security.JwtTokenService;
import com.example.lowcode.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
class AuthSecurityTest extends MySqlIntegrationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void unauthenticatedDesignCreationReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/designs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidBearerTokenReturnsJsonUnauthorizedResponse() throws Exception {
        mockMvc.perform(post("/api/v1/designs")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void authenticatedRequestDeniedByPolicyReturnsJsonForbiddenResponse() throws Exception {
        String token = jwtTokenService.issue(1L, 1L).value();

        mockMvc.perform(post("/not-an-api-route")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void swaggerDocumentationIsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }

    @Test
    void missingSwaggerUiResourceReturns404InsteadOf500() throws Exception {
        mockMvc.perform(get("/swagger-ui/not-found.js"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
