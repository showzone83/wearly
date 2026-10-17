package app.wearly.admin.application.port.out.dto;

import java.time.Instant;

/** 로그인 이력(tb_auth_event) 한 행 */
public record AuthEventRowDto(
    Long id,
    String type,
    String sessionId,
    String client,
    String ip,
    String userAgent,
    Instant createdAt) {}
