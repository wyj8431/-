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
        return userIdentity(user, false);
    }

    @Override
    public Optional<UserIdentity> findByWechatOpenId(String openId) {
        return userIdentity(userMapper.findByWechatOpenId(openId), false);
    }

    @Override
    public Optional<String> findWechatOpenIdByUserId(long userId) {
        return Optional.ofNullable(userMapper.findWechatOpenIdByUserId(userId));
    }

    @Override
    public boolean bindWechatOpenId(long userId, String openId) {
        return userMapper.bindWechatOpenId(userId, openId) == 1;
    }

    private Optional<UserIdentity> userIdentity(User user, boolean forUpdate) {
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        Long tenantId = forUpdate
            ? tenantMemberMapper.findPreferredTenantIdForUpdate(user.getId())
            : tenantMemberMapper.findPreferredTenantId(user.getId());
        String tenantStatus = tenantId == null ? null : forUpdate
            ? tenantMapper.findStatusByIdForUpdate(tenantId)
            : tenantMapper.findStatusById(tenantId);
        String tenantRole = tenantId == null ? null : forUpdate
            ? tenantMemberMapper.findPreferredTenantRoleForUpdate(user.getId())
            : tenantMemberMapper.findPreferredTenantRole(user.getId());
        return Optional.of(new UserIdentity(
            user.getId(), tenantId, user.getPhone(), user.getStatus(), tenantStatus, tenantRole,
            user.getSecurityVersion()
        ));
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
            String tenantRole = tenantId == null ? null : tenantMemberMapper.findPreferredTenantRoleForUpdate(user.getId());
            return new UserIdentity(
                user.getId(), tenantId, user.getPhone(), user.getStatus(), tenantStatus, tenantRole,
                user.getSecurityVersion()
            );
        }

        tenantMapper.insert(phone + " 的团队");
        long createdTenantId = tenantMapper.lastInsertedId();
        tenantMemberMapper.insert(createdTenantId, user.getId(), "ADMIN");
        return new UserIdentity(
            user.getId(), createdTenantId, user.getPhone(), user.getStatus(), "ACTIVE", "ADMIN",
            user.getSecurityVersion()
        );
    }

    @Override
    public Optional<UserIdentity> findByUserAndTenant(long userId, long tenantId) {
        UserMapper.UserIdentityRow row = userMapper.findByUserAndTenant(userId, tenantId);
        if (row == null || row.getId() == null || row.getTenantId() == null) {
            return Optional.empty();
        }
        return Optional.of(new UserIdentity(
            row.getId(), row.getTenantId(), row.getPhone(), row.getStatus(),
            row.getTenantStatus(), row.getTenantRole(), row.getSecurityVersion()
        ));
    }
}
