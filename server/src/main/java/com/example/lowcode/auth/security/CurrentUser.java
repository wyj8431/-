package com.example.lowcode.auth.security;

import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import org.springframework.security.oauth2.jwt.Jwt;

public record CurrentUser(long userId, long tenantId) {
    public static CurrentUser fromJwt(Jwt jwt) {
        try {
            long userId = Long.parseLong(jwt.getSubject());
            Object tenantClaim = jwt.getClaim("tenantId");
            if (!(tenantClaim instanceof Number number)) {
                throw new NumberFormatException("tenantId is not numeric");
            }
            long tenantId = number.longValue();
            if (userId <= 0 || tenantId <= 0) {
                throw new NumberFormatException("identity claim must be positive");
            }
            return new CurrentUser(userId, tenantId);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录身份无效");
        }
    }
}
