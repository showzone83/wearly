package app.wearly.auth.domain;

import java.time.Instant;

/**
 * 콜백에서 확인을 마치고 관리자 사이트에 준 한 번만 쓰는 교환 코드의 내용 (Valkey {@code wearly:sso:grant:{codeHash}}, 1분). 토큰은
 * URL에 싣지 않고, 관리자 사이트가 이 코드를 POST /auth/sso/exchange로 바꿔 받는다.
 *
 * @param amr IdP가 알려 준 인증 방법 (쉼표로 이은 값, 없으면 null)
 */
public record SsoGrant(long userId, String providerId, String amr, Instant createdAt) {}
