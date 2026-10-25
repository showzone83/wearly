package app.wearly.user.adapter.in.web.response;

import app.wearly.common.web.Times;
import app.wearly.user.application.port.in.dto.WithdrawalDto;

/**
 * 탈퇴 신청 응답 (doc/27). 시각은 ISO-8601 UTC 문자열.
 *
 * @param purgeAt 이 시각이 지나면 회원과 데이터를 모두 지운다. 그 전에 다시 로그인하면 취소할 수 있다
 */
public record WithdrawalResponse(String requestedAt, String purgeAt) {

  public static WithdrawalResponse from(WithdrawalDto d) {
    return new WithdrawalResponse(Times.iso(d.requestedAt()), Times.iso(d.purgeAfter()));
  }
}
