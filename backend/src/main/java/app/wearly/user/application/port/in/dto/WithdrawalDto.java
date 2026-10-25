package app.wearly.user.application.port.in.dto;

import java.time.Instant;

/**
 * 열린 탈퇴 (탈퇴 예정, doc/27).
 *
 * @param purgeAfter 이 시각이 지나면 회원을 지운다
 */
public record WithdrawalDto(long userId, String reason, Instant requestedAt, Instant purgeAfter) {}
