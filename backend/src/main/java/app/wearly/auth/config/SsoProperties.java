package app.wearly.auth.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * wearly.sso.* (22 문서 4.2). 관리자 사이트 회사 계정 로그인(범용 OIDC). 비밀(client-secret)은
 * config/application-local.yml이나 환경변수(WEARLY_SSO_PROVIDERS_&lt;ID&gt;_CLIENTSECRET)에 둔다.
 *
 * @param enabled 기본 false. 꺼져 있으면 공급자 목록이 비고 시작·콜백·교환은 404
 * @param trustIdpMfa 기본 true. SSO 세션은 IdP가 MFA를 맡은 것으로 보고 앱 TOTP를 묻지 않는다. false면 비밀번호 로그인처럼 앱 TOTP
 * @param httpTimeout IdP 호출 제한 시간 (기본 10초)
 * @param backchannelRewrites 서버끼리 부르는 IdP 주소 바꾸기 (docker compose: localhost:8180 → keycloak:8080)
 * @param providers 공급자 ID(영문 소문자·숫자·하이픈) → 설정
 */
@ConfigurationProperties("wearly.sso")
public record SsoProperties(
    Boolean enabled,
    Boolean trustIdpMfa,
    Duration httpTimeout,
    List<Rewrite> backchannelRewrites,
    Map<String, Provider> providers) {

  public boolean on() {
    return Boolean.TRUE.equals(enabled);
  }

  public boolean trustMfa() {
    return trustIdpMfa == null || trustIdpMfa;
  }

  public Duration timeout() {
    return httpTimeout == null ? Duration.ofSeconds(10) : httpTimeout;
  }

  public List<Rewrite> rewrites() {
    return backchannelRewrites == null ? List.of() : backchannelRewrites;
  }

  public Map<String, Provider> providerMap() {
    return providers == null ? Map.of() : providers;
  }

  /** 서버끼리 부를 때 from으로 시작하는 주소를 to로 바꾼다 */
  public record Rewrite(String from, String to) {}

  /**
   * 공급자 하나. 각 값의 뜻은 domain.SsoProvider.
   *
   * @param enabled 기본 true. false면 이 공급자만 끈다
   */
  public record Provider(
      Boolean enabled,
      String displayName,
      String issuerUri,
      String discoveryUri,
      String clientId,
      String clientSecret,
      List<String> scopes,
      String rolesClaim,
      List<String> adminGroups,
      List<String> adminEmails,
      List<String> redirectAllowlist,
      String callbackUri,
      Boolean assumeEmailVerified) {

    @Override
    public String toString() {
      return "Provider[issuerUri=" + issuerUri + ", clientId=" + clientId + ", clientSecret=***]";
    }
  }
}
