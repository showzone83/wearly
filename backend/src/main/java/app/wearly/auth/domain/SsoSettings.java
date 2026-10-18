package app.wearly.auth.domain;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 관리자 사이트 SSO 설정 (22 문서 4.2, wearly.sso.*).
 *
 * @param enabled false(기본)면 공급자 목록이 비고 시작·콜백·교환은 404
 * @param trustIdpMfa true(기본)면 SSO로 만든 세션을 2단계 인증을 거친 세션으로 본다(IdP가 MFA를 맡음). false면 비밀번호 로그인처럼 앱
 *     TOTP를 거친다
 * @param providers 쓸 수 있는 공급자 (설정 순서). 설정이 모자란 공급자는 시작 때 빼고 로그를 남긴다
 */
public record SsoSettings(boolean enabled, boolean trustIdpMfa, List<SsoProvider> providers) {

  /** 시작 요청에서 콜백까지 기다리는 시간 (state·nonce·PKCE verifier 보관) */
  public static final Duration STATE_TTL = Duration.ofMinutes(5);

  /** 콜백에서 관리자 사이트가 교환하기까지 기다리는 시간 (한 번만 쓰는 교환 코드) */
  public static final Duration EXCHANGE_TTL = Duration.ofMinutes(1);

  public SsoSettings {
    providers = providers == null ? List.of() : List.copyOf(providers);
  }

  public static SsoSettings disabled() {
    return new SsoSettings(false, true, List.of());
  }

  /** 켜져 있을 때 그 공급자 */
  public Optional<SsoProvider> find(String id) {
    if (!enabled || id == null) {
      return Optional.empty();
    }
    return providers.stream().filter(p -> p.id().equals(id)).findFirst();
  }

  /** 로그인 화면에 보일 공급자 (꺼져 있으면 빈 목록) */
  public List<SsoProvider> visible() {
    return enabled ? providers : List.of();
  }
}
