package app.wearly.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Refresh 토큰 회전 규칙·기기 정보 (05 문서 5장, 16 문서 3장, Spring 없이) */
class RefreshTokenTest {

  static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

  RefreshToken token(RefreshToken.Status status) {
    return new RefreshToken("h", 7L, "sid", status, null, NOW);
  }

  @Test
  void 상태별_갱신_판단() {
    assertThat(RefreshToken.issue("h", 7L, "sid", NOW).check()).isEqualTo(RefreshToken.Check.OK);
    assertThat(token(RefreshToken.Status.ROTATED).check()).isEqualTo(RefreshToken.Check.REUSED);
    assertThat(token(RefreshToken.Status.REVOKED).check()).isEqualTo(RefreshToken.Check.INVALID);
  }

  @Test
  void 원문은_32바이트_base64url이고_저장소에는_SHA256_hex만() {
    String raw = RefreshTokens.newRawToken();
    assertThat(raw).hasSize(43).matches("[A-Za-z0-9_-]+");
    assertThat(RefreshTokens.newRawToken()).isNotEqualTo(raw);
    assertThat(RefreshTokens.hash(raw))
        .hasSize(64)
        .matches("[0-9a-f]+")
        .isEqualTo(RefreshTokens.hash(raw));
    assertThat(RefreshTokens.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void 기기_종류는_헤더가_먼저이고_없으면_User_Agent로_추정한다() {
    assertThat(ClientKind.resolve("admin", null)).isEqualTo(ClientKind.ADMIN);
    assertThat(ClientKind.resolve(" Web ", "okhttp/4.12")).isEqualTo(ClientKind.WEB);
    assertThat(ClientKind.resolve("tv", "Mozilla/5.0 (Macintosh)")).isEqualTo(ClientKind.WEB);
    assertThat(ClientKind.resolve(null, "Wearly/1 CFNetwork/1 Darwin/24"))
        .isEqualTo(ClientKind.APP);
    assertThat(ClientKind.resolve(null, "okhttp/4.12.0")).isEqualTo(ClientKind.APP);
    assertThat(ClientKind.resolve("", null)).isEqualTo(ClientKind.APP);
  }

  @Test
  void 기기_정보는_길이를_자르고_빈_값은_null() {
    ClientInfo info = ClientInfo.of(null, "x".repeat(500), "  ");
    assertThat(info.userAgent()).hasSize(ClientInfo.USER_AGENT_MAX);
    assertThat(info.ip()).isNull();
    assertThat(ClientInfo.of("app", null, " 10.0.0.1 ").ip()).isEqualTo("10.0.0.1");
  }

  @Test
  void 새_세션은_로그인_시각이_마지막_활동이고_갱신_전이다() {
    LoginSession s = LoginSession.start(7L, ClientInfo.of("app", "ua", "1.2.3.4"), NOW);
    assertThat(s.id()).hasSize(36);
    assertThat(s.lastSeenAt()).isEqualTo(NOW);
    assertThat(s.lastRefreshAt()).isNull();
    assertThat(LoginSession.start(7L, ClientInfo.of(null, null, null), NOW).id())
        .isNotEqualTo(s.id());
  }

  @Test
  void 이메일은_소문자로_정규화() {
    assertThat(LocalCredential.normalizeEmail("  Kim@Wearly.TEST ")).isEqualTo("kim@wearly.test");
  }
}
