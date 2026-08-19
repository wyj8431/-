package com.example.lowcode.auth.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.auth.security.JwtTokenService;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern MAINLAND_CHINA_PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Set<String> TENANT_ROLES = Set.of("ADMIN", "USER", "OPERATOR");

    private final AuthRepository authRepository;
    private final VerificationCodeVerifier verificationCodeVerifier;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;

    public AuthService(
        AuthRepository authRepository,
        VerificationCodeVerifier verificationCodeVerifier,
        JwtTokenService jwtTokenService
    ) {
        this(authRepository, verificationCodeVerifier, jwtTokenService, null, null);
    }

    @Autowired
    public AuthService(
        AuthRepository authRepository,
        VerificationCodeVerifier verificationCodeVerifier,
        JwtTokenService jwtTokenService,
        RefreshTokenService refreshTokenService,
        AuditLogService auditLogService
    ) {
        this.authRepository = authRepository;
        this.verificationCodeVerifier = verificationCodeVerifier;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public LoginResult login(LoginCommand command) {
        return login(command, null, null);
    }

    @Transactional
    public LoginResult login(LoginCommand command, String userAgent, String ipAddress) {
        String phone = normalizePhone(command.phone());
        if (!verificationCodeVerifier.verify(phone, command.verificationCode())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "验证码错误或已过期");
        }

        AuthRepository.UserIdentity identity = authRepository.findByPhone(phone)
            .orElseGet(() -> authRepository.createUserWithDefaultTenant(phone));
        validateLoginIdentity(identity);
        validateTenantRole(identity);
        RefreshTokenService.IssuedSession session = refreshTokenService == null
            ? null
            : refreshTokenService.issue(
                identity.userId(),
                identity.tenantId(),
                identity.tenantRole(),
                identity.securityVersion(),
                userAgent,
                ipAddress
            );
        JwtTokenService.IssuedToken fallbackToken = session == null
            ? jwtTokenService.issue(identity.userId(), identity.tenantId(), identity.tenantRole())
            : null;
        String accessToken = session == null
            ? fallbackToken.value()
            : session.accessToken();
        long expiresIn = session == null
            ? fallbackToken.expiresInSeconds()
            : session.expiresInSeconds();

        if (auditLogService != null) {
            auditLogService.record(new AuditLogService.AuditEvent(
                identity.userId(), identity.tenantId(), "LOGIN", "AUTH_SESSION", null,
                AuditLogService.Outcome.SUCCESS, null,
                Map.of("tenantRole", identity.tenantRole())
            ));
        }

        return new LoginResult(
            accessToken,
            "Bearer",
            expiresIn,
            identity.userId(),
            identity.tenantId(),
            identity.tenantRole(),
            session == null ? null : session.refreshToken()
        );
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        RefreshTokenService service = requireRefreshTokenService();
        RefreshTokenService.IssuedSession session = service.rotate(rawRefreshToken);
        if (auditLogService != null) {
            auditLogService.record(new AuditLogService.AuditEvent(
                null, null, "REFRESH", "AUTH_SESSION", null,
                AuditLogService.Outcome.SUCCESS, null, Map.of()
            ));
        }
        return new RefreshResult(session.accessToken(), "Bearer", session.expiresInSeconds(), session.refreshToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (refreshTokenService != null) {
            refreshTokenService.revoke(rawRefreshToken, "LOGOUT");
        }
    }

    @Transactional(readOnly = true)
    public MeResult me(CurrentUser currentUser) {
        AuthRepository.UserIdentity identity = authRepository
            .findByUserAndTenant(currentUser.userId(), currentUser.tenantId())
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效"));
        validateLoginIdentity(identity);
        validateTenantRole(identity);
        return new MeResult(identity.userId(), identity.tenantId(), identity.phone(), identity.tenantRole());
    }

    private RefreshTokenService requireRefreshTokenService() {
        if (refreshTokenService == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效");
        }
        return refreshTokenService;
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
        String tenantRole,
        String refreshToken
    ) {
        public LoginResult(
            String accessToken,
            String tokenType,
            long expiresIn,
            long userId,
            long tenantId,
            String tenantRole
        ) {
            this(accessToken, tokenType, expiresIn, userId, tenantId, tenantRole, null);
        }

        @JsonIgnore
        @Override
        public String refreshToken() {
            return refreshToken;
        }
    }

    public record RefreshResult(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken
    ) {
        @JsonIgnore
        @Override
        public String refreshToken() {
            return refreshToken;
        }
    }

    public record MeResult(
        long userId,
        long tenantId,
        String phone,
        String tenantRole
    ) {
    }
}
