package app.wearly.weather.adapter.in.web;

import app.wearly.weather.adapter.in.web.response.WeatherResponse;
import app.wearly.weather.application.port.in.WeatherQueryUseCase;
import app.wearly.weather.application.port.in.dto.WeatherViewDto;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * 앱 날씨 조회 (17 문서 5장). 로그인 없이 부를 수 있다. 서버에 저장된 값만 주고 외부 API는 부르지 않는다. 코드·앱 설정 API와 같게
 * ETag("version")를 주고 If-None-Match가 같으면 304.
 */
@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

  private final WeatherQueryUseCase weather;

  public WeatherController(WeatherQueryUseCase weather) {
    this.weather = weather;
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
}
