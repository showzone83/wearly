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

/** Access 토큰(JWT HS256) 발급. sub = userId, iss = wearly */
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

    public String issue(long userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(Long.toString(userId))
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
