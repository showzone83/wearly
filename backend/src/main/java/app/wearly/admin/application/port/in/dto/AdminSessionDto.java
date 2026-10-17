package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/**
 * 관리자 로그인 사용자 목록의 한 줄 (유스케이스 출력, 16 문서 4장). email·name·role은 DB의 회원 정보(탈퇴했으면 null). current = 요청한
 * 관리자 자신의 세션
 */
public record AdminSessionDto(
    String sessionId,
    long userId,
    String email,
    String name,
    String role,
    String client,
    String userAgent,
    String ip,
    Instant createdAt,
    Instant lastRefreshAt,
    Instant lastSeenAt,
    boolean current) {}
