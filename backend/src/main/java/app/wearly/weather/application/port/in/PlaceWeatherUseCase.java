package app.wearly.weather.application.port.in;

import app.wearly.weather.application.port.in.dto.PlaceCandidateDto;
import app.wearly.weather.application.port.in.dto.WeatherDayDto;
import app.wearly.weather.application.port.in.dto.WeatherDayQueryDto;
import java.util.List;

/**
 * 장소별 날씨 (19 문서): 장소(REGION 또는 좌표)와 날짜 하나의 날씨, 장소 검색. REGION은 저장된 예보만 읽고, REGION 밖 좌표는 캐시가 오래됐을 때만
 * 공급자를 한 번 부른다 (회원별 하루 한도). 예보 밖 날짜는 평년값.
 */
public interface PlaceWeatherUseCase {

  /**
   * 날짜 하나의 날씨. 예보에 그 날짜가 있으면 FORECAST, 없으면 그 격자·그 달의 평년값 CLIMATE.
   *
   * <p>오류: 날짜·지역·좌표가 틀리면 400 VALIDATION_FAILED, 좌표 요청인데 로그인하지 않았으면 401 UNAUTHORIZED, 예보도 평년값도 없으면
   * 503 WEATHER_UNAVAILABLE, 그런데 오늘 좌표 요청 한도를 넘어 공급자를 부르지 못했으면 429 WEATHER_LIMIT.
   */
  WeatherDayDto day(WeatherDayQueryDto query);

  /**
   * 장소 검색 (최대 10곳). 같은 검색어는 서버가 하루 캐시한다. REGION 좌표와 30km 안이면 regionCode를 붙인다.
   *
   * <p>오류: 검색어가 비었거나 50자를 넘으면 400(q), 오늘 검색 한도를 넘었으면 429 WEATHER_LIMIT, 검색할 공급자가 없거나 모두 실패하면 503
   * WEATHER_UNAVAILABLE.
   */
  List<PlaceCandidateDto> searchPlaces(long userId, String query);
}
