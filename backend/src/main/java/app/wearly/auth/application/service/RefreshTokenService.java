package app.wearly.auth.application.service;

import app.wearly.auth.application.port.out.RefreshTokenPort;
import app.wearly.auth.domain.RefreshToken;
import app.wearly.auth.domain.RefreshTokens;
import app.wearly.auth.domain.TokenPolicy;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh 토큰 발급·회전·폐기. 회전 규칙(재사용 감지)은 도메인 RefreshToken.check가 정한다.
 * 이미 회전된 토큰이 다시 오면 같은 family 전체를 폐기한다.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenPort tokens;
    private final TokenPolicy policy;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenPort tokens, TokenPolicy policy, Clock clock) {
        this.tokens = tokens;
        this.policy = policy;
        this.clock = clock;
    }

    public record Issued(String rawToken, RefreshToken token) {
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
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(ErrorCode.REFRESH_INVALID);
        }
        Instant now = clock.instant();
        RefreshToken current = tokens.findByHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_INVALID));
        switch (current.check(now)) {
            case REUSED -> {
                tokens.revokeFamily(current.familyId(), now);
                throw new ApiException(ErrorCode.REFRESH_REUSED);
            }
            case INVALID -> throw new ApiException(ErrorCode.REFRESH_INVALID);
            case OK -> {
                // 회전한다
            }
        }
        Issued next = issue(current.userId(), current.familyId());
        current.rotateTo(next.token().id(), now);
        tokens.save(current);
        return next;
    }

    /** 로그아웃: 있으면 폐기, 없어도 조용히 끝낸다. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        tokens.findByHashForUpdate(hash(rawToken)).ifPresent(t -> {
            t.revoke(clock.instant());
            tokens.save(t);
        });
    }

    /** 회원 정지: 그 회원의 살아 있는 Refresh 토큰을 모두 폐기한다 */
    @Transactional
    public void revokeAllForUser(long userId) {
        tokens.revokeAllForUser(userId, clock.instant());
    }

    public long refreshTtlSeconds() {
        return policy.refreshTtl().toSeconds();
    }

    private Issued issue(long userId, UUID familyId) {
        String raw = RefreshTokens.newRawToken();
        RefreshToken saved = tokens.save(RefreshToken.issue(userId, hash(raw), familyId,
                clock.instant().plus(policy.refreshTtl())));
        return new Issued(raw, saved);
    }

    /** DB에 두는 토큰 해시 (SHA-256 hex) */
    public static String hash(String raw) {
        return RefreshTokens.hash(raw);
    }
}
