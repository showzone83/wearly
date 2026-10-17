package app.wearly.admin.adapter.in.web;

import app.wearly.admin.application.port.in.AdminDashboardUseCase;
import app.wearly.admin.application.port.in.AdminUserUseCase;
import app.wearly.admin.application.port.in.dto.AdminViews.AdminUserView;
import app.wearly.admin.application.port.in.dto.AdminViews.AuditView;
import app.wearly.admin.application.port.in.dto.AdminViews.DashboardView;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.JsonPatchBody;
import app.wearly.common.web.PathIds;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 관리자 대시보드·회원·변경 이력 API (07 문서 4장). ADMIN 전용 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

  private final AdminDashboardUseCase logs;
  private final AdminUserUseCase users;
  private final JsonMapper jsonMapper;

  public AdminController(
      AdminDashboardUseCase logs, AdminUserUseCase users, JsonMapper jsonMapper) {
    this.logs = logs;
    this.users = users;
    this.jsonMapper = jsonMapper;
  }

  @GetMapping("/dashboard")
  public DashboardView dashboard() {
    return logs.dashboard();
  }

  @GetMapping("/users")
  public PageDto<AdminUserView> users(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return users.list(q, status, role, PageDto.paging(page, size));
  }

  @PatchMapping("/users/{id}")
  public AdminUserView patchUser(
      @CurrentUser long adminId,
      @PathVariable String id,
      @RequestBody(required = false) JsonNode body) {
    return users.patch(adminId, PathIds.parse(id), new JsonPatchBody(body, jsonMapper));
  }

  @GetMapping("/audit-logs")
  public PageDto<AuditView> auditLogs(
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return logs.auditLogs(targetType, PageDto.paging(page, size));
  }
}
