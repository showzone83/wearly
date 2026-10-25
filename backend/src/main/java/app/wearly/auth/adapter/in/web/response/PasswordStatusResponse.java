package app.wearly.auth.adapter.in.web.response;

import app.wearly.auth.application.port.in.dto.PasswordStatusDto;
import app.wearly.common.web.Times;

/**
 * GET /users/me/password · POST /users/me/password/postpone (28 문서). 시각은 ISO-8601 UTC 문자열.
 *
 * @param changedAt 비밀번호를 마지막으로 정한 시각. 비밀번호가 없는 계정이면 null
 * @param dueAt 바꿔야 하는 시각. 기능이 꺼졌거나 비밀번호가 없으면 null
 * @param due 기한이 지났는지 (앱이 "바꾸기 / 연장" 창을 띄운다)
 * @param intervalDays 변경 주기 일수 (연장하면 지금부터 그만큼). 기능이 꺼졌으면 0
 */
public record PasswordStatusResponse(
    String changedAt, String dueAt, boolean due, long intervalDays) {

  public static PasswordStatusResponse from(PasswordStatusDto d) {
    return new PasswordStatusResponse(
        Times.iso(d.changedAt()), Times.iso(d.dueAt()), d.due(), d.intervalDays());
  }
}
