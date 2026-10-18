package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.ClimateFetch;
import java.time.LocalDate;

/**
 * 과거 기상 자료 (평년값 계산용, 19 문서 4장). 지금은 Open-Meteo 과거 기상 API이고, 목업 실행이면 목업 자료를 준다. 호출 실패도 예외 없이
 * ClimateFetch로 돌려준다.
 *
 * <p>Open-Meteo 무료 API는 비상업용이다. 상업 서비스로 바꿀 때는 설정(wearly.weather.climate.*)에서 유료 주소·키로 바꾸거나 끈다.
 */
public interface ClimateSourcePort {

  /** 호출 이력의 공급자 코드 */
  String PROVIDER_CODE = "OPEN_METEO";

  /** tb_weather_climate.source에 남길 값 (OPEN_METEO, 목업 실행이면 MOCK) */
  String sourceCode();

  /**
   * from~to(포함)의 일별 최고·최저 기온과 강수량.
   *
   * @param target 호출 기록의 regionCode (격자 키)
   */
  ClimateFetch fetch(String target, double lat, double lon, LocalDate from, LocalDate to);
}
