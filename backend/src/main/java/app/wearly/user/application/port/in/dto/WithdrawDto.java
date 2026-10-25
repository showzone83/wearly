package app.wearly.user.application.port.in.dto;

/**
 * 탈퇴 신청 입력 (doc/27).
 *
 * @param reason 사유 (필수): NOT_USEFUL · BAD_RECOMMEND · HARD_TO_USE · PRIVACY · OTHER_APP · OTHER
 * @param reasonText 하고 싶은 말 (선택, 200자)
 */
public record WithdrawDto(String reason, String reasonText) {}
