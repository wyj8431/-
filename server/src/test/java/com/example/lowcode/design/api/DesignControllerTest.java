package com.example.lowcode.design.api;

import com.example.lowcode.auth.security.JwtTokenService;
import com.example.lowcode.auth.support.TestAuthConfiguration;
import com.example.lowcode.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
class DesignControllerTest extends MySqlIntegrationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    private long userId;
    private long tenantId;
    private String token;

    @BeforeEach
    void setUpTenantMember() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String phone = "139" + suffix.substring(0, 8);
        String tenantName = "设计测试团队-" + suffix;
        jdbcTemplate.update("INSERT INTO sys_user (phone, status) VALUES (?, 'ACTIVE')", phone);
        userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE phone = ?", Long.class, phone);
        jdbcTemplate.update("INSERT INTO sys_tenant (name, status) VALUES (?, 'ACTIVE')", tenantName);
        tenantId = jdbcTemplate.queryForObject("SELECT id FROM sys_tenant WHERE name = ?", Long.class, tenantName);
        jdbcTemplate.update(
            "INSERT INTO sys_tenant_member (tenant_id, user_id, role) VALUES (?, ?, 'OWNER')",
            tenantId,
            userId
        );
        token = jwtTokenService.issue(userId, tenantId).value();
    }

    @Test
    void createsReadsAndVersionsDesignWithOptimisticLocking() throws Exception {
        String created = mockMvc.perform(post("/api/v1/designs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"templateId":1001,"name":"周末促销"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentVersion").value(1))
            .andExpect(jsonPath("$.data.schema.schemaVersion").value(1))
            .andReturn().getResponse().getContentAsString();
        Number createdId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");
        long documentId = createdId.longValue();

        mockMvc.perform(get("/api/v1/designs/{documentId}", documentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(documentId))
            .andExpect(jsonPath("$.data.currentVersion").value(1));

        String saveRequest = """
            {"baseVersion":1,"schema":{"schemaVersion":1,
             "canvas":{"width":1080,"height":1440,"background":"#ffffff"},
             "pages":[{"id":"page-1","elements":[
               {"id":"text-product-name","type":"text",
                "transform":{"x":96,"y":180,"width":888,"height":96,"rotate":0},
                "props":{"text":"限时优惠"},"visible":true,"locked":false,"zIndex":1}
             ]}]}}
            """;
        mockMvc.perform(patch("/api/v1/designs/{documentId}", documentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentVersion").value(2));

        mockMvc.perform(patch("/api/v1/designs/{documentId}", documentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveRequest))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DESIGN_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/designs/{documentId}/versions", documentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[1].versionNo").value(2));
    }

    @Test
    void anotherTenantCannotDiscoverDesignById() throws Exception {
        String created = mockMvc.perform(post("/api/v1/designs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"templateId":1001,"name":"仅限本团队"}
                    """))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        Number createdId = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");
        long documentId = createdId.longValue();
        String otherToken = tokenForOtherTenant();

        mockMvc.perform(get("/api/v1/designs/{documentId}", documentId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private String tokenForOtherTenant() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String phone = "137" + suffix.substring(0, 8);
        String tenantName = "隔离测试团队-" + suffix;
        jdbcTemplate.update("INSERT INTO sys_user (phone, status) VALUES (?, 'ACTIVE')", phone);
        long otherUserId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE phone = ?", Long.class, phone);
        jdbcTemplate.update("INSERT INTO sys_tenant (name, status) VALUES (?, 'ACTIVE')", tenantName);
        long otherTenantId = jdbcTemplate.queryForObject("SELECT id FROM sys_tenant WHERE name = ?", Long.class, tenantName);
        jdbcTemplate.update(
            "INSERT INTO sys_tenant_member (tenant_id, user_id, role) VALUES (?, ?, 'OWNER')",
            otherTenantId,
            otherUserId
        );
        return jwtTokenService.issue(otherUserId, otherTenantId).value();
    }
}
