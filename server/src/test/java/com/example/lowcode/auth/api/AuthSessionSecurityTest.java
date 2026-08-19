package com.example.lowcode.auth.api;

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
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
class AuthSessionSecurityTest extends MySqlIntegrationTestSupport {
    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void loginSetsHttpOnlyCookieAndRefreshRotatesIt() {
        ResponseEntity<JsonNode> login = exchange(
            HttpMethod.POST,
            "/api/v1/auth/login",
            null,
            Map.of("phone", "13900001001", "verificationCode", "123456")
        );

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().at("/data/accessToken").asText()).isNotBlank();
        assertThat(login.getBody().toString()).doesNotContain("refreshToken");
        assertThat(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).startsWith("poster_refresh_token=");
        String firstCookie = cookieValue(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        assertThat(firstCookie).isNotBlank();
        assertThat(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).containsIgnoringCase("HttpOnly");

        ResponseEntity<JsonNode> refresh = exchange(
            HttpMethod.POST,
            "/api/v1/auth/refresh",
            firstCookie,
            null
        );

        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refresh.getBody().at("/data/accessToken").asText()).isNotBlank();
        assertThat(cookieValue(refresh.getHeaders().getFirst(HttpHeaders.SET_COOKIE)))
            .isNotEqualTo(firstCookie);
    }

    @Test
    void meResolvesCurrentTenantIdentityFromDatabase() {
        ResponseEntity<JsonNode> login = exchange(
            HttpMethod.POST,
            "/api/v1/auth/login",
            null,
            Map.of("phone", "13900001002", "verificationCode", "123456")
        );
        String accessToken = login.getBody().at("/data/accessToken").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<JsonNode> me = restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/auth/me",
            HttpMethod.GET,
            new HttpEntity<>(null, headers),
            JsonNode.class
        );

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().at("/data/phone").asText()).isEqualTo("13900001002");
        assertThat(me.getBody().at("/data/tenantRole").asText()).isEqualTo("ADMIN");
    }

    private ResponseEntity<JsonNode> exchange(
        HttpMethod method,
        String path,
        String cookie,
        Object body
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (cookie != null) {
            headers.set(HttpHeaders.COOKIE, cookie);
        }
        return restTemplate.exchange(
            "http://localhost:" + port + path,
            method,
            new HttpEntity<>(body, headers),
            JsonNode.class
        );
    }

    private String cookieValue(String setCookie) {
        assertThat(setCookie).isNotBlank();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }
}
