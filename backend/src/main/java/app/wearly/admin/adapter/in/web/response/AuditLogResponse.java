package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AuditLogDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import java.util.Map;

/** GET /admin/audit-logs 항목 (07 문서 4장). before·after는 저장된 JSON 그대로 */
public record AuditLogResponse(
    String id,
    String adminUserId,
    String adminEmail,
    String action,
    String targetType,
    String targetId,
    Map<String, Object> before,
    Map<String, Object> after,
    String createdAt) {

  public static AuditLogResponse from(AuditLogDto d) {
    return new AuditLogResponse(
        Ids.format(d.id()),
        Ids.format(d.adminUserId()),
        d.adminEmail(),
        d.action(),
        d.targetType(),
        d.targetId(),
        d.before(),
        d.after(),
        Times.iso(d.createdAt()));
  }
}
