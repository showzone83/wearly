package app.wearly.admin;

import app.wearly.admin.AdminDtos.AuditItem;
import app.wearly.admin.AdminDtos.Dashboard;
import app.wearly.admin.AdminDtos.PageResponse;
import app.wearly.admin.AdminDtos.UserItem;
import app.wearly.auth.CurrentUser;
import app.wearly.common.Ids;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 관리자 대시보드·회원·변경 이력 (ADMIN 전용) */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminReportService reports;
    private final AdminUserService users;

    public AdminController(AdminReportService reports, AdminUserService users) {
        this.reports = reports;
        this.users = users;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return reports.dashboard();
    }

    @GetMapping("/users")
    public PageResponse<UserItem> users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return users.list(q, status, role, Paging.of(page, size));
    }

    @PatchMapping("/users/{id}")
    public UserItem patchUser(@CurrentUser long adminId, @PathVariable String id, @RequestBody JsonNode body) {
        return users.patch(adminId, Ids.parse(id), body);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditItem> auditLogs(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return reports.auditLogs(targetType, Paging.of(page, size));
    }
}
