package com.example.lowcode.home.application;

import java.time.Instant;
import java.util.List;

public interface HomeRepository {
    List<HomeTopic> findPublishedTopics();

    record HomeTopic(
        long id,
        String code,
        String title,
        String subtitle,
        String type,
        Long coverAssetId,
        Instant startsAt,
        Instant endsAt,
        List<Long> templateIds
    ) {
        public HomeTopic {
            templateIds = List.copyOf(templateIds);
        }
    }
}
