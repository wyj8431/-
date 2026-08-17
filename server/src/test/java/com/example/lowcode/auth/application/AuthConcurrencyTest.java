package com.example.lowcode.auth.application;

import com.example.lowcode.auth.support.TestAuthConfiguration;
import com.example.lowcode.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
class AuthConcurrencyTest extends MySqlIntegrationTestSupport {
    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentFirstLoginsForSamePhoneShareOneUserAndTenant() throws Exception {
        String phone = String.format(Locale.ROOT, "139%08d", Math.floorMod(System.nanoTime(), 100_000_000L));
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AuthService.LoginResult> first = executor.submit(() -> loginAfterBarrier(phone, startBarrier));
            Future<AuthService.LoginResult> second = executor.submit(() -> loginAfterBarrier(phone, startBarrier));

            AuthService.LoginResult firstResult = first.get(30, TimeUnit.SECONDS);
            AuthService.LoginResult secondResult = second.get(30, TimeUnit.SECONDS);

            assertThat(firstResult.userId()).isEqualTo(secondResult.userId());
            assertThat(firstResult.tenantId()).isEqualTo(secondResult.tenantId());
            assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE phone = ?", Integer.class, phone
            )).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_tenant_member WHERE user_id = ?", Integer.class, firstResult.userId()
            )).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private AuthService.LoginResult loginAfterBarrier(String phone, CyclicBarrier startBarrier) throws Exception {
        startBarrier.await(10, TimeUnit.SECONDS);
        return authService.login(new AuthService.LoginCommand(phone, "123456"));
    }
}
