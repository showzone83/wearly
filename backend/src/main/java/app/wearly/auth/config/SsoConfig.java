package app.wearly.auth.config;

import app.wearly.auth.adapter.out.oidc.HttpOidcAdapter;
import app.wearly.auth.application.port.out.OidcPort;
import app.wearly.auth.domain.SsoProvider;
import app.wearly.auth.domain.SsoSettings;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 관리자 사이트 SSO 설정 (22 문서 4.2). 설정이 모자란 공급자는 서버를 멈추지 않고 빼고 ERROR 로그를 남긴다 — SSO는 선택 기능이고, 로컬 비상
 * 관리자(비밀번호 + TOTP)는 그대로 들어올 수 있어야 한다.
 */
@Configuration
public class SsoConfig {

  private static final Logger log = LoggerFactory.getLogger(SsoConfig.class);

  @Bean
  SsoSettings ssoSettings(SsoProperties properties) {
    if (!properties.on()) {
      log.info("관리자 SSO: 꺼짐 (wearly.sso.enabled=false)");
      return new SsoSettings(false, properties.trustMfa(), List.of());
    }
    List<SsoProvider> providers = new ArrayList<>();
    for (Map.Entry<String, SsoProperties.Provider> e : properties.providerMap().entrySet()) {
      SsoProperties.Provider p = e.getValue();
      if (p == null || Boolean.FALSE.equals(p.enabled())) {
        continue;
      }
      SsoProvider provider = toProvider(e.getKey(), p);
      List<String> problems = provider.problems();
      if (!problems.isEmpty()) {
        log.error(
            "관리자 SSO: 공급자 {} 설정이 모자라 뺍니다: {} (wearly.sso.providers.{}.*)",
            e.getKey(),
            String.join(", ", problems),
            e.getKey());
        continue;
      }
      providers.add(provider);
      log.info(
          "관리자 SSO 공급자 {}: issuer={} roles-claim={} admin-groups={} admin-emails={}개",
          provider.id(),
          provider.issuer(),
          provider.rolesClaim(),
          provider.adminGroups(),
          provider.adminEmails().size());
    }
    if (providers.isEmpty()) {
      log.warn("관리자 SSO: 켜져 있지만 쓸 수 있는 공급자가 없어요 (wearly.sso.providers)");
    } else {
      log.info("관리자 SSO: 켜짐, 공급자 {}개, IdP MFA 믿기={}", providers.size(), properties.trustMfa());
    }
    return new SsoSettings(true, properties.trustMfa(), providers);
  }

  @Bean
  OidcPort oidcPort(SsoProperties properties, Clock clock) {
    List<HttpOidcAdapter.Rewrite> rewrites =
        properties.rewrites().stream()
            .filter(r -> r != null && r.from() != null && !r.from().isBlank() && r.to() != null)
            .map(r -> new HttpOidcAdapter.Rewrite(r.from().strip(), r.to().strip()))
            .toList();
    return new HttpOidcAdapter(properties.timeout(), rewrites, clock);
  }

  static SsoProvider toProvider(String id, SsoProperties.Provider p) {
    return new SsoProvider(
        id,
        p.displayName(),
        p.issuerUri(),
        p.discoveryUri(),
        p.clientId(),
        p.clientSecret(),
        p.scopes(),
        p.rolesClaim(),
        p.adminGroups(),
        p.adminEmails(),
        p.redirectAllowlist(),
        p.callbackUri(),
        Boolean.TRUE.equals(p.assumeEmailVerified()));
  }
}
