package app.wearly.auth.adapter.out.security;

import app.wearly.auth.application.port.out.AccessTokenIssuer;
import app.wearly.auth.domain.AccessTokenClaims;
import app.wearly.common.config.WearlyProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Access 토큰(JWT HS256) 발급. sub = userId, iss = wearly, role = USER | ADMIN, sid = 로그인 세션 ID.
 *
 * <p>JWT의 iat·exp는 초 단위라서 밀리초를 버리면 실제 수명이 설정보다 최대 1초 짧아진다(PT1S면 바로 만료될 수 있었다). exp는 올림해서 수명이 설정값
 * 이상이 되게 한다.
 */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

  private final JwtEncoder encoder;
  private final WearlyProperties.Jwt props;
  private final Clock clock;

  public JwtAccessTokenIssuer(JwtEncoder encoder, WearlyProperties properties, Clock clock) {
    this.encoder = encoder;
    this.props = properties.jwt();
    this.clock = clock;
  }

  @Override
  public String issue(long userId, String role, String sessionId) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(props.issuer())
            .subject(Long.toString(userId))
            .claim(AccessTokenClaims.ROLE, role)
            .claim(AccessTokenClaims.SESSION_ID, sessionId)
            .issuedAt(now.truncatedTo(ChronoUnit.SECONDS))
            .expiresAt(ceilToSecond(now.plus(props.accessTtl())))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  static Instant ceilToSecond(Instant t) {
    Instant floor = t.truncatedTo(ChronoUnit.SECONDS);
    return floor.equals(t) ? t : floor.plusSeconds(1);
  }

  @Override
  public long accessTtlSeconds() {
    return props.accessTtl().toSeconds();
  }
}
