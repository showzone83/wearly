package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/**
 * 관리자 로그인 기록 한 줄 (22 문서). type은 LOGIN · LOGOUT · REFRESH_REUSED · FORCED_LOGOUT · LOGIN_FAILED ·
 * MFA_SUCCESS · MFA_FAILED · MFA_LOCKED · SSO_LOGIN · SSO_DENIED. ssoProvider·ssoAmr은 SSO 로그인·거부에만
 * 있다
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
    Instant createdAt,
    String ssoProvider,
    String ssoAmr) {}
