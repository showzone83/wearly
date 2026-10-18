package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/**
 * 회원의 로그인 이력 한 줄 (유스케이스 출력). type은 LOGIN | LOGOUT | REFRESH_REUSED | FORCED_LOGOUT | LOGIN_FAILED |
 * MFA_SUCCESS | MFA_FAILED | MFA_LOCKED
 */
public record LoginEventDto(
    String type, String sessionId, String client, String ip, String userAgent, Instant createdAt) {}
