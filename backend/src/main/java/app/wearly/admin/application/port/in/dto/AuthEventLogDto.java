package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/**
 * 관리자 로그인 기록 한 줄 (22 문서). type은 LOGIN · LOGOUT · REFRESH_REUSED · FORCED_LOGOUT · LOGIN_FAILED ·
 * MFA_SUCCESS · MFA_FAILED · MFA_LOCKED
 */
public record AuthEventLogDto(
    long id,
    long userId,
    String email,
    String name,
    String role,
    String type,
    String sessionId,
    String client,
    String ip,
    String userAgent,
    Instant createdAt) {}
