package com.example.lowcode.auth.infrastructure;

import com.example.lowcode.auth.application.VerificationCodeVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Profile("local")
public class LocalVerificationCodeVerifier implements VerificationCodeVerifier {
    private final byte[] expectedCode;

    public LocalVerificationCodeVerifier(
        @Value("${LOCAL_VERIFICATION_CODE:123456}") String expectedCode
    ) {
        this.expectedCode = expectedCode.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean verify(String phone, String verificationCode) {
        byte[] actualCode = verificationCode == null
            ? new byte[0]
            : verificationCode.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedCode, actualCode);
    }
}
