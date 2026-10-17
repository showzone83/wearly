package app.wearly.auth;

import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.config.WearlyProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh 토큰: 32바이트 난수 base64url (JWT 아님). DB에는 SHA-256 hex만 저장한다.
 * 갱신할 때마다 회전하고, 이미 회전된 토큰이 다시 오면 같은 family 전체를 폐기한다.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final WearlyProperties.Jwt props;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, WearlyProperties properties, Clock clock) {
        this.tokens = tokens;
        this.props = properties.jwt();
        this.clock = clock;
    }

    public record Issued(String rawToken, RefreshToken entity) {
    }

    /** 로그인·가입: 새 family로 발급 */
    @Transactional(propagation = Propagation.MANDATORY)
    public Issued issueNewFamily(long userId) {
        return issue(userId, UUID.randomUUID());
    }

    /**
     * 회전. 실패하면 ApiException을 던지지만 재사용 감지로 family를 폐기한 결과는 커밋해야 하므로
     * ApiException에서는 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Issued rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = tokens.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_INVALID));
        if (current.isRevoked()) {
            if (current.isRotated()) {
                tokens.revokeFamily(current.getFamilyId(), now);
                throw new ApiException(ErrorCode.REFRESH_REUSED);
            }
            throw new ApiException(ErrorCode.REFRESH_INVALID);
        }
        if (current.isExpired(now)) {
            throw new ApiException(ErrorCode.REFRESH_INVALID);
        }
        Issued next = issue(current.getUserId(), current.getFamilyId());
        current.rotateTo(next.entity(), now);
        return next;
    }

    /** 로그아웃: 있으면 폐기, 없어도 조용히 끝낸다. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        tokens.findByTokenHashForUpdate(hash(rawToken)).ifPresent(t -> t.revoke(clock.instant()));
    }

    public long refreshTtlSeconds() {
        return props.refreshTtl().toSeconds();
    }

    private Issued issue(long userId, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken entity = new RefreshToken(userId, hash(raw), familyId, clock.instant().plus(props.refreshTtl()));
        tokens.saveAndFlush(entity);
        return new Issued(raw, entity);
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
