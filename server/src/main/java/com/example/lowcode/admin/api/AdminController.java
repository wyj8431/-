package com.example.lowcode.admin.api;

import com.example.lowcode.admin.application.AdminService;
import com.example.lowcode.auth.security.CurrentUser;
import com.example.lowcode.audit.application.AuditLogService;
import com.example.lowcode.audit.application.AuditCsvExporter;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService adminService;
    private final AuditCsvExporter auditCsvExporter;

    public AdminController(AdminService adminService, AuditCsvExporter auditCsvExporter) {
        this.adminService = adminService;
        this.auditCsvExporter = auditCsvExporter;
    }

    @GetMapping("/summary")
    public ApiResponse<AdminService.Summary> summary(
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            adminService.summary(CurrentUser.fromJwt(jwt)),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    @GetMapping("/users")
    public ApiResponse<AdminService.MemberPage> members(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) String status,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            adminService.members(
                CurrentUser.fromJwt(jwt),
                new AdminService.MemberQuery(page, pageSize, role, status)
            ),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    @GetMapping("/audit-logs")
    public ApiResponse<AuditLogService.AuditPage> auditLogs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String outcome,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            adminService.auditLogs(
                CurrentUser.fromJwt(jwt),
                new AuditLogService.AuditQuery(
                    page, pageSize, action, outcome, from, to
                )
            ),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    @GetMapping(value = "/audit-logs/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportAuditLogs(
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String outcome,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @AuthenticationPrincipal Jwt jwt
    ) {
        AuditLogService.AuditExport export = adminService.auditLogExport(
            CurrentUser.fromJwt(jwt),
            new AuditLogService.AuditExportQuery(action, outcome, from, to)
        );
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.csv\"")
            .header("X-Audit-Export-Total", Long.toString(export.total()))
            .header("X-Audit-Export-Truncated", Boolean.toString(export.truncated()))
            .body(auditCsvExporter.toCsv(export));
    }

    @PatchMapping("/users/{userId}/tenant-role")
    public ApiResponse<AdminService.RoleChangeResult> changeRole(
        @PathVariable long userId,
        @RequestBody RoleChangeRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
            adminService.changeTenantRole(CurrentUser.fromJwt(jwt), userId, request.tenantRole()),
            (String) servletRequest.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }

    public record RoleChangeRequest(String tenantRole) {
    }
}
