package app.wearly.appsetting.adapter.in.web;

import app.wearly.appsetting.adapter.in.web.response.AppConfigResponse;
import app.wearly.appsetting.application.port.in.AppSettingUseCase;
import app.wearly.appsetting.application.port.in.dto.AppConfigDto;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/** 공개 앱 설정 조회 (12 문서 3장). 코드 API와 같게 ETag = "version", If-None-Match가 같으면 304 */
@RestController
@RequestMapping("/api/v1/app/config")
public class AppConfigController {

  private final AppSettingUseCase settings;

  public AppConfigController(AppSettingUseCase settings) {
    this.settings = settings;
  }

  @GetMapping
  public ResponseEntity<AppConfigResponse> config(WebRequest request) {
    AppConfigDto config = settings.config();
    String etag = "\"" + config.version() + "\"";
    // 캐시는 해도 매번 서버에 확인하게 한다 (no-cache = 재검증 필수)
    CacheControl cache = CacheControl.noCache();
    if (request.checkNotModified(etag)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
    }
    return ResponseEntity.ok().eTag(etag).cacheControl(cache).body(AppConfigResponse.from(config));
  }
}
