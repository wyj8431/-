package com.example.lowcode.auth.infrastructure;

import com.example.lowcode.auth.application.WechatOAuthStateRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class MyBatisWechatOAuthStateRepository implements WechatOAuthStateRepository {
    private final WechatOAuthStateMapper mapper;

    public MyBatisWechatOAuthStateRepository(WechatOAuthStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(NewState state) {
        WechatOAuthStateMapper.StateRow row = new WechatOAuthStateMapper.StateRow();
        row.setStateHash(state.stateHash());
        row.setPurpose(state.purpose());
        row.setInitiatorUserId(state.initiatorUserId());
        row.setInitiatorTenantId(state.initiatorTenantId());
        row.setReturnPath(state.returnPath());
        row.setExpiresAt(Timestamp.from(state.expiresAt()));
        if (mapper.insert(row) != 1 || row.getId() == null) {
            throw new IllegalStateException("Could not create WeChat OAuth state");
        }
    }

    @Override
    public Optional<StoredState> findByHashForUpdate(String stateHash) {
        return Optional.ofNullable(mapper.findByHashForUpdate(stateHash)).map(this::toStoredState);
    }

    @Override
    public boolean markConsumed(long id, Instant consumedAt) {
        return mapper.markConsumed(id, Timestamp.from(consumedAt)) == 1;
    }

    private StoredState toStoredState(WechatOAuthStateMapper.StateRow row) {
        return new StoredState(
            row.getId(),
            row.getStateHash(),
            row.getPurpose(),
            row.getInitiatorUserId(),
            row.getInitiatorTenantId(),
            row.getReturnPath(),
            toInstant(row.getExpiresAt()),
            toInstant(row.getConsumedAt())
        );
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
