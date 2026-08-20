package com.example.lowcode.auth.infrastructure;

import com.example.lowcode.auth.application.WechatOAuthProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(WechatOAuthProperties.class)
public class WechatOAuthConfiguration {
    @Bean
    @ConditionalOnMissingBean(WechatOAuthProvider.class)
    WechatOAuthProvider wechatOAuthProvider(WechatOAuthProperties properties, ObjectMapper objectMapper) {
        if (!properties.isProviderConfigured()) {
            return new UnavailableWechatOAuthProvider();
        }
        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(5))
            .callTimeout(Duration.ofSeconds(10))
            .build();
        return new WechatOpenPlatformProvider(
            properties.getAppId(),
            properties.getAppSecret(),
            properties.getRedirectUri(),
            httpClient,
            objectMapper
        );
    }
}
