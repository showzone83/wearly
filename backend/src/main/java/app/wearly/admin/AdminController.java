package app.wearly.admin;

import app.wearly.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 관리자 대시보드·회원·변경 이력 API (07 문서 4장). ADMIN 전용 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminLogService logs;
    private final AdminUserService users;

    public AdminController(AdminLogService logs, AdminUserService users) {
        this.logs = logs;
        this.users = users;
    }

    @GetMapping("/dashboard")
    public AdminLogService.DashboardDto dashboard() {
        return logs.dashboard();
    }

    @GetMapping("/users")
    public PageResponse<AdminUserService.AdminUserDto> users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return users.list(q, status, role, PageResponse.paging(page, size));
    }

    @PatchMapping("/users/{id}")
    public AdminUserService.AdminUserDto patchUser(@CurrentUser long adminId, @PathVariable String id,
            @RequestBody(required = false) JsonNode body) {
        return users.patch(adminId, id, body);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AdminLogService.AuditDto> auditLogs(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return logs.auditLogs(targetType, PageResponse.paging(page, size));
    }
}
