package app.wearly.auth.application.port.in;

import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.MfaSetupDto;
import app.wearly.auth.application.port.in.dto.MfaStatusDto;
import app.wearly.auth.application.port.in.dto.MfaVerifyDto;
import app.wearly.auth.application.port.in.dto.MfaVerifyResultDto;
import java.util.List;

/**
 * 관리자 2단계 인증 TOTP (22 문서 4.1). 로그인 중(MFA 대기 토큰)의 등록·확인과, 로그인한 관리자의 상태·등록·초기화. 코드를 5번 틀리면 15분
 * 잠긴다(429 MFA_LOCKED, 대기 토큰 무효).
 */
public interface MfaUseCase {

  /** 대기 토큰으로 등록 시작: 새 비밀을 만든다 (다시 부르면 앞의 비밀은 무효) */
  MfaSetupDto setup(String mfaToken);

  /** 대기 토큰 + TOTP 코드(또는 복구 코드) → 정식 토큰. 등록 전이면 이 확인으로 등록을 마치고 복구 코드 10개를 준다 */
  MfaVerifyResultDto verify(MfaVerifyDto command, ClientDto client);

  MfaStatusDto status(long userId);

  /** 로그인한 관리자가 스스로 등록을 시작한다 (이미 등록했으면 409 MFA_ALREADY_ENROLLED) */
  MfaSetupDto startSetup(long userId);

  /**
   * 로그인한 관리자가 코드를 확인해 등록을 마친다. 지금 세션은 2단계 인증을 확인한 세션이 된다.
   *
   * @return 복구 코드 10개 (한 번만 보여 준다)
   */
  List<String> enable(long userId, String sessionId, String code);

  /**
   * 자기 2단계 인증 초기화: 현재 비밀번호 + 코드(또는 복구 코드)를 확인하고 등록·복구 코드를 지운다. 지금 세션만 남기고 다른 세션은 끊는다.
   *
   * @return 끊은 세션 수
   */
  int reset(
      long userId, String sessionId, String currentPassword, String code, String recoveryCode);

  /** 다른 관리자가 초기화: 등록·복구 코드를 지운다 (세션은 부르는 쪽이 끊는다). @return 지우기 전 상태 */
  MfaStatusDto remove(long userId);
}
