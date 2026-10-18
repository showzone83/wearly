package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.GeocodeResult;

/**
 * 장소 검색용 지오코딩 (19 문서). 지금은 Open-Meteo 지오코딩 API(language=ko, 한글 검색어·한글 이름)이고, 목업 실행이면 목업 장소 목록을 준다.
 * 호출 실패도 예외 없이 GeocodeResult로 돌려준다. 꺼져 있거나 실패하면 서비스가 날씨 공급자(WeatherAPI) 검색으로 대신한다.
 *
 * <p>Open-Meteo 무료 API는 비상업용이다. 상업 서비스로 바꿀 때는 설정(wearly.weather.geocoding.*)에서 유료 주소·키로 바꾸거나 끈다.
 */
public interface GeocodingPort {

  /** 호출 이력의 공급자 코드 (tb_weather_call_log.provider_code, 호출 종류 SEARCH) */
  String PROVIDER_CODE = "OPEN_METEO_GEO";

  /** 설정 geocoding.enabled */
  boolean enabled();

  /**
   * @param query 정리한 검색어 (소문자, 공백 하나)
   */
  GeocodeResult search(String query);
}
