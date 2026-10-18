package app.wearly.weather.domain;

/** 앱이 쓰는 날씨 상태 (mobile Weather.cond). 공급자 상태 코드는 각 어댑터가 이 세 값으로 바꾼다 (17 문서 4장) */
public enum WeatherCondition {
  SUNNY,
  CLOUDY,
  RAIN
}
