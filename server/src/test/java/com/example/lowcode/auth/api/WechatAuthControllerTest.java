package com.example.lowcode.auth.api;

import com.example.lowcode.auth.support.TestAuthConfiguration;
import com.example.lowcode.integration.MySqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
class WechatAuthControllerTest extends MySqlIntegrationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unboundWechatCallbackNeverSetsSessionCookie() throws Exception {
        String state = authorizeState("/");

        mockMvc.perform(get("/api/v1/auth/wechat/callback")
                .param("code", "unbound")
                .param("state", state))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("result=unbound")))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void boundWechatCallbackSetsCookieWithoutLeakingIdentityOrTokens() throws Exception {
        String phone = "13900009001";
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"verificationCode\":\"123456\"}"))
            .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE sys_user SET wechat_open_id = ? WHERE phone = ?", "test-openid-bound", phone);
        String state = authorizeState("/templates");

        mockMvc.perform(get("/api/v1/auth/wechat/callback")
                .param("code", "bound")
                .param("state", state))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("poster_refresh_token=")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("result=success")))
            .andExpect(header().string(HttpHeaders.LOCATION, not(containsString("test-openid-bound"))))
            .andExpect(header().string(HttpHeaders.LOCATION, not(containsString("accessToken"))))
            .andExpect(header().string(HttpHeaders.LOCATION, not(containsString("refreshToken"))));
    }

    @Test
    void bindingAuthorizationRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/wechat/bind/authorize"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidReturnPathIsRejectedBeforeAuthorizationRedirect() throws Exception {
        mockMvc.perform(post("/api/v1/auth/wechat/login/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"returnTo\":\"https://attacker.test\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String authorizeState(String returnTo) throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/auth/wechat/login/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"returnTo\":\"" + returnTo + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.authorizeUrl").value(containsString("https://wechat.test/authorize?state=")))
            .andReturn();
        JsonNode payload = objectMapper.readTree(started.getResponse().getContentAsString());
        String authorizeUrl = payload.path("data").path("authorizeUrl").asText();
        String query = URI.create(authorizeUrl).getQuery();
        return query.substring("state=".length());
    }
}
