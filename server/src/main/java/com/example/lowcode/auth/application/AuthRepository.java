package com.example.lowcode.auth.application;

import java.util.Optional;

public interface AuthRepository {
    Optional<UserIdentity> findByPhone(String phone);

    default Optional<UserIdentity> findByUserAndTenant(long userId, long tenantId) {
        return Optional.empty();
    }

    UserIdentity createUserWithDefaultTenant(String phone);

    record UserIdentity(
        long userId,
        Long tenantId,
        String phone,
        String userStatus,
        String tenantStatus,
        String tenantRole,
        int securityVersion
    ) {
        public UserIdentity(
            long userId,
            Long tenantId,
            String phone,
            String userStatus,
            String tenantStatus,
            String tenantRole
        ) {
            this(userId, tenantId, phone, userStatus, tenantStatus, tenantRole, 0);
        }
    }
}
