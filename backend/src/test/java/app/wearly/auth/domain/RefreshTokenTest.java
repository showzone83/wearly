package app.wearly.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Refresh 토큰 회전 규칙: 회전·만료·폐기·재사용 감지 (05 문서 5장, Spring 없이) */
class RefreshTokenTest {

    static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

    RefreshToken token(Instant expiresAt) {
        return new RefreshToken(1L, 7L, "h", UUID.randomUUID(), expiresAt, null, null);
    }

    @Test
    void 살아_있는_토큰은_회전할_수_있고_회전하면_재사용으로_본다() {
        RefreshToken t = token(NOW.plusSeconds(60));
        assertThat(t.check(NOW)).isEqualTo(RefreshToken.Check.OK);
        t.rotateTo(2L, NOW);
        assertThat(t.isRevoked()).isTrue();
        assertThat(t.replacedById()).isEqualTo(2L);
        assertThat(t.check(NOW)).isEqualTo(RefreshToken.Check.REUSED);
    }

    @Test
    void 만료되거나_로그아웃으로_폐기된_토큰은_INVALID() {
        assertThat(token(NOW).check(NOW)).isEqualTo(RefreshToken.Check.INVALID);
        RefreshToken t = token(NOW.plusSeconds(60));
        t.revoke(NOW);
        t.revoke(NOW.plusSeconds(5)); // 처음 폐기 시각을 지킨다
        assertThat(t.revokedAt()).isEqualTo(NOW);
        assertThat(t.check(NOW)).isEqualTo(RefreshToken.Check.INVALID);
    }

    @Test
    void 원문은_32바이트_base64url이고_DB에는_SHA256_hex만() {
        String raw = RefreshTokens.newRawToken();
        assertThat(raw).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(RefreshTokens.newRawToken()).isNotEqualTo(raw);
        assertThat(RefreshTokens.hash(raw)).hasSize(64).matches("[0-9a-f]+").isEqualTo(RefreshTokens.hash(raw));
        assertThat(RefreshTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        RefreshToken issued = RefreshToken.issue(7L, "h", UUID.randomUUID(), NOW.plusSeconds(1));
        assertThat(issued.id()).isNull();
        assertThat(issued.check(NOW)).isEqualTo(RefreshToken.Check.OK);
    }

    @Test
    void 이메일은_소문자로_정규화() {
        assertThat(LocalCredential.normalizeEmail("  Kim@Wearly.TEST ")).isEqualTo("kim@wearly.test");
    }
}
