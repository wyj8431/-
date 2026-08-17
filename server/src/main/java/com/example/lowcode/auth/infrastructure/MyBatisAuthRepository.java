package com.example.lowcode.auth.infrastructure;

import com.example.lowcode.auth.application.AuthRepository;
import com.example.lowcode.auth.domain.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisAuthRepository implements AuthRepository {
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final TenantMemberMapper tenantMemberMapper;

    public MyBatisAuthRepository(
        UserMapper userMapper,
        TenantMapper tenantMapper,
        TenantMemberMapper tenantMemberMapper
    ) {
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.tenantMemberMapper = tenantMemberMapper;
    }

    @Override
    public Optional<UserIdentity> findByPhone(String phone) {
        User user = userMapper.findByPhone(phone);
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        Long tenantId = tenantMemberMapper.findPreferredTenantId(user.getId());
        String tenantStatus = tenantId == null ? null : tenantMapper.findStatusById(tenantId);
        return Optional.of(new UserIdentity(user.getId(), tenantId, user.getPhone(), user.getStatus(), tenantStatus));
    }

    @Override
    public UserIdentity createUserWithDefaultTenant(String phone) {
        int inserted = userMapper.insertOrAcquire(phone);
        User user = userMapper.findByPhoneForUpdate(phone);
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("Acquired user could not be loaded");
        }

        Long tenantId = tenantMemberMapper.findPreferredTenantIdForUpdate(user.getId());
        if (inserted != 1 || tenantId != null) {
            String tenantStatus = tenantId == null ? null : tenantMapper.findStatusByIdForUpdate(tenantId);
            return new UserIdentity(user.getId(), tenantId, user.getPhone(), user.getStatus(), tenantStatus);
        }

        tenantMapper.insert(phone + " 的团队");
        long createdTenantId = tenantMapper.lastInsertedId();
        tenantMemberMapper.insert(createdTenantId, user.getId(), "OWNER");
        return new UserIdentity(user.getId(), createdTenantId, user.getPhone(), user.getStatus(), "ACTIVE");
    }
}
