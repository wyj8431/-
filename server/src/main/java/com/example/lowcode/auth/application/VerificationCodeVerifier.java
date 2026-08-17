package com.example.lowcode.auth.application;

/**
 * Production deployments must provide a verifier backed by the selected SMS provider.
 */
public interface VerificationCodeVerifier {
    boolean verify(String phone, String verificationCode);
}
