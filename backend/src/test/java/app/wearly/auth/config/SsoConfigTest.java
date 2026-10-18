package app.wearly.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.auth.domain.SsoSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** wearly.sso.* 묶기 (yml 키·환경변수)와 설정이 모자란 공급자 빼기 (22 문서 4.2) */
class SsoConfigTest {

  static SsoProperties bind(Map<String, Object> yml, Map<String, Object> env) {
    StandardEnvironment e = new StandardEnvironment();
    e.getPropertySources().addLast(new MapPropertySource("yml", yml));
    e.getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env));
    return new Binder(ConfigurationPropertySources.get(e))
        .bind("wearly.sso", SsoProperties.class)
        .get();
  }

  @Test
  void 환경변수로_비밀과_켜기를_줄_수_있다() {
    SsoProperties p =
        bind(
            Map.of(
                "wearly.sso.providers.keycloak.issuer-uri", "http://localhost:8180/realms/wearly",
                "wearly.sso.providers.keycloak.client-id", "wearly-admin",
                "wearly.sso.providers.keycloak.admin-groups[0]", "wearly-admins",
                "wearly.sso.providers.keycloak.redirect-allowlist[0]",
                    "http://localhost:5173/sso/callback"),
            Map.of(
                "WEARLY_SSO_ENABLED", "true",
                "WEARLY_SSO_PROVIDERS_KEYCLOAK_CLIENTSECRET", "from-env"));
    assertThat(p.on()).isTrue();
    assertThat(p.trustMfa()).isTrue();
    assertThat(p.providerMap().get("keycloak").clientSecret()).isEqualTo("from-env");
    SsoSettings s = new SsoConfig().ssoSettings(p);
    assertThat(s.enabled()).isTrue();
    assertThat(s.providers()).hasSize(1);
    assertThat(s.find("keycloak")).isPresent();
  }

  @Test
  void 꺼져_있거나_설정이_모자란_공급자는_쓰지_않는다() {
    SsoProperties off = bind(Map.of("wearly.sso.providers.keycloak.client-id", "x"), Map.of());
    assertThat(new SsoConfig().ssoSettings(off).visible()).isEmpty();

    SsoProperties broken =
        bind(
            Map.of(
                "wearly.sso.enabled", "true",
                "wearly.sso.providers.keycloak.issuer-uri", "http://localhost:8180/realms/wearly",
                "wearly.sso.providers.keycloak.client-id", "wearly-admin"),
            Map.of());
    SsoSettings s = new SsoConfig().ssoSettings(broken);
    assertThat(s.enabled()).isTrue();
    assertThat(s.providers()).isEmpty();
  }
}
