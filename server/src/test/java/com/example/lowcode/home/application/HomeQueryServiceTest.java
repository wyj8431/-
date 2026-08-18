package com.example.lowcode.home.application;

import com.example.lowcode.template.application.TemplatePage;
import com.example.lowcode.template.application.TemplateQueryService;
import com.example.lowcode.template.application.TemplateRepository;
import com.example.lowcode.template.application.TemplateSearchCriteria;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HomeQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");

    @Test
    void loadsPublicTagsFeaturedTemplatesAndOnlyActiveNonemptyEditorialTopics() {
        HomeQueryService service = new HomeQueryService(
            new FakeHomeRepository(),
            new TemplateQueryService(new FakeTemplateRepository()),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        HomeQueryService.HomeView home = service.loadHome();

        assertThat(home.trendingTags()).extracting(HomeQueryService.TagView::code)
            .containsExactly("promotion");
        assertThat(home.featuredTemplates()).extracting(TemplateQueryService.TemplateSummary::id)
            .containsExactly(1001L);
        assertThat(home.hotspotCalendar()).extracting(HomeQueryService.HotspotView::code)
            .containsExactly("empty-calendar");
        assertThat(home.hotspotCalendar()).extracting(HomeQueryService.HotspotView::templateCount)
            .containsExactly(0L);
        assertThat(home.editorialScenes()).extracting(HomeQueryService.TopicView::code)
            .containsExactly("summer-promotion");
    }

    private static final class FakeHomeRepository implements HomeRepository {
        @Override
        public List<HomeTopic> findPublishedTopics() {
            return List.of(
                new HomeTopic(1L, "empty-calendar", "营销日历", null, "HOTSPOT_CALENDAR", null, null, null, List.of()),
                new HomeTopic(2L, "summer-promotion", "夏日促销", "夏季营销模板精选", "EDITORIAL_SCENE", null, null, null, List.of(1001L)),
                new HomeTopic(3L, "empty-editorial", "空专题", null, "EDITORIAL_SCENE", null, null, null, List.of()),
                new HomeTopic(4L, "future", "未来专题", null, "EDITORIAL_SCENE", null, NOW.plusSeconds(1), null, List.of(1001L)),
                new HomeTopic(5L, "expired", "过期专题", null, "EDITORIAL_SCENE", null, null, NOW, List.of(1001L))
            );
        }
    }

    private static final class FakeTemplateRepository implements TemplateRepository {
        private final TemplateQueryService.TemplateSummary summary = new TemplateQueryService.TemplateSummary(
            1001L, "朋友圈促销", 1080, 1440, null, null, "marketing", List.of("promotion"), NOW
        );

        @Override
        public TemplatePage<TemplateQueryService.TemplateSummary> searchPublished(TemplateSearchCriteria criteria) {
            return new TemplatePage<>(List.of(summary), criteria.page(), criteria.pageSize(), 1L);
        }

        @Override
        public List<TemplateQueryService.TemplateSummary> findPublishedByIds(List<Long> templateIds) {
            return templateIds.contains(summary.id()) ? List.of(summary) : List.of();
        }

        @Override
        public List<TemplateQueryService.TemplateCategoryView> findPublishedCategories() {
            return List.of();
        }

        @Override
        public List<TemplateQueryService.TemplateTagView> findPublishedTags() {
            return List.of(new TemplateQueryService.TemplateTagView("promotion", "促销"));
        }

        @Override
        public boolean hasPublishedCategory(String code) {
            return false;
        }

        @Override
        public boolean hasPublishedTag(String code) {
            return false;
        }

        @Override
        public Optional<TemplateQueryService.TemplateDetail> findPublishedById(long templateId) {
            return Optional.empty();
        }
    }
}
