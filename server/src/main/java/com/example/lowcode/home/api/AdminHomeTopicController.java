package com.example.lowcode.home.api;

import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import com.example.lowcode.home.application.HomeTopicAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/home-topics")
public class AdminHomeTopicController {
    private final HomeTopicAdminService service;

    public AdminHomeTopicController(HomeTopicAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<HomeTopicAdminService.TopicView>> list(
        @RequestParam(required = false) String status,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.list(CurrentUser.fromJwt(jwt), status), trace(request));
    }

    @PostMapping
    public ApiResponse<HomeTopicAdminService.TopicView> create(
        @RequestBody TopicRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.create(CurrentUser.fromJwt(jwt), body.toCommand()), trace(request));
    }

    @PatchMapping("/{topicId}")
    public ApiResponse<HomeTopicAdminService.TopicView> update(
        @PathVariable long topicId,
        @RequestBody TopicRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.update(CurrentUser.fromJwt(jwt), topicId, body.toCommand()), trace(request));
    }

    @PatchMapping("/{topicId}/status")
    public ApiResponse<HomeTopicAdminService.TopicView> changeStatus(
        @PathVariable long topicId,
        @RequestBody ChangeStatusRequest body,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.changeStatus(CurrentUser.fromJwt(jwt), topicId, body.status()), trace(request));
    }

    @DeleteMapping("/{topicId}")
    public ApiResponse<Void> delete(
        @PathVariable long topicId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        service.delete(CurrentUser.fromJwt(jwt), topicId);
        return ApiResponse.success(null, trace(request));
    }

    private String trace(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
    }

    public record ChangeStatusRequest(String status) {}

    public record TopicRequest(
        String code,
        String title,
        String subtitle,
        String type,
        Long coverAssetId,
        java.time.Instant startsAt,
        java.time.Instant endsAt,
        int sortOrder,
        List<Long> templateIds
    ) {
        HomeTopicAdminService.TopicCommand toCommand() {
            return new HomeTopicAdminService.TopicCommand(code, title, subtitle, type, coverAssetId, startsAt, endsAt, sortOrder, templateIds);
        }
    }
}
