package app.wearly.weather.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.weather.adapter.in.web.response.PlaceSearchResponse;
import app.wearly.weather.adapter.in.web.response.WeatherDayResponse;
import app.wearly.weather.adapter.in.web.response.WeatherResponse;
import app.wearly.weather.application.port.in.PlaceWeatherUseCase;
import app.wearly.weather.application.port.in.WeatherQueryUseCase;
import app.wearly.weather.application.port.in.dto.WeatherDayQueryDto;
import app.wearly.weather.application.port.in.dto.WeatherViewDto;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * 앱 날씨 조회 (17 문서 5장). 로그인 없이 부를 수 있다. 서버에 저장된 값만 주고 외부 API는 부르지 않는다. 코드·앱 설정 API와 같게
 * ETag("version")를 주고 If-None-Match가 같으면 304.
 *
 * <p>장소별 날씨 (19 문서): GET /day는 region 방식이면 로그인 없이(보안 설정에서 공개, 토큰은 보지 않는다), lat·lon 방식이면 로그인이 필요하다
 * (여기서 확인해 401 UNAUTHORIZED). GET /places(장소 검색)는 로그인 필요 (보안 설정).
 */
@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

  private final WeatherQueryUseCase weather;
  private final PlaceWeatherUseCase places;

  public WeatherController(WeatherQueryUseCase weather, PlaceWeatherUseCase places) {
    this.weather = weather;
    this.places = places;
  }

  @GetMapping
  public ResponseEntity<WeatherResponse> current(
      @RequestParam(required = false) String region, WebRequest request) {
    WeatherViewDto view = weather.current(region);
    String etag = "\"" + view.version() + "\"";
    CacheControl cache = CacheControl.noCache();
    if (request.checkNotModified(etag)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
    }
    return ResponseEntity.ok().eTag(etag).cacheControl(cache).body(WeatherResponse.from(view));
  }

  /** 날짜 하나의 날씨: ?region=SEOUL&date=YYYY-MM-DD 또는 ?lat=..&lon=..&date=.. (좌표는 로그인 필요) */
  @GetMapping("/day")
  public WeatherDayResponse day(
      @RequestParam(required = false) String region,
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lon,
      @RequestParam(required = false) String date,
      @AuthenticationPrincipal Jwt jwt) {
    Long userId = jwt == null ? null : Long.parseLong(jwt.getSubject());
    return WeatherDayResponse.from(
        places.day(new WeatherDayQueryDto(region, lat, lon, date, userId)));
  }

  /** 장소 검색 (로그인 필요): ?q=도쿄 (1~50자) */
  @GetMapping("/places")
  public PlaceSearchResponse places(
      @RequestParam(required = false) String q, @CurrentUser long userId) {
    return PlaceSearchResponse.from(places.searchPlaces(userId, q));
  }
}
