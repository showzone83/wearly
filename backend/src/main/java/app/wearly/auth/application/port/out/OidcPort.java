package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.IdTokenClaims;
import app.wearly.auth.domain.OidcMetadata;
import app.wearly.auth.domain.SsoProvider;
import java.time.Instant;

/**
 * OIDC IdP와 서버끼리 주고받기 (22 문서 4.2). 닿지 못하면 OidcUnavailableException, 응답을 믿을 수 없으면
 * OidcRejectedException. discovery 문서·JWKS는 어댑터가 캐시한다.
 */
public interface OidcPort {

  /** discovery 문서. issuer가 설정과 다르면 OidcUnavailableException (설정 오류) */
  OidcMetadata metadata(SsoProvider provider);

  /** 인가 코드 → ID 토큰 원문 (token 엔드포인트, PKCE verifier·redirect_uri 함께) */
  String exchangeCode(
      SsoProvider provider,
      OidcMetadata metadata,
      String code,
      String callbackUri,
      String verifier);

  /** ID 토큰 서명(JWKS)·iss·aud(azp)·exp·iat·nonce·sub를 확인하고 쓸 값을 꺼낸다 */
  IdTokenClaims verify(
      SsoProvider provider, OidcMetadata metadata, String idToken, String nonce, Instant now);
}
