package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.request.ChangePasswordRequest;
import app.wearly.admin.adapter.in.web.request.MfaCodeRequest;
import app.wearly.admin.adapter.in.web.response.AdminMfaSetupResponse;
import app.wearly.admin.adapter.in.web.response.AdminMfaStatusResponse;
import app.wearly.admin.adapter.in.web.response.RecoveryCodesResponse;
import app.wearly.admin.application.port.in.AdminMeUseCase;
import app.wearly.common.web.CurrentSession;
import app.wearly.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 관리자 자신의 계정 API (22 문서 4.1): 비밀번호 변경, 2단계 인증. ADMIN 전용 */
@RestController
@RequestMapping("/api/v1/admin/me")
public class AdminMeController {

  private static final MfaCodeRequest EMPTY = new MfaCodeRequest(null, null, null);

  private final AdminMeUseCase me;

  public AdminMeController(AdminMeUseCase me) {
    this.me = me;
  }

  /** 204. 다른 기기의 세션은 모두 끊기고 지금 세션은 남는다 */
  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @CurrentUser long adminId,
      @CurrentSession String sessionId,
      @RequestBody(required = false) ChangePasswordRequest request) {
    ChangePasswordRequest r = request == null ? new ChangePasswordRequest(null, null) : request;
    me.changePassword(adminId, sessionId, r.currentPassword(), r.newPassword());
  }

  @GetMapping("/mfa")
  public AdminMfaStatusResponse mfaStatus(@CurrentUser long adminId) {
    return AdminMfaStatusResponse.from(me.mfaStatus(adminId));
  }

  @PostMapping("/mfa/setup")
  public AdminMfaSetupResponse mfaSetup(@CurrentUser long adminId) {
    return AdminMfaSetupResponse.from(me.startMfaSetup(adminId));
  }

  @PostMapping("/mfa/enable")
  public RecoveryCodesResponse mfaEnable(
      @CurrentUser long adminId,
      @CurrentSession String sessionId,
      @RequestBody(required = false) MfaCodeRequest request) {
    MfaCodeRequest r = request == null ? EMPTY : request;
    return new RecoveryCodesResponse(me.enableMfa(adminId, sessionId, r.code()));
  }

  /** 204. 내 TOTP·복구 코드를 지운다. 이어서 setup → enable로 다시 등록한다 */
  @PostMapping("/mfa/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void mfaReset(
      @CurrentUser long adminId,
      @CurrentSession String sessionId,
      @RequestBody(required = false) MfaCodeRequest request) {
    MfaCodeRequest r = request == null ? EMPTY : request;
    me.resetMfa(adminId, sessionId, r.currentPassword(), r.code(), r.recoveryCode());
  }
}
