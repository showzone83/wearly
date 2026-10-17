package app.wearly.auth.application.port.in.dto;

import java.time.Instant;

/**
 * 로그인 세션 하나 (유스케이스 출력, 16 문서 3장). client는 APP | ADMIN | WEB, userAgent·ip·lastRefreshAt은 없을 수 있다
 */
public record SessionDto(
    String sessionId,
    long userId,
    String client,
    String userAgent,
    String ip,
    Instant createdAt,
    Instant lastRefreshAt,
    Instant lastSeenAt) {}
