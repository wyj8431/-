package com.example.lowcode.auth.application;

import com.example.lowcode.auth.security.JwtTokenService;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern MAINLAND_CHINA_PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Set<String> TENANT_ROLES = Set.of("ADMIN", "USER", "OPERATOR");

    private final AuthRepository authRepository;
    private final VerificationCodeVerifier verificationCodeVerifier;
    private final JwtTokenService jwtTokenService;

    public AuthService(
        AuthRepository authRepository,
        VerificationCodeVerifier verificationCodeVerifier,
        JwtTokenService jwtTokenService
    ) {
        this.authRepository = authRepository;
        this.verificationCodeVerifier = verificationCodeVerifier;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public LoginResult login(LoginCommand command) {
        String phone = normalizePhone(command.phone());
        if (!verificationCodeVerifier.verify(phone, command.verificationCode())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "验证码错误或已过期");
        }

        AuthRepository.UserIdentity identity = authRepository.findByPhone(phone)
            .orElseGet(() -> authRepository.createUserWithDefaultTenant(phone));
        validateLoginIdentity(identity);
        validateTenantRole(identity);
        JwtTokenService.IssuedToken token = jwtTokenService.issue(
            identity.userId(),
            identity.tenantId(),
            identity.tenantRole()
        );

        return new LoginResult(
            token.value(),
            "Bearer",
            token.expiresInSeconds(),
            identity.userId(),
            identity.tenantId(),
            identity.tenantRole()
        );
    }

    private String normalizePhone(String phone) {
        String normalized = phone == null ? "" : phone.trim();
        if (!MAINLAND_CHINA_PHONE.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手机号格式无效");
        }
        return normalized;
    }

    private void validateLoginIdentity(AuthRepository.UserIdentity identity) {
        if (!"ACTIVE".equals(identity.userStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        if (identity.tenantId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号尚未加入团队");
        }
        if (!"ACTIVE".equals(identity.tenantStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "所属团队已被禁用");
        }
    }

    private void validateTenantRole(AuthRepository.UserIdentity identity) {
        if (!TENANT_ROLES.contains(identity.tenantRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前成员角色无效");
        }
    }

    public record LoginCommand(String phone, String verificationCode) {
    }

    public record LoginResult(
        String accessToken,
        String tokenType,
        long expiresIn,
        long userId,
        long tenantId,
        String tenantRole
    ) {
    }
}
