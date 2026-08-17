package com.example.lowcode.auth.application;

import com.example.lowcode.auth.security.JwtTokenService;
import com.example.lowcode.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest {
    private final InMemoryAuthRepository userRepository = new InMemoryAuthRepository();
    private final AuthService service = new AuthService(
        userRepository,
        (phone, verificationCode) -> "123456".equals(verificationCode),
        new JwtTokenService("test-signing-secret-must-have-at-least-thirty-two-bytes", Duration.ofMinutes(15))
    );

    @Test
    void firstSuccessfulLoginCreatesUserDefaultTenantAndToken() {
        AuthService.LoginResult result = service.login(new AuthService.LoginCommand("13800000000", "123456"));

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(900);
        assertThat(result.userId()).isPositive();
        assertThat(result.tenantId()).isPositive();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void wrongCodeDoesNotCreateUser() {
        assertThatThrownBy(() -> service.login(new AuthService.LoginCommand("13800000000", "000000")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("验证码");

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void invalidPhoneDoesNotCreateUser() {
        assertThatThrownBy(() -> service.login(new AuthService.LoginCommand("123", "123456")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("手机号");

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void disabledUserCannotReceiveToken() {
        userRepository.addExisting("13800000000", 1, 2L, "DISABLED", "ACTIVE");

        assertThatThrownBy(() -> service.login(new AuthService.LoginCommand("13800000000", "123456")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("禁用");
    }

    @Test
    void disabledTenantCannotReceiveToken() {
        userRepository.addExisting("13800000000", 1, 2L, "ACTIVE", "DISABLED");

        assertThatThrownBy(() -> service.login(new AuthService.LoginCommand("13800000000", "123456")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("禁用");
    }

    @Test
    void existingUserWithoutTenantIsRejectedWithoutCreatingAnotherAccount() {
        userRepository.addExisting("13800000000", 1, null, "ACTIVE", null);

        assertThatThrownBy(() -> service.login(new AuthService.LoginCommand("13800000000", "123456")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("团队");

        assertThat(userRepository.count()).isEqualTo(1);
    }

    private static final class InMemoryAuthRepository implements AuthRepository {
        private final Map<String, UserIdentity> users = new HashMap<>();
        private long nextId = 1;

        @Override
        public Optional<UserIdentity> findByPhone(String phone) {
            return Optional.ofNullable(users.get(phone));
        }

        @Override
        public UserIdentity createUserWithDefaultTenant(String phone) {
            UserIdentity identity = new UserIdentity(nextId++, nextId++, phone, "ACTIVE", "ACTIVE");
            users.put(phone, identity);
            return identity;
        }

        void addExisting(String phone, long userId, Long tenantId, String userStatus, String tenantStatus) {
            users.put(phone, new UserIdentity(userId, tenantId, phone, userStatus, tenantStatus));
        }

        int count() {
            return users.size();
        }
    }
}
