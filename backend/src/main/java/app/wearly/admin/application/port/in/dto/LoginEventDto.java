package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/**
 * 회원의 로그인 이력 한 줄 (유스케이스 출력). type은 LOGIN | LOGOUT | REFRESH_REUSED | FORCED_LOGOUT | LOGIN_FAILED |
 * MFA_SUCCESS | MFA_FAILED | MFA_LOCKED | SSO_LOGIN | SSO_DENIED. ssoProvider·ssoAmr은 SSO 로그인·거부에만
 * 있다
 */
public record LoginEventDto(
    String type,
    String sessionId,
    String client,
    String ip,
    String userAgent,
    Instant createdAt,
    String ssoProvider,
    String ssoAmr) {}
