package app.wearly.auth.domain;

import java.util.List;

/**
 * OIDC discovery 문서에서 쓰는 값 (.well-known/openid-configuration).
 *
 * @param endSessionEndpoint RP-Initiated Logout 주소. 없는 IdP(Google)는 null
 * @param tokenAuthMethods token_endpoint_auth_methods_supported (없으면 빈 목록 = client_secret_basic)
 */
public record OidcMetadata(
    String issuer,
    String authorizationEndpoint,
    String tokenEndpoint,
    String jwksUri,
    String endSessionEndpoint,
    List<String> tokenAuthMethods) {

  public OidcMetadata {
    tokenAuthMethods = tokenAuthMethods == null ? List.of() : List.copyOf(tokenAuthMethods);
  }

  /** 클라이언트 비밀을 Basic 헤더로 보낼지 (OIDC 기본). IdP가 client_secret_post만 받으면 본문으로 */
  public boolean useBasicAuth() {
    return tokenAuthMethods.isEmpty()
        || tokenAuthMethods.contains("client_secret_basic")
        || !tokenAuthMethods.contains("client_secret_post");
  }
}
