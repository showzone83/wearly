package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/**
 * 관리자 탈퇴 기록 한 줄 (doc/27 4단계). state = OPEN(탈퇴 예정) · CANCELED · PURGED. overdue = 탈퇴 예정인데 삭제 예정 시각이
 * 지났다(예약 삭제가 밀림). email·name은 회원이 지워졌으면 null
 */
public record AdminWithdrawalDto(
    Long id,
    Long userId,
    String email,
    String name,
    String reason,
    String reasonText,
    String state,
    boolean overdue,
    Instant requestedAt,
    Instant purgeAt,
    Instant canceledAt,
    String canceledBy,
    Instant purgedAt,
    String purgedBy) {}
