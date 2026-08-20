package com.example.lowcode.auth.application;

public interface WechatOAuthProvider {
    boolean available();

    String authorizeUrl(String state);

    OpenId exchangeCode(String code);

    record OpenId(String value) {
        public OpenId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("WeChat OpenID is required");
            }
        }
    }

    final class ExchangeFailedException extends RuntimeException {
        public ExchangeFailedException() {
            super("WeChat OAuth code exchange failed");
        }

        public ExchangeFailedException(Throwable cause) {
            super("WeChat OAuth code exchange failed", cause);
        }
    }
}
