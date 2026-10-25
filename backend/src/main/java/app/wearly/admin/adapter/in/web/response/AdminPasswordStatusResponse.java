package app.wearly.admin.adapter.in.web.response;

import app.wearly.auth.application.port.in.dto.PasswordStatusDto;
import app.wearly.common.web.Times;

/**
 * GET /admin/me/password · POST /admin/me/password/postpone (28 문서): 내 비밀번호 변경 주기. 앱의
 * /users/me/password와 같은 모양. changedAt·dueAt은 비밀번호가 없거나 기능이 꺼졌으면 null
 */
public record AdminPasswordStatusResponse(
    String changedAt, String dueAt, boolean due, long intervalDays) {

  public static AdminPasswordStatusResponse from(PasswordStatusDto d) {
    return new AdminPasswordStatusResponse(
        Times.iso(d.changedAt()), Times.iso(d.dueAt()), d.due(), d.intervalDays());
  }
}
