package app.wearly.weather.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

  /** 목업 과거 관측값 (평년값 계산용). 같은 식(서울 월평균 + 위도 보정 + 날짜별 고정 난수)으로 from~to의 하루하루를 만든다. 넷 중 하루쯤 비가 온다. */
  public static List<DailyObservation> climateDays(double lat, LocalDate from, LocalDate to) {
    List<DailyObservation> out = new ArrayList<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      String key = String.format(Locale.ROOT, "%.1f:%s", lat, d);
      double avg = SEOUL_MONTHLY_AVG[d.getMonthValue() - 1] + (SEOUL_LAT - lat) * 0.8;
      double mid = avg + (seeded(key, 1) - 0.5) * 6;
      double rain = seeded(key, 3) > 0.75 ? 4.0 : 0.0;
      out.add(new DailyObservation(d, mid + 4, mid - 4, rain));
    }
    return out;
  }

  /** 목업 장소 검색 대상 (한국어 이름, 공급자식 영어 이름, 시·도, 나라, 위도, 경도) */
  static final List<String[]> PLACES =
      List.of(
          new String[] {"서울", "Seoul", "", "South Korea", "37.57", "126.98"},
          new String[] {"부산", "Busan", "", "South Korea", "35.1", "129.04"},
          new String[] {"제주", "Jeju", "", "South Korea", "33.51", "126.52"},
          new String[] {"강릉", "Gangneung", "Kangwon-do", "South Korea", "37.75", "128.9"},
          new String[] {"도쿄", "Tokyo", "Tokyo", "Japan", "35.69", "139.69"},
          new String[] {"오사카", "Osaka", "Osaka", "Japan", "34.67", "135.5"},
          new String[] {"상하이", "Shanghai", "Shanghai", "China", "31.23", "121.47"},
          new String[] {"싱가포르", "Singapore", "", "Singapore", "1.29", "103.86"},
          new String[] {"파리", "Paris", "Ile-de-France", "France", "48.87", "2.33"},
          new String[] {
            "런던", "London", "City of London, Greater London", "United Kingdom", "51.52", "-0.11"
          },
          new String[] {
            "뉴욕", "New York", "New York", "United States of America", "40.71", "-74.01"
          });

  /** 목업 장소 검색: 한국어·영어 이름에 검색어가 들어간 곳 (대소문자 무시) */
  public static List<PlaceCandidate> search(String query) {
    String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    if (q.isEmpty()) {
      return List.of();
    }
    return PLACES.stream()
        .filter(p -> p[0].contains(q) || p[1].toLowerCase(Locale.ROOT).contains(q))
        .map(
            p ->
                new PlaceCandidate(
                    p[1], p[2], p[3], Double.parseDouble(p[4]), Double.parseDouble(p[5])))
        .toList();
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
