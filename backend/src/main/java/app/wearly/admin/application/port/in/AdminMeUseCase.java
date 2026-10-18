package app.wearly.admin.application.port.in;

import app.wearly.auth.application.port.in.dto.MfaSetupDto;
import app.wearly.auth.application.port.in.dto.MfaStatusDto;
import java.util.List;

/**
 * 로그인한 관리자 자신의 계정 (22 문서 4.1): 비밀번호 변경, 2단계 인증 상태·등록·초기화. 바꾸면 변경 이력(ADMIN_PASSWORD_CHANGE ·
 * ADMIN_MFA_ENROLL · ADMIN_MFA_RESET)을 남긴다.
 */
public interface AdminMeUseCase {

  /**
   * 현재 비밀번호 확인(403 REAUTH_FAILED) 뒤 새 비밀번호(가입 규칙과 같음, 400 VALIDATION_FAILED)로 바꾼다. 지금 세션만 남기고 다른
   * 세션은 모두 끊는다
   */
  void changePassword(long adminId, String sessionId, String currentPassword, String newPassword);

  MfaStatusDto mfaStatus(long adminId);

  MfaSetupDto startMfaSetup(long adminId);

  /**
   * @return 복구 코드 10개 (한 번만 보여 준다)
   */
  List<String> enableMfa(long adminId, String sessionId, String code);

  /** 현재 비밀번호 + 코드(또는 복구 코드)로 자기 2단계 인증을 지운다. 다른 세션은 끊고 지금 세션은 남긴다 */
  void resetMfa(
      long adminId, String sessionId, String currentPassword, String code, String recoveryCode);
}
