package app.wearly.admin.application.port.out.dto;

import java.time.Instant;

/**
 * 탈퇴 기록 한 행 (tb_user_withdrawal, doc/27). email·name은 회원이 아직 있을 때만(지운 뒤에는 null). canceledBy = USER
 * · ADMIN, purgedBy = SCHEDULER · ADMIN
 */
public record AdminWithdrawalRowDto(
    Long id,
    Long userId,
    String email,
    String name,
    String reason,
    String reasonText,
    Instant requestedAt,
    Instant purgeAfter,
    Instant canceledAt,
    String canceledBy,
    Instant purgedAt,
    String purgedBy) {}
