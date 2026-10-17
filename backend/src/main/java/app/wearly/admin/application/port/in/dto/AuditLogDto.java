package app.wearly.admin.application.port.in.dto;

import java.time.Instant;
import java.util.Map;

/** 변경 이력 한 줄 (유스케이스 출력). before·after는 저장된 JSON 객체 그대로 */
public record AuditLogDto(
    Long id,
    Long adminUserId,
    String adminEmail,
    String action,
    String targetType,
    String targetId,
    Map<String, Object> before,
    Map<String, Object> after,
    Instant createdAt) {}
