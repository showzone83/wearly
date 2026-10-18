package app.wearly.auth.domain;

import java.time.Instant;

/**
 * 비밀번호는 맞았고 2단계 인증을 기다리는 관리자 로그인 (Valkey {@code wearly:mfa:pending:{tokenHash}}, 5분). 토큰 원문은 응답으로만
 * 주고 저장소에는 SHA-256만 둔다. 관리자 API에는 쓸 수 없다 (JWT가 아니다).
 */
public record PendingMfa(long userId, ClientInfo client, Instant createdAt) {}
