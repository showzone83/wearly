package app.wearly.auth.adapter.in.web;

import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * exp·nbf 검사. 기본 JwtTimestampValidator 대신 쓴다. - 시계 오차 허용 없음: 짧은 TTL(하네스 PT3S)에서도 정확히 만료된다. - 만료는 고유
 * 오류 코드로 표시해 401 TOKEN_EXPIRED로 구분한다.
 */
public class ExpiryValidator implements OAuth2TokenValidator<Jwt> {

  public static final String EXPIRED_ERROR = "wearly_token_expired";

  private final Clock clock;

  public ExpiryValidator(Clock clock) {
    this.clock = clock;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    Instant now = clock.instant();
    Instant exp = jwt.getExpiresAt();
    if (exp == null) {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "exp가 없어요.", null));
    }
    if (!now.isBefore(exp)) {
      return OAuth2TokenValidatorResult.failure(new OAuth2Error(EXPIRED_ERROR, "토큰이 만료됐어요.", null));
    }
    Instant nbf = jwt.getNotBefore();
    if (nbf != null && now.isBefore(nbf)) {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "아직 쓸 수 없는 토큰이에요.", null));
    }
    return OAuth2TokenValidatorResult.success();
  }
}
