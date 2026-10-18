package app.wearly.auth.application.port.in;

import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.LoginResultDto;
import app.wearly.auth.application.port.in.dto.SsoCallbackDto;
import app.wearly.auth.application.port.in.dto.SsoProviderDto;
import app.wearly.auth.application.port.in.dto.SsoRedirectDto;
import java.util.List;

/**
 * 관리자 사이트 SSO: 범용 OIDC Authorization Code + PKCE, 서버가 주도한다 (22 문서 4.2).
 *
 * <ol>
 *   <li>{@link #start}: state·nonce·PKCE verifier를 저장하고 IdP authorize 주소로 보낸다
 *   <li>{@link #callback}: 코드를 토큰으로 바꾸고 ID 토큰을 확인한 뒤 계정 연결·관리자 그룹 확인 → 관리자 사이트로 한 번만 쓰는 교환 코드를 붙여
 *       보낸다 (토큰은 URL에 싣지 않는다)
 *   <li>{@link #exchange}: 교환 코드 → 우리 Access·Refresh 토큰 (또는 앱 TOTP 대기)
 * </ol>
 *
 * SSO가 꺼져 있거나 없는 공급자면 시작·콜백·교환·로그아웃은 404 NOT_FOUND다.
 */
public interface SsoUseCase {

  /** 로그인 화면 버튼용 공급자 (꺼져 있으면 빈 목록) */
  List<SsoProviderDto> providers();

  /**
   * 로그인 시작. redirect(관리자 사이트 콜백 주소)가 허용 목록에 없으면 400 VALIDATION_FAILED(redirect). IdP에 닿지 못하면
   * redirect로 오류(SSO_PROVIDER_UNAVAILABLE)를 붙여 보낸다.
   *
   * @param callbackUri 요청 주소로 만든 백엔드 콜백 주소 (설정 callback-uri가 있으면 그 값을 쓴다)
   */
  SsoRedirectDto start(String providerId, String redirect, String callbackUri);

  /** IdP에서 돌아옴. 결과(교환 코드 또는 오류)를 관리자 사이트 주소에 붙여 보낸다 */
  SsoRedirectDto callback(SsoCallbackDto callback);

  /** 교환 코드 → 토큰. 코드가 없거나 만료·사용됐으면 401 SSO_EXCHANGE_INVALID */
  LoginResultDto exchange(String code, ClientDto client);

  /**
   * IdP 로그아웃 주소 (end_session_endpoint + client_id + post_logout_redirect_uri). IdP가 지원하지 않거나 닿지 못하면
   * redirect 그대로. redirect가 허용 목록에 없으면 400 VALIDATION_FAILED(redirect).
   */
  String logoutLocation(String providerId, String redirect);
}
