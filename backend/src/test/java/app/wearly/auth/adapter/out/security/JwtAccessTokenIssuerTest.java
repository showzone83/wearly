package app.wearly.auth.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** JWT exp는 초 단위라 올림해서 수명이 설정값보다 짧아지지 않게 한다 (ShortAccessTtlIntegrationTest가 가끔 실패하던 원인) */
class JwtAccessTokenIssuerTest {

  @Test
  void exp는_초_단위로_올림한다() {
    assertThat(JwtAccessTokenIssuer.ceilToSecond(Instant.parse("2026-10-17T00:00:01.999Z")))
        .isEqualTo(Instant.parse("2026-10-17T00:00:02Z"));
    assertThat(JwtAccessTokenIssuer.ceilToSecond(Instant.parse("2026-10-17T00:00:01.001Z")))
        .isEqualTo(Instant.parse("2026-10-17T00:00:02Z"));
    assertThat(JwtAccessTokenIssuer.ceilToSecond(Instant.parse("2026-10-17T00:00:02Z")))
        .isEqualTo(Instant.parse("2026-10-17T00:00:02Z"));
  }
}
