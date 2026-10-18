package app.wearly.weather.domain;

import java.util.Locale;

/**
 * 좌표 캐시 키 (19 문서). 위도·경도를 소수 첫째 자리로 반올림한다 (약 11km 격자). 같은 도시를 찾는 요청은 같은 키로 모여 예보 캐시와 평년값을 같이 쓴다.
 *
 * @param key "37.6,127.0" (tb_weather_place_snapshot·tb_weather_climate의 grid_key)
 * @param lat 반올림한 위도
 * @param lon 반올림한 경도
 */
public record GridKey(String key, double lat, double lon) {

  public static GridKey of(double lat, double lon) {
    double la = round(lat);
    double lo = round(lon);
    return new GridKey(String.format(Locale.ROOT, "%.1f,%.1f", la, lo), la, lo);
  }

  /** 소수 첫째 자리 반올림. -0.0은 0.0으로 (키가 "-0.0"이 되지 않게) */
  static double round(double v) {
    double r = Math.round(v * 10) / 10.0;
    return r == 0 ? 0.0 : r;
  }

  public static boolean validLat(double lat) {
    return Double.isFinite(lat) && lat >= -90 && lat <= 90;
  }

  public static boolean validLon(double lon) {
    return Double.isFinite(lon) && lon >= -180 && lon <= 180;
  }

  /** 두 좌표 사이 거리 (km, 하버사인) */
  public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
    double latDelta = Math.toRadians(lat2 - lat1);
    double lonDelta = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDelta / 2)
                * Math.sin(lonDelta / 2);
    return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
}
