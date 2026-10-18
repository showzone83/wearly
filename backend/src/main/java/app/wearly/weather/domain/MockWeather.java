package app.wearly.weather.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 목업 날씨 (WEARLY_WEATHER_PROVIDER=mock). 앱 목업(mobile/src/lib/weather.ts)과 같은 방식으로 서울 월평균 기온에 날짜별 고정
 * 난수를 더하고, 위도가 낮을수록 조금 따뜻하게 한다. 같은 날짜·지역은 늘 같은 값이다.
 */
public final class MockWeather {

  static final int[] SEOUL_MONTHLY_AVG = {-2, 1, 6, 13, 18, 23, 26, 26, 22, 15, 8, 1};
  static final double SEOUL_LAT = 37.5665;

  private MockWeather() {}

  public static WeatherReport report(WeatherRegion region, LocalDate today, int days, Instant now) {
    List<DailyForecast> list = new ArrayList<>();
    for (int i = 0; i < days; i++) {
      list.add(day(region, today.plusDays(i)));
    }
    DailyForecast first = list.getFirst();
    CurrentWeather current =
        new CurrentWeather(first.temp(), first.temp() - 1, first.cond(), first.condText(), now);
    return new WeatherReport(
        region.label() + " (목업)", current, list, Map.of("mock", true, "region", region.code()));
  }

  static DailyForecast day(WeatherRegion region, LocalDate date) {
    String key = region.code() + ":" + date;
    double avg = SEOUL_MONTHLY_AVG[date.getMonthValue() - 1] + (SEOUL_LAT - region.lat()) * 0.8;
    int temp = (int) Math.round(avg + (seeded(key, 1) - 0.5) * 8);
    int range = 6 + (int) Math.round(seeded(key, 2) * 6);
    double rainRoll = seeded(key, 3);
    int rainProb =
        rainRoll > 0.8 ? 60 + (int) Math.round(rainRoll * 30) : (int) Math.round(rainRoll * 40);
    WeatherCondition cond =
        rainProb >= 60
            ? WeatherCondition.RAIN
            : seeded(key, 4) > 0.5 ? WeatherCondition.CLOUDY : WeatherCondition.SUNNY;
    String text =
        switch (cond) {
          case RAIN -> "비";
          case CLOUDY -> "구름 많음";
          case SUNNY -> "맑음";
        };
    return new DailyForecast(
        date,
        temp,
        temp - Math.ceil(range / 2.0),
        temp + Math.floor(range / 2.0),
        rainProb,
        cond,
        text);
  }

  /** FNV-1a 해시로 만든 0~1 값 (앱 목업의 seeded와 같은 식) */
  static double seeded(String key, int salt) {
    int h = 0x811c9dc5 ^ salt;
    for (int i = 0; i < key.length(); i++) {
      h ^= key.charAt(i);
      h *= 16777619;
    }
    return (Integer.toUnsignedLong(h) % 10000) / 10000.0;
  }
}
