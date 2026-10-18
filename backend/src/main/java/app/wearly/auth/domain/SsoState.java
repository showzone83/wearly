package app.wearly.auth.domain;

import java.time.Instant;

/**
 * SSO 시작에서 콜백까지 들고 있는 값 (Valkey {@code wearly:sso:state:{stateHash}}, 5분, 한 번만 꺼낸다). state 원문은 IdP로
 * 가는 주소와 브라우저 쿠키에만 있고 저장소에는 SHA-256만 둔다.
 *
 * @param nonce ID 토큰 nonce와 맞춰 본다 (토큰 재사용 막기)
 * @param codeVerifier PKCE verifier (토큰 교환 때 보낸다)
 * @param redirect 정규화한 관리자 사이트 콜백 주소 (허용 목록에 있던 값)
 * @param callbackUri IdP authorize에 보낸 redirect_uri (토큰 교환에 같은 값을 보낸다)
 */
public record SsoState(
    String providerId,
    String nonce,
    String codeVerifier,
    String redirect,
    String callbackUri,
    Instant createdAt) {}
