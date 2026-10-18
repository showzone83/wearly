package app.wearly.weather.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 격자의 한 달 평년값 (tb_weather_climate). 기온은 섭씨.
 *
 * @param tempAvg (최고+최저)/2의 평균
 * @param tempMin 일 최저 기온의 평균
 * @param tempMax 일 최고 기온의 평균
 * @param rainDayRatio 강수 1mm 이상인 날의 비율 (0~1)
 * @param years 계산에 쓴 연도 (예: "2016-2025")
 * @param source 자료 출처 (OPEN_METEO, 목업 실행이면 MOCK)
 */
public record MonthlyClimate(
    String gridKey,
    int month,
    double tempAvg,
    double tempMin,
    double tempMax,
    double rainDayRatio,
    String years,
    String source,
    Instant computedAt) {

  /** 이보다 오래된 평년값은 다시 계산한다 */
  public static final Duration MAX_AGE = Duration.ofDays(400);

  /** 비 온 날로 치는 하루 강수량 (mm) */
  public static final double RAIN_DAY_MM = 1.0;

  public boolean fresh(Instant now) {
    return computedAt.plus(MAX_AGE).isAfter(now);
  }

  /** 앱 rainProb (0~100): 비 온 날 비율 × 100 반올림 */
  public int rainProb() {
    return (int) Math.round(rainDayRatio * 100);
  }

  /**
   * 일별 관측값으로 월별 평년값을 만든다. 기온이 하나도 없는 달은 빠진다 (그 달은 평년값 없음).
   *
   * @return 월 순서 (최대 12개)
   */
  public static List<MonthlyClimate> compute(
      String gridKey, List<DailyObservation> days, String years, String source, Instant now) {
    double[] maxSum = new double[13];
    double[] minSum = new double[13];
    double[] avgSum = new double[13];
    int[] maxN = new int[13];
    int[] minN = new int[13];
    int[] avgN = new int[13];
    int[] rainDays = new int[13];
    int[] rainN = new int[13];
    for (DailyObservation d : days) {
      int m = d.date().getMonthValue();
      if (d.max() != null) {
        maxSum[m] += d.max();
        maxN[m]++;
      }
      if (d.min() != null) {
        minSum[m] += d.min();
        minN[m]++;
      }
      if (d.max() != null && d.min() != null) {
        avgSum[m] += (d.max() + d.min()) / 2;
        avgN[m]++;
      }
      if (d.precipitation() != null) {
        rainN[m]++;
        if (d.precipitation() >= RAIN_DAY_MM) {
          rainDays[m]++;
        }
      }
    }
    List<MonthlyClimate> out = new ArrayList<>();
    for (int m = 1; m <= 12; m++) {
      if (maxN[m] == 0 || minN[m] == 0 || avgN[m] == 0) {
        continue;
      }
      double ratio = rainN[m] == 0 ? 0 : (double) rainDays[m] / rainN[m];
      out.add(
          new MonthlyClimate(
              gridKey,
              m,
              avgSum[m] / avgN[m],
              minSum[m] / minN[m],
              maxSum[m] / maxN[m],
              ratio,
              years,
              source,
              now));
    }
    return out;
  }
}
