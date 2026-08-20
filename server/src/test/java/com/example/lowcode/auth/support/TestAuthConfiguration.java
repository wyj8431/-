package com.example.lowcode.auth.support;

import com.example.lowcode.auth.application.VerificationCodeVerifier;
import com.example.lowcode.auth.application.WechatOAuthProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class TestAuthConfiguration {
    @Bean
    VerificationCodeVerifier verificationCodeVerifier() {
        return (phone, verificationCode) -> "123456".equals(verificationCode);
    }

    @Bean
    @Primary
    WechatOAuthProvider wechatOAuthProvider() {
        return new WechatOAuthProvider() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String authorizeUrl(String state) {
                return "https://wechat.test/authorize?state=" + state;
            }

            @Override
            public OpenId exchangeCode(String code) {
                if ("failure".equals(code)) {
                    throw new ExchangeFailedException();
                }
                return new OpenId("test-openid-" + code);
            }
        };
    }
}
