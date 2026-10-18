package app.wearly.auth.adapter.in.web;

import app.wearly.auth.application.port.in.AuthenticationUseCase;
import app.wearly.auth.application.port.in.dto.AuthenticatedDto;
import app.wearly.auth.domain.AccessTokenClaims;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 서명·만료가 맞는 토큰이라도 로그인 세션(sid)이 끊겼거나(로그아웃·강제 로그아웃·정지·탈퇴) sid가 없는 옛 토큰이거나 회원이 활성 상태가 아니면 거부한다 → 401
 * TOKEN_INVALID (16 문서 3장). 인증 주체 이름은 userId 문자열이다. 권한(ROLE_USER·ROLE_ADMIN)은 토큰 클레임이 아니라 DB의 현재
 * 역할로 준다. 관리자인데 이 세션이 2단계 인증을 거치지 않았으면(22 문서) ROLE_ADMIN 대신 ROLE_USER와 {@link #MFA_REQUIRED}를 준다 →
 * 관리자 API 403 MFA_REQUIRED, 앱 API는 그대로 쓴다.
 */
public class UserIdAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  public static final String USER_GONE = "로그인 세션이 끝났거나 쓸 수 없는 회원이에요.";

  /** 2단계 인증을 거치지 않은 관리자 세션 표시 (권한 이름) */
  public static final String MFA_REQUIRED = "MFA_REQUIRED";

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
    AuthenticatedDto auth =
        authentication
            .authenticate(userId, jwt.getClaimAsString(AccessTokenClaims.SESSION_ID))
            .orElseThrow(() -> new InvalidBearerTokenException(USER_GONE));
    List<GrantedAuthority> authorities =
        auth.mfaRequired()
            ? List.of(
                new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority(MFA_REQUIRED))
            : List.of(new SimpleGrantedAuthority("ROLE_" + auth.role()));
    return new JwtAuthenticationToken(jwt, authorities, Long.toString(userId));
  }
}
