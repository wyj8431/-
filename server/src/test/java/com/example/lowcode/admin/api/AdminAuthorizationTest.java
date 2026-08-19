package com.example.lowcode.admin.api;

import com.example.lowcode.auth.support.TestAuthConfiguration;
import com.example.lowcode.integration.MySqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
class AdminAuthorizationTest extends MySqlIntegrationTestSupport {
    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    @Test
    void adminCanReadSummaryButUserReceivesForbidden() {
        JsonNode adminLogin = login("13900002001");
        String adminToken = adminLogin.at("/data/accessToken").asText();
        long userId = adminLogin.at("/data/userId").asLong();
        long tenantId = adminLogin.at("/data/tenantId").asLong();

        ResponseEntity<JsonNode> summary = getSummary(adminToken);
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getBody().at("/data/memberCount").asLong()).isPositive();
        assertThat(summary.getBody().at("/data/health").asText()).isEqualTo("UP");

        jdbcTemplate.update(
            "UPDATE sys_tenant_member SET role = 'USER' WHERE user_id = ? AND tenant_id = ?",
            userId,
            tenantId
        );
        JsonNode userLogin = login("13900002001");
        assertThat(userLogin.at("/data/tenantRole").asText()).isEqualTo("USER");

        assertThat(getSummary(userLogin.at("/data/accessToken").asText()).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cannotDowngradeTheLastAdminAndRoleChangeInvalidatesTargetSession() {
        JsonNode admin = login("13900002011");
        JsonNode target = login("13900002012");
        long adminUserId = admin.at("/data/userId").asLong();
        long tenantId = admin.at("/data/tenantId").asLong();
        long targetUserId = target.at("/data/userId").asLong();
        jdbcTemplate.update(
            "INSERT INTO sys_tenant_member (tenant_id, user_id, role) VALUES (?, ?, 'ADMIN')",
            tenantId,
            targetUserId
        );
        jdbcTemplate.update(
            "UPDATE sys_tenant_member SET role = 'USER' WHERE user_id = ? AND tenant_id <> ?",
            targetUserId,
            tenantId
        );
        target = login("13900002012");

        ResponseEntity<JsonNode> changed = patchRole(admin.at("/data/accessToken").asText(), targetUserId, "USER");
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getBody().at("/data/tenantRole").asText()).isEqualTo("USER");
        assertThat(patchRole(admin.at("/data/accessToken").asText(), adminUserId, "USER").getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getSummary(target.at("/data/accessToken").asText()).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT security_version FROM sys_user WHERE id = ?", Integer.class, targetUserId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_audit_log WHERE tenant_id = ? AND action = 'ROLE_CHANGE'", Integer.class, tenantId
        )).isPositive();
    }

    @Test
    void operatorCanListMembersButCannotChangeTheirRole() {
        JsonNode operator = login("13900002021");
        long operatorUserId = operator.at("/data/userId").asLong();
        long tenantId = operator.at("/data/tenantId").asLong();
        jdbcTemplate.update(
            "UPDATE sys_tenant_member SET role = 'OPERATOR' WHERE user_id = ? AND tenant_id = ?",
            operatorUserId,
            tenantId
        );
        operator = login("13900002021");

        ResponseEntity<JsonNode> members = getMembers(operator.at("/data/accessToken").asText(), "", "");
        assertThat(members.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(members.getBody().at("/data/items").isArray()).isTrue();
        assertThat(members.getBody().at("/data/items/0/phoneMasked").asText())
            .doesNotContain("13900002021");
        assertThat(patchRole(operator.at("/data/accessToken").asText(), operatorUserId, "USER").getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void memberListIsTenantScopedAndSupportsRoleFilter() {
        JsonNode admin = login("13900002031");
        JsonNode target = login("13900002032");
        long tenantId = admin.at("/data/tenantId").asLong();
        long targetUserId = target.at("/data/userId").asLong();
        jdbcTemplate.update(
            "INSERT INTO sys_tenant_member (tenant_id, user_id, role) VALUES (?, ?, 'USER')",
            tenantId,
            targetUserId
        );

        ResponseEntity<JsonNode> members = getMembers(
            admin.at("/data/accessToken").asText(), "USER", "ACTIVE"
        );
        assertThat(members.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(members.getBody().at("/data/page").asInt()).isEqualTo(1);
        assertThat(members.getBody().at("/data/pageSize").asInt()).isEqualTo(20);
        assertThat(members.getBody().at("/data/items").toString()).contains("\"userId\":" + targetUserId);
        assertThat(members.getBody().at("/data/items").toString()).doesNotContain("13900002032");
    }

    @Test
    void disabledMemberCannotBeAssignedAnotherRole() {
        JsonNode admin = login("13900002041");
        JsonNode target = login("13900002042");
        long tenantId = admin.at("/data/tenantId").asLong();
        long targetUserId = target.at("/data/userId").asLong();
        jdbcTemplate.update(
            "INSERT INTO sys_tenant_member (tenant_id, user_id, role) VALUES (?, ?, 'USER')",
            tenantId,
            targetUserId
        );
        jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLED' WHERE id = ?", targetUserId);

        assertThat(patchRole(admin.at("/data/accessToken").asText(), targetUserId, "OPERATOR").getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private JsonNode login(String phone) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("phone", phone, "verificationCode", "123456")),
            JsonNode.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<JsonNode> getSummary(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/admin/summary",
            HttpMethod.GET,
            new HttpEntity<>(null, headers),
            JsonNode.class
        );
    }

    private ResponseEntity<JsonNode> getMembers(String accessToken, String role, String status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        String query = "?page=1&pageSize=20";
        if (!role.isBlank()) query += "&role=" + role;
        if (!status.isBlank()) query += "&status=" + status;
        return restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/admin/users" + query,
            HttpMethod.GET,
            new HttpEntity<>(null, headers),
            JsonNode.class
        );
    }

    private ResponseEntity<JsonNode> patchRole(String accessToken, long userId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/admin/users/" + userId + "/tenant-role",
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("tenantRole", role), headers),
            JsonNode.class
        );
    }
}
