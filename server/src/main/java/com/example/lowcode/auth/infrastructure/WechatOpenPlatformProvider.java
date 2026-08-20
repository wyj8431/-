package com.example.lowcode.auth.infrastructure;

import com.example.lowcode.auth.application.WechatOAuthProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

public class WechatOpenPlatformProvider implements WechatOAuthProvider {
    private static final String AUTHORIZE_URL = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";

    private final String appId;
    private final String appSecret;
    private final String redirectUri;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WechatOpenPlatformProvider(
        String appId,
        String appSecret,
        String redirectUri,
        OkHttpClient httpClient,
        ObjectMapper objectMapper
    ) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.redirectUri = redirectUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
            .queryParam("appid", appId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "snsapi_login")
            .queryParam("state", state)
            .fragment("wechat_redirect")
            .build()
            .encode()
            .toUriString();
    }

    @Override
    public OpenId exchangeCode(String code) {
        String url = UriComponentsBuilder.fromUriString(TOKEN_URL)
            .queryParam("appid", appId)
            .queryParam("secret", appSecret)
            .queryParam("code", code)
            .queryParam("grant_type", "authorization_code")
            .build()
            .encode()
            .toUriString();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new ExchangeFailedException();
            }
            JsonNode body = objectMapper.readTree(response.body().string());
            String openId = body.path("openid").asText("").trim();
            if (openId.isBlank() || body.has("errcode")) {
                throw new ExchangeFailedException();
            }
            return new OpenId(openId);
        } catch (IOException exception) {
            throw new ExchangeFailedException(exception);
        }
    }
}
