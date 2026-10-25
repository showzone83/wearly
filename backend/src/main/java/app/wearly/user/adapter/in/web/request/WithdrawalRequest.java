package app.wearly.user.adapter.in.web.request;

import app.wearly.user.application.port.in.dto.WithdrawDto;

/**
 * POST /users/me/withdrawal 본문. 검증은 도메인이 한다 (doc/27).
 *
 * @param reason 사유 (필수): NOT_USEFUL · BAD_RECOMMEND · HARD_TO_USE · PRIVACY · OTHER_APP · OTHER
 * @param reasonText 하고 싶은 말 (선택, 200자)
 */
public record WithdrawalRequest(String reason, String reasonText) {

  public WithdrawDto toDto() {
    return new WithdrawDto(reason, reasonText);
  }
}
