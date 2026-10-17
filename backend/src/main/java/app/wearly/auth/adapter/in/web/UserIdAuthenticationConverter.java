package app.wearly.auth.adapter.in.web;

import app.wearly.auth.application.port.in.AuthenticationUseCase;
import app.wearly.auth.domain.AccessTokenClaims;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 서명·만료가 맞는 토큰이라도 로그인 세션(sid)이 끊겼거나(로그아웃·강제 로그아웃·정지·탈퇴) sid가 없는 옛 토큰이거나 회원이 활성 상태가 아니면 거부한다 → 401
 * TOKEN_INVALID (16 문서 3장). 인증 주체 이름은 userId 문자열이다. 권한(ROLE_USER·ROLE_ADMIN)은 토큰 클레임이 아니라 DB의 현재
 * 역할로 준다.
 */
public class UserIdAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  public static final String USER_GONE = "로그인 세션이 끝났거나 쓸 수 없는 회원이에요.";

  private final AuthenticationUseCase authentication;

  public UserIdAuthenticationConverter(AuthenticationUseCase authentication) {
    this.authentication = authentication;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    long userId;
    try {
      userId = Long.parseLong(jwt.getSubject());
    } catch (NumberFormatException e) {
      throw new InvalidBearerTokenException("sub가 올바르지 않아요.");
    }
    String role =
        authentication
            .authenticate(userId, jwt.getClaimAsString(AccessTokenClaims.SESSION_ID))
            .orElseThrow(() -> new InvalidBearerTokenException(USER_GONE));
    return new JwtAuthenticationToken(
        jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)), Long.toString(userId));
  }
}
