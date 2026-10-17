package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.response.AdminUserResponse;
import app.wearly.admin.adapter.in.web.response.AuditLogResponse;
import app.wearly.admin.adapter.in.web.response.DashboardResponse;
import app.wearly.admin.adapter.in.web.response.PageResponse;
import app.wearly.admin.application.port.in.AdminDashboardUseCase;
import app.wearly.admin.application.port.in.AdminUserUseCase;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.in.dto.PatchUserDto;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.JsonPatches;
import app.wearly.common.web.PathIds;
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

  private final AdminDashboardUseCase logs;
  private final AdminUserUseCase users;

  public AdminController(AdminDashboardUseCase logs, AdminUserUseCase users) {
    this.logs = logs;
    this.users = users;
  }

  @GetMapping("/dashboard")
  public DashboardResponse dashboard() {
    return DashboardResponse.from(logs.dashboard());
  }

  @GetMapping("/users")
  public PageResponse<AdminUserResponse> users(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        users.list(q, status, role, PagingDto.of(page, size)), AdminUserResponse::from);
  }

  @PatchMapping("/users/{id}")
  public AdminUserResponse patchUser(
      @CurrentUser long adminId,
      @PathVariable String id,
      @RequestBody(required = false) JsonNode body) {
    PatchUserDto patch =
        JsonPatches.isObject(body)
            ? new PatchUserDto(JsonPatches.string(body, "status"), JsonPatches.string(body, "role"))
            : null;
    return AdminUserResponse.from(users.patch(adminId, PathIds.parse(id), patch));
  }

  @GetMapping("/audit-logs")
  public PageResponse<AuditLogResponse> auditLogs(
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        logs.auditLogs(targetType, PagingDto.of(page, size)), AuditLogResponse::from);
  }
}
