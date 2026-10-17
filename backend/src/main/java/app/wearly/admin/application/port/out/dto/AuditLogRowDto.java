package app.wearly.admin.application.port.out.dto;

import java.time.Instant;
import java.util.Map;

/** 변경 이력 한 행 (관리자 이메일 포함). before·after는 저장된 JSON 객체 */
public record AuditLogRowDto(
    Long id,
    Long adminUserId,
    String adminEmail,
    String action,
    String targetType,
    String targetId,
    Map<String, Object> before,
    Map<String, Object> after,
    Instant createdAt) {}
