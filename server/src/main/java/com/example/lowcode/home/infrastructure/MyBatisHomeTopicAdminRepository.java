package com.example.lowcode.home.infrastructure;

import com.example.lowcode.home.application.HomeTopicAdminRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisHomeTopicAdminRepository implements HomeTopicAdminRepository {
    private final HomeTopicAdminMapper mapper;

    public MyBatisHomeTopicAdminRepository(HomeTopicAdminMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TopicRecord> findAll(String status) {
        return mapper.findAll(status).stream().map(this::toRecord).toList();
    }

    @Override
    public Optional<TopicRecord> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::toRecord);
    }

    @Override
    public Optional<TopicRecord> findByCode(String code) {
        return Optional.ofNullable(mapper.findByCode(code)).map(this::toRecord);
    }

    @Override
    public long insert(NewTopic topic) {
        HomeTopicAdminMapper.TopicRow row = toRow(topic);
        if (mapper.insert(row) != 1 || row.getId() == null) throw new IllegalStateException("首页专题创建失败");
        return row.getId();
    }

    @Override
    public boolean update(long id, NewTopic topic) {
        HomeTopicAdminMapper.TopicRow row = toRow(topic);
        row.setId(id);
        return mapper.update(row) == 1;
    }

    @Override
    public boolean updateStatus(long id, String status) { return mapper.updateStatus(id, status) == 1; }

    @Override
    public boolean delete(long id) { return mapper.delete(id) == 1; }

    @Override
    @Transactional
    public void replaceTemplateRelations(long topicId, List<Long> templateIds) {
        mapper.deleteTemplateRelations(topicId);
        for (int i = 0; i < templateIds.size(); i++) mapper.insertTemplateRelation(topicId, templateIds.get(i), (i + 1) * 10);
    }

    @Override
    public List<Long> findExistingTemplateIds(List<Long> templateIds) {
        return templateIds.isEmpty() ? List.of() : mapper.findExistingTemplateIds(templateIds);
    }

    private TopicRecord toRecord(HomeTopicAdminMapper.TopicRow row) {
        return new TopicRecord(row.getId(), row.getCode(), row.getTitle(), row.getSubtitle(), row.getType(), row.getCoverAssetId(),
            toInstant(row.getStartsAt()), toInstant(row.getEndsAt()), row.getSortOrder(), row.getStatus(), mapper.findTemplateIds(row.getId()));
    }

    private HomeTopicAdminMapper.TopicRow toRow(NewTopic topic) {
        HomeTopicAdminMapper.TopicRow row = new HomeTopicAdminMapper.TopicRow();
        row.setCode(topic.code()); row.setTitle(topic.title()); row.setSubtitle(topic.subtitle()); row.setType(topic.type());
        row.setCoverAssetId(topic.coverAssetId()); row.setStartsAt(topic.startsAt() == null ? null : Timestamp.from(topic.startsAt()));
        row.setEndsAt(topic.endsAt() == null ? null : Timestamp.from(topic.endsAt())); row.setSortOrder(topic.sortOrder()); row.setStatus(topic.status());
        return row;
    }

    private Instant toInstant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
