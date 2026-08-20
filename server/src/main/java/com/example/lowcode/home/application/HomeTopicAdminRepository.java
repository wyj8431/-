package com.example.lowcode.home.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HomeTopicAdminRepository {
    List<TopicRecord> findAll(String status);

    Optional<TopicRecord> findById(long id);

    Optional<TopicRecord> findByCode(String code);

    long insert(NewTopic topic);

    boolean update(long id, NewTopic topic);

    boolean updateStatus(long id, String status);

    boolean delete(long id);

    void replaceTemplateRelations(long topicId, List<Long> templateIds);

    List<Long> findExistingTemplateIds(List<Long> templateIds);

    record NewTopic(
        String code,
        String title,
        String subtitle,
        String type,
        Long coverAssetId,
        Instant startsAt,
        Instant endsAt,
        int sortOrder,
        String status
    ) {
    }

    record TopicRecord(
        long id,
        String code,
        String title,
        String subtitle,
        String type,
        Long coverAssetId,
        Instant startsAt,
        Instant endsAt,
        int sortOrder,
        String status,
        List<Long> templateIds
    ) {
        public TopicRecord {
            templateIds = List.copyOf(templateIds);
        }
    }
}
