package com.example.lowcode.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.wechat")
public class WechatOAuthProperties {
    private boolean enabled;
    private String appId = "";
    private String appSecret = "";
    private String redirectUri = "";
    private String webPublicBaseUrl = "http://localhost:5173";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getWebPublicBaseUrl() { return webPublicBaseUrl; }
    public void setWebPublicBaseUrl(String webPublicBaseUrl) { this.webPublicBaseUrl = webPublicBaseUrl; }

    public boolean isProviderConfigured() {
        return enabled
            && hasText(appId)
            && hasText(appSecret)
            && redirectUri != null
            && redirectUri.startsWith("https://");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
