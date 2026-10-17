package app.wearly.weather.domain;

import java.time.Duration;
import java.util.Locale;

/**
 * 서버 설정(wearly.weather.*)에서 온 고정값. 운영값(주기·예보 일수 등)은 DB의 WeatherSetting에 있다.
 *
 * @param mock 공급자 대신 목업 날씨 (WEARLY_WEATHER_PROVIDER=mock)
 * @param schedulerEnabled false면 주기 실행·시작 실행·이력 정리를 하지 않는다
 */
public record WeatherOptions(boolean mock, boolean schedulerEnabled, int logRetentionDays) {

  /** 실행 잠금 유지 시간. 실행이 멈춰도 이 시간이 지나면 풀린다 (17개 지역 × 시간 초과 10초보다 길게) */
  public static final Duration LOCK_TTL = Duration.ofMinutes(5);

  /** 서버 시작 뒤 첫 실행까지 기다리는 시간 */
  public static final Duration STARTUP_DELAY = Duration.ofSeconds(10);

  /** 설정 값 db | mock (대소문자 무시, 비우면 db). 그 밖이면 시작을 멈춘다 */
  public static boolean isMock(String provider) {
    String v = provider == null ? "" : provider.strip().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "", "db" -> false;
      case "mock" -> true;
      default ->
          throw new IllegalStateException(
              "wearly.weather.provider(WEARLY_WEATHER_PROVIDER)는 db 또는 mock이어야 해요: '"
                  + provider
                  + "'");
    };
  }
}
