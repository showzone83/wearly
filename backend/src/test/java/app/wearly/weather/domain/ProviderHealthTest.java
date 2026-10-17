package app.wearly.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 17 문서 4장: 공급자 차단(쿨다운)·시험·키 문제 상태 전이 */
class ProviderHealthTest {

  static final Instant T0 = Instant.parse("2026-10-17T00:00:00Z");
  static final Duration COOLDOWN = Duration.ofMinutes(15);

  @Test
  void 연속_실패가_기준에_닿으면_쿨다운_동안_차단된다() {
    ProviderHealth h = ProviderHealth.fresh();
    h = h.failed(T0, FetchErrors.HTTP_5XX, "오류1", 3, COOLDOWN);
    h = h.failed(T0, FetchErrors.TIMEOUT, "오류2", 3, COOLDOWN);
    assertThat(h.status(T0)).isEqualTo(ProviderStatus.OK);
    assertThat(h.consecutiveFailures()).isEqualTo(2);

    h = h.failed(T0, FetchErrors.HTTP_5XX, "오류3", 3, COOLDOWN);
    assertThat(h.status(T0)).isEqualTo(ProviderStatus.OPEN);
    assertThat(h.circuitOpenUntil()).isEqualTo(T0.plus(COOLDOWN));
    assertThat(h.lastError()).isEqualTo("오류3");
    // 쿨다운이 끝나면 시험 상태
    assertThat(h.status(T0.plus(COOLDOWN))).isEqualTo(ProviderStatus.HALF_OPEN);
  }

  @Test
  void 시험에서_성공하면_정상으로_실패하면_바로_다시_차단된다() {
    ProviderHealth open = ProviderHealth.fresh();
    for (int i = 0; i < 3; i++) {
      open = open.failed(T0, FetchErrors.HTTP_5XX, "x", 3, COOLDOWN);
    }
    Instant later = T0.plus(COOLDOWN).plusSeconds(1);
    assertThat(open.status(later)).isEqualTo(ProviderStatus.HALF_OPEN);

    ProviderHealth ok = open.succeeded(later);
    assertThat(ok.status(later)).isEqualTo(ProviderStatus.OK);
    assertThat(ok.consecutiveFailures()).isZero();
    assertThat(ok.lastSuccessAt()).isEqualTo(later);

    // 기준(3)보다 적어도 시험 실패면 바로 차단
    ProviderHealth reopened = open.failed(later, FetchErrors.HTTP_5XX, "y", 100, COOLDOWN);
    assertThat(reopened.status(later)).isEqualTo(ProviderStatus.OPEN);
    assertThat(reopened.circuitOpenUntil()).isEqualTo(later.plus(COOLDOWN));
  }

  @Test
  void 인증_오류는_한_번에_키_확인_필요가_되고_시간이_지나도_풀리지_않는다() {
    ProviderHealth h = ProviderHealth.fresh().failed(T0, FetchErrors.AUTH, "인증", 3, COOLDOWN);
    assertThat(h.status(T0)).isEqualTo(ProviderStatus.KEY_INVALID);
    assertThat(h.status(T0.plus(Duration.ofDays(30)))).isEqualTo(ProviderStatus.KEY_INVALID);

    ProviderHealth reset = h.reset();
    assertThat(reset.status(T0)).isEqualTo(ProviderStatus.OK);
    assertThat(reset.consecutiveFailures()).isZero();
    assertThat(reset.lastFailureAt()).isEqualTo(T0);
  }

  @Test
  void 차단_중이거나_키_문제거나_한도를_넘으면_부를_수_없다() {
    WeatherProvider p =
        new WeatherProvider(
            "WEATHERAPI",
            "WeatherAPI.com",
            true,
            1,
            "K",
            100,
            3,
            ProviderHealth.fresh(),
            null,
            null);
    assertThat(p.usable(true, T0, 99, 1)).isTrue();
    assertThat(p.usable(true, T0, 100, 1)).as("한도 초과").isFalse();
    assertThat(p.usable(false, T0, 0, 1)).as("키 없음").isFalse();
    assertThat(p.configured(false, 1, 100, 3, null, T0).usable(true, T0, 0, 1)).as("꺼짐").isFalse();
    WeatherProvider open =
        p.withHealth(ProviderHealth.fresh().failed(T0, FetchErrors.HTTP_5XX, "x", 1, COOLDOWN));
    assertThat(open.usable(true, T0, 0, 1)).isFalse();
    assertThat(open.usable(true, T0.plus(COOLDOWN), 0, 1)).as("시험 상태는 부른다").isTrue();
    WeatherProvider noQuota = p.configured(true, 1, null, 3, null, T0);
    assertThat(noQuota.usable(true, T0, 1_000_000, 1)).isTrue();
  }
}
