package app.wearly.auth;

import app.wearly.config.WearlyProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Access 토큰(JWT HS256) 발급. sub = userId, iss = wearly, role = USER | ADMIN */
@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final WearlyProperties.Jwt props;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, WearlyProperties properties, Clock clock) {
        this.encoder = encoder;
        this.props = properties.jwt();
        this.clock = clock;
    }

    public static final String ROLE_CLAIM = "role";

    /** role 클레임은 화면 표시용이다. 서버 권한 판단은 매 요청 DB의 역할로 한다 (강등·정지가 바로 반영되게). */
    public String issue(long userId, String role) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(Long.toString(userId))
                .claim(ROLE_CLAIM, role)
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTtl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTtlSeconds() {
        return props.accessTtl().toSeconds();
    }
}
