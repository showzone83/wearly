package app.wearly.admin.application.port.out.dto;

/** 사유별 탈퇴 신청 수 (doc/27) */
public record WithdrawalReasonCountRowDto(String reason, long count) {}
