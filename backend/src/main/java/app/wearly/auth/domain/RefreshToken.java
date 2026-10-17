package app.wearly.auth.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh 토큰 (순수 도메인 모델). 원문은 저장하지 않고 SHA-256 hex만 둔다.
 * 회전할 때마다 같은 family 안에서 새 토큰이 생기고, 이미 회전된 토큰이 다시 오면 재사용으로 본다.
 */
public class RefreshToken {

    /** 이 토큰으로 갱신해도 되는지 */
    public enum Check {
        /** 회전해도 된다 */
        OK,
        /** 폐기됐거나(로그아웃·정지) 만료됨 → 401 REFRESH_INVALID */
        INVALID,
        /** 이미 회전된 토큰의 재사용 → family 전체 폐기, 401 REFRESH_REUSED */
        REUSED
    }

    private final Long id;
    private final long userId;
    private final String tokenHash;
    private final UUID familyId;
    private final Instant expiresAt;
    private Instant revokedAt;
    private Long replacedById;

    public RefreshToken(Long id, long userId, String tokenHash, UUID familyId, Instant expiresAt, Instant revokedAt,
            Long replacedById) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.replacedById = replacedById;
    }

    /** 아직 저장하지 않은 새 토큰 */
    public static RefreshToken issue(long userId, String tokenHash, UUID familyId, Instant expiresAt) {
        return new RefreshToken(null, userId, tokenHash, familyId, expiresAt, null, null);
    }

    public Check check(Instant now) {
        if (isRevoked()) {
            return isRotated() ? Check.REUSED : Check.INVALID;
        }
        return isExpired(now) ? Check.INVALID : Check.OK;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** 회전으로 폐기된 토큰인지 (재사용 감지 기준) */
    public boolean isRotated() {
        return replacedById != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** 이미 폐기됐으면 처음 폐기 시각을 그대로 둔다 */
    public void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }

    public void rotateTo(long nextId, Instant at) {
        revoke(at);
        replacedById = nextId;
    }

    public Long id() {
        return id;
    }

    public long userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public UUID familyId() {
        return familyId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Long replacedById() {
        return replacedById;
    }
}
