package app.wearly.admin.application.port.out.dto;

/**
 * 기간 안에 신청한 탈퇴의 상태별 수 (doc/27).
 *
 * @param open 아직 탈퇴 예정 (취소·삭제 안 됨)
 */
public record WithdrawalStateCountRowDto(long total, long open, long canceled, long purged) {}
