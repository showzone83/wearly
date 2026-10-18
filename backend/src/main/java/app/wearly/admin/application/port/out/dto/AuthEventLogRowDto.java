package app.wearly.admin.application.port.out.dto;

import java.time.Instant;

/** 관리자 로그인 기록(tb_auth_event + 회원) 한 행 */
public record AuthEventLogRowDto(
    Long id,
    Long userId,
    String email,
    String name,
    String role,
    String type,
    String sessionId,
    String client,
    String ip,
    String userAgent,
    Instant createdAt,
    String ssoProvider,
    String ssoAmr) {}
