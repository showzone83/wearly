package app.wearly.admin.adapter.in.web.response;

import app.wearly.auth.application.port.in.dto.MfaStatusDto;
import app.wearly.common.web.Times;

/**
 * GET /admin/me/mfa (22 문서): 내 2단계 인증 상태. enrolledAt은 등록하지 않았으면 null, required는 서버 설정
 * wearly.admin.mfa.required
 */
public record AdminMfaStatusResponse(
    boolean enrolled, String enrolledAt, int recoveryCodesLeft, boolean required) {

  public static AdminMfaStatusResponse from(MfaStatusDto d) {
    return new AdminMfaStatusResponse(
        d.enrolled(), Times.iso(d.enrolledAt()), d.recoveryCodesLeft(), d.required());
  }
}
