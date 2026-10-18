package app.wearly.notify.domain;

/**
 * D-1 알림에 쓰는 하루 날씨 (기온은 섭씨, 강수 확률 0~100).
 *
 * @param source FORECAST(예보) · CLIMATE(평년) · MANUAL(사용자가 고른 체감 온도)
 */
public record DayBeforeWeather(double temp, double rainProb, String source) {

  public static final String FORECAST = "FORECAST";
  public static final String CLIMATE = "CLIMATE";
  public static final String MANUAL = "MANUAL";

  /** 이만큼(°C) 이상 달라지면 예보가 바뀌었다고 본다 (앱 lib/planWeather.ts RECHECK_TEMP_DIFF와 같다) */
  public static final int TEMP_DIFF = 3;

  /** 비 여부 기준 강수 확률 (앱 RAIN_PROB_LINE과 같다) */
  public static final int RAIN_PROB_LINE = 50;

  /** 앱 규칙대로 정수로 반올림한 기온 */
  public long roundedTemp() {
    return Math.round(temp);
  }

  public boolean rainy() {
    return Math.round(Math.max(0, Math.min(100, rainProb))) >= RAIN_PROB_LINE;
  }

  /**
   * 저장된 날씨(saved)와 다시 확인한 날씨(this)가 코디를 바꿀 만큼 다른지: 기온 3도 이상 또는 비 여부가 바뀜. 앱 planWeather.ts의
   * weatherChanged와 같은 규칙이다 (다시 확인한 기온은 반올림해서 비교).
   */
  public boolean changedFrom(DayBeforeWeather saved) {
    return Math.abs(saved.temp - roundedTemp()) >= TEMP_DIFF || saved.rainy() != rainy();
  }

  /** 출처 표시 (예보 · 평년 · 직접 입력) */
  public String sourceLabel() {
    if (CLIMATE.equals(source)) {
      return "평년";
    }
    if (MANUAL.equals(source)) {
      return "직접 입력";
    }
    return "예보";
  }
}
