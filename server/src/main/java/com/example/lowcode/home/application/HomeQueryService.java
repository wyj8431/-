package com.example.lowcode.home.application;

import com.example.lowcode.template.application.TemplatePage;
import com.example.lowcode.template.application.TemplateQueryService;
import com.example.lowcode.template.application.TemplateSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class HomeQueryService {
    private static final int FEATURED_PAGE_SIZE = 6;

    private final HomeRepository homeRepository;
    private final TemplateQueryService templateQueryService;
    private final Clock clock;

    @Autowired
    public HomeQueryService(HomeRepository homeRepository, TemplateQueryService templateQueryService) {
        this(homeRepository, templateQueryService, Clock.systemUTC());
    }

    public HomeQueryService(HomeRepository homeRepository, TemplateQueryService templateQueryService, Clock clock) {
        this.homeRepository = homeRepository;
        this.templateQueryService = templateQueryService;
        this.clock = clock;
    }

    public HomeView loadHome() {
        List<TagView> trendingTags = templateQueryService.listPublishedTags().stream()
            .map(tag -> new TagView(tag.code(), tag.name()))
            .toList();
        TemplatePage<TemplateQueryService.TemplateSummary> featuredPage = templateQueryService.searchPublished(
            new TemplateSearchCriteria(null, null, null, 1, FEATURED_PAGE_SIZE)
        );
        Instant now = clock.instant();
        List<HomeRepository.HomeTopic> topics = homeRepository.findPublishedTopics();
        List<HotspotView> hotspotCalendar = topics.stream()
            .filter(topic -> isActive(topic, now))
            .filter(topic -> "HOTSPOT_CALENDAR".equals(topic.type()))
            .map(topic -> new HotspotView(
                topic.code(),
                topic.title(),
                topic.startsAt(),
                visibleTemplates(topic.templateIds()).size()
            ))
            .toList();
        List<TopicView> editorialScenes = topics.stream()
            .filter(topic -> isActive(topic, now))
            .filter(topic -> "EDITORIAL_SCENE".equals(topic.type()))
            .map(topic -> new TopicWithTemplates(topic, visibleTemplates(topic.templateIds())))
            .filter(topic -> !topic.templates().isEmpty())
            .map(topic -> new TopicView(
                topic.topic().code(),
                topic.topic().title(),
                topic.topic().subtitle(),
                topic.topic().coverAssetId(),
                coverUrl(topic.topic().coverAssetId())
            ))
            .toList();
        return new HomeView(trendingTags, featuredPage.items(), hotspotCalendar, editorialScenes);
    }

    private List<TemplateQueryService.TemplateSummary> visibleTemplates(List<Long> templateIds) {
        return templateQueryService.findPublishedByIds(templateIds);
    }

    private boolean isActive(HomeRepository.HomeTopic topic, Instant now) {
        return (topic.startsAt() == null || !topic.startsAt().isAfter(now))
            && (topic.endsAt() == null || topic.endsAt().isAfter(now));
    }

    private String coverUrl(Long coverAssetId) {
        return coverAssetId == null
            ? null
            : "/api/v1/template-cover-assets/" + coverAssetId + "/content";
    }

    public record HomeView(
        List<TagView> trendingTags,
        List<TemplateQueryService.TemplateSummary> featuredTemplates,
        List<HotspotView> hotspotCalendar,
        List<TopicView> editorialScenes
    ) {
    }

    public record TagView(String code, String name) {
    }

    public record HotspotView(String code, String title, Instant startsAt, long templateCount) {
    }

    public record TopicView(String code, String title, String subtitle, Long coverAssetId, String coverUrl) {
    }

    private record TopicWithTemplates(
        HomeRepository.HomeTopic topic,
        List<TemplateQueryService.TemplateSummary> templates
    ) {
    }
}
