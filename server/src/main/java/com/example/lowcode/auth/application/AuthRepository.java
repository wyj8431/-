package com.example.lowcode.auth.application;

import java.util.Optional;

public interface AuthRepository {
    Optional<UserIdentity> findByPhone(String phone);

    UserIdentity createUserWithDefaultTenant(String phone);

    record UserIdentity(long userId, Long tenantId, String phone, String userStatus, String tenantStatus) {
    }
}
