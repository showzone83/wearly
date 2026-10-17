package app.wearly.auth;

import app.wearly.user.UserRepository;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 서명·만료가 맞는 토큰이라도 회원이 없거나(탈퇴) 활성 상태가 아니면 거부한다 → 401 TOKEN_INVALID.
 * 인증 주체 이름은 userId 문자열이다.
 */
public class UserIdAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String USER_GONE = "탈퇴했거나 쓸 수 없는 회원이에요.";

    private final UserRepository users;

    public UserIdAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("sub가 올바르지 않아요.");
        }
        boolean active = users.findById(userId).map(u -> u.isActive()).orElse(false);
        if (!active) {
            throw new InvalidBearerTokenException(USER_GONE);
        }
        return new JwtAuthenticationToken(jwt, List.of(), Long.toString(userId));
    }
}
