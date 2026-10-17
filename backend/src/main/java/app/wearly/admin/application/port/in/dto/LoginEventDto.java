package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/** 회원의 로그인 이력 한 줄 (유스케이스 출력). type은 LOGIN | LOGOUT | REFRESH_REUSED | FORCED_LOGOUT */
public record LoginEventDto(
    String type, String sessionId, String client, String ip, String userAgent, Instant createdAt) {}
