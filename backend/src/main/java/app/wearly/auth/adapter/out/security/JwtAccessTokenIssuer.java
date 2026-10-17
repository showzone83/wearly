package app.wearly.auth.adapter.out.security;

import app.wearly.auth.application.port.out.AccessTokenIssuer;
import app.wearly.common.config.WearlyProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Access 토큰(JWT HS256) 발급. sub = userId, iss = wearly, role = USER | ADMIN */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final WearlyProperties.Jwt props;
    private final Clock clock;

    public JwtAccessTokenIssuer(JwtEncoder encoder, WearlyProperties properties, Clock clock) {
        this.encoder = encoder;
        this.props = properties.jwt();
        this.clock = clock;
    }

    @Override
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

    @Override
    public long accessTtlSeconds() {
        return props.accessTtl().toSeconds();
    }
}
