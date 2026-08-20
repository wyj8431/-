package com.example.lowcode.auth.application;

import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class WechatAuthService {
    private final WechatOAuthStateService stateService;
    private final WechatOAuthProvider provider;
    private final AuthRepository authRepository;
    private final AuthService authService;
    private final AuditLogService auditLogService;

    public WechatAuthService(
        WechatOAuthStateService stateService,
        WechatOAuthProvider provider,
        AuthRepository authRepository,
        AuthService authService,
        AuditLogService auditLogService
    ) {
        this.stateService = stateService;
        this.provider = provider;
        this.authRepository = authRepository;
        this.authService = authService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PreparedAuthorization beginLogin(String returnPath) {
        requireAvailable();
        String normalizedPath = normalizeReturnPath(returnPath);
        WechatOAuthStateService.CreatedState state = stateService.create(
            WechatOAuthStateService.Purpose.LOGIN, null, null, normalizedPath
        );
        return new PreparedAuthorization(provider.authorizeUrl(state.rawState()), state.rawState());
    }

    @Transactional
    public PreparedAuthorization beginBind(CurrentUser currentUser) {
        requireAvailable();
        AuthRepository.UserIdentity identity = authService.requireActiveIdentity(
            currentUser.userId(), currentUser.tenantId()
        );
        WechatOAuthStateService.CreatedState state = stateService.create(
            WechatOAuthStateService.Purpose.BIND,
            identity.userId(),
            identity.tenantId(),
            "/account"
        );
        return new PreparedAuthorization(provider.authorizeUrl(state.rawState()), state.rawState());
    }

    @Transactional
    public CallbackResult complete(String code, String state, String userAgent, String ipAddress) {
        WechatOAuthStateService.ConsumedState consumed = stateService.consume(state).orElse(null);
        if (consumed == null) {
            return new CallbackResult(CallbackResultKind.EXPIRED, "/", null);
        }
        if (code == null || code.isBlank()) {
            record(consumed, CallbackResultKind.CANCELLED, null, null);
            return new CallbackResult(CallbackResultKind.CANCELLED, consumed.returnPath(), null);
        }
        WechatOAuthProvider.OpenId openId;
        try {
            openId = provider.exchangeCode(code.trim());
        } catch (WechatOAuthProvider.ExchangeFailedException exception) {
            record(consumed, CallbackResultKind.FAILED, null, null);
            return new CallbackResult(CallbackResultKind.FAILED, consumed.returnPath(), null);
        }
        return consumed.purpose() == WechatOAuthStateService.Purpose.LOGIN
            ? completeLogin(consumed, openId, userAgent, ipAddress)
            : completeBind(consumed, openId);
    }

    private CallbackResult completeLogin(
        WechatOAuthStateService.ConsumedState state,
        WechatOAuthProvider.OpenId openId,
        String userAgent,
        String ipAddress
    ) {
        AuthRepository.UserIdentity identity = authRepository.findByWechatOpenId(openId.value()).orElse(null);
        if (identity == null) {
            record(state, CallbackResultKind.UNBOUND, null, null);
            return new CallbackResult(CallbackResultKind.UNBOUND, state.returnPath(), null);
        }
        try {
            AuthService.LoginResult session = authService.issueSession(identity, userAgent, ipAddress);
            record(state, CallbackResultKind.SUCCESS, identity.userId(), identity.tenantId());
            return new CallbackResult(CallbackResultKind.SUCCESS, state.returnPath(), session.refreshToken());
        } catch (BusinessException exception) {
            record(state, CallbackResultKind.FAILED, identity.userId(), identity.tenantId());
            return new CallbackResult(CallbackResultKind.FAILED, state.returnPath(), null);
        }
    }

    private CallbackResult completeBind(
        WechatOAuthStateService.ConsumedState state,
        WechatOAuthProvider.OpenId openId
    ) {
        try {
            AuthRepository.UserIdentity identity = authService.requireActiveIdentity(
                state.initiatorUserId(), state.initiatorTenantId()
            );
            AuthRepository.UserIdentity existing = authRepository.findByWechatOpenId(openId.value()).orElse(null);
            if (existing != null && existing.userId() != identity.userId()) {
                record(state, CallbackResultKind.ALREADY_BOUND, identity.userId(), identity.tenantId());
                return new CallbackResult(CallbackResultKind.ALREADY_BOUND, state.returnPath(), null);
            }
            if (existing == null && !bind(identity.userId(), openId.value())) {
                record(state, CallbackResultKind.ALREADY_BOUND, identity.userId(), identity.tenantId());
                return new CallbackResult(CallbackResultKind.ALREADY_BOUND, state.returnPath(), null);
            }
            record(state, CallbackResultKind.SUCCESS, identity.userId(), identity.tenantId());
            return new CallbackResult(CallbackResultKind.SUCCESS, state.returnPath(), null);
        } catch (BusinessException exception) {
            record(state, CallbackResultKind.FAILED, state.initiatorUserId(), state.initiatorTenantId());
            return new CallbackResult(CallbackResultKind.FAILED, state.returnPath(), null);
        }
    }

    private boolean bind(long userId, String openId) {
        try {
            if (authRepository.bindWechatOpenId(userId, openId)) {
                return true;
            }
        } catch (DuplicateKeyException exception) {
            return false;
        }
        return authRepository.findByWechatOpenId(openId)
            .map(identity -> identity.userId() == userId)
            .orElse(false);
    }

    private void requireAvailable() {
        if (!provider.available()) {
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_UNAVAILABLE);
        }
    }

    private String normalizeReturnPath(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/")
            || normalized.startsWith("//")
            || normalized.contains("\\")
            || normalized.contains("://")
            || normalized.contains("#")
            || normalized.length() > 512) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "返回地址无效");
        }
        return normalized;
    }

    private void record(
        WechatOAuthStateService.ConsumedState state,
        CallbackResultKind result,
        Long actorUserId,
        Long tenantId
    ) {
        String action = state.purpose() == WechatOAuthStateService.Purpose.LOGIN ? "WECHAT_LOGIN" : "WECHAT_BIND";
        String resourceType = state.purpose() == WechatOAuthStateService.Purpose.LOGIN ? "AUTH_SESSION" : "USER_IDENTITY";
        auditLogService.record(new AuditLogService.AuditEvent(
            actorUserId,
            tenantId,
            action,
            resourceType,
            actorUserId == null ? null : Long.toString(actorUserId),
            result == CallbackResultKind.SUCCESS ? AuditLogService.Outcome.SUCCESS : AuditLogService.Outcome.FAILURE,
            null,
            Map.of("purpose", state.purpose().name(), "result", result.name())
        ));
    }

    public record PreparedAuthorization(String authorizeUrl, String state) {
    }

    public enum CallbackResultKind {
        SUCCESS,
        UNBOUND,
        ALREADY_BOUND,
        CANCELLED,
        FAILED,
        EXPIRED
    }

    public record CallbackResult(CallbackResultKind kind, String returnPath, String refreshToken) {
    }
}
