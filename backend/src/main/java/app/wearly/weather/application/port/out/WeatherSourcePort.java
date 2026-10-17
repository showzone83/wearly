package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.FetchResult;
import app.wearly.weather.domain.KeyState;
import app.wearly.weather.domain.WeatherProvider;
import app.wearly.weather.domain.WeatherRegion;

/**
 * 날씨 공급자 API (HTTP). 공급자 코드마다 구현이 있고(지금은 WEATHERAPI), 목업 실행이면 모든 공급자를 목업 날씨로 대신한다. 호출 실패도 예외 없이
 * FetchResult로 돌려준다. 키는 오류 문구·호출 기록에 남기지 않는다.
 */
public interface WeatherSourcePort {

  KeyState keyState(WeatherProvider provider);

  /** 한 지역을 받는 데 드는 HTTP 호출 수 (월 한도 계산용) */
  int callsPerFetch(String providerCode);

  /**
   * @param days 받을 예보 일수 (오늘 포함)
   */
  FetchResult fetch(WeatherProvider provider, WeatherRegion region, int days);
}
