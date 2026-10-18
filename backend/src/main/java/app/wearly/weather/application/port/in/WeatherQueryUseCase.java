package app.wearly.weather.application.port.in;

import app.wearly.weather.application.port.in.dto.WeatherViewDto;

/** 앱 날씨 조회 (17 문서 5장). DB에 저장된 값만 읽고 외부 API는 부르지 않는다 */
public interface WeatherQueryUseCase {

  /**
   * @param regionCode null이면 서울. 없는 지역이면 400, 한 번도 받지 못했으면 503 WEATHER_UNAVAILABLE
   */
  WeatherViewDto current(String regionCode);
}
