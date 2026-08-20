package com.example.lowcode.auth.infrastructure;

import com.example.lowcode.auth.application.WechatOAuthProvider;

public class UnavailableWechatOAuthProvider implements WechatOAuthProvider {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String authorizeUrl(String state) {
        throw new IllegalStateException("WeChat OAuth provider is unavailable");
    }

    @Override
    public OpenId exchangeCode(String code) {
        throw new ExchangeFailedException();
    }
}
