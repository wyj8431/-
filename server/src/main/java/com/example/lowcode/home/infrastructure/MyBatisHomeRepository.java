package com.example.lowcode.home.infrastructure;

import com.example.lowcode.home.application.HomeRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisHomeRepository implements HomeRepository {
    private final HomeTopicMapper homeTopicMapper;

    public MyBatisHomeRepository(HomeTopicMapper homeTopicMapper) {
        this.homeTopicMapper = homeTopicMapper;
    }

    @Override
    public List<HomeTopic> findPublishedTopics() {
        return homeTopicMapper.findPublishedTopics().stream()
            .map(row -> new HomeTopic(
                row.getId(),
                row.getCode(),
                row.getTitle(),
                row.getSubtitle(),
                row.getType(),
                row.getCoverAssetId(),
                row.getStartsAt(),
                row.getEndsAt(),
                homeTopicMapper.findTemplateIds(row.getId())
            ))
            .toList();
    }
}
