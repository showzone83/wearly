package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.request.WeatherRegionRequest;
import app.wearly.admin.adapter.in.web.request.WeatherSettingRequest;
import app.wearly.admin.adapter.in.web.response.PageResponse;
import app.wearly.admin.adapter.in.web.response.WeatherCallResponse;
import app.wearly.admin.adapter.in.web.response.WeatherCallSummaryResponse;
import app.wearly.admin.adapter.in.web.response.WeatherProviderResponse;
import app.wearly.admin.adapter.in.web.response.WeatherProviderTestResponse;
import app.wearly.admin.adapter.in.web.response.WeatherRefreshResponse;
import app.wearly.admin.adapter.in.web.response.WeatherRunDetailResponse;
import app.wearly.admin.adapter.in.web.response.WeatherRunResponse;
import app.wearly.admin.adapter.in.web.response.WeatherScheduleResponse;
import app.wearly.admin.adapter.in.web.response.WeatherSnapshotResponse;
import app.wearly.admin.application.port.in.AdminWeatherUseCase;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.in.dto.WeatherCallFilterDto;
import app.wearly.common.application.Patch;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.JsonPatches;
import app.wearly.common.web.PathIds;
import app.wearly.weather.application.port.in.dto.PatchWeatherProviderDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 관리자 날씨 API (17 문서 6장). ADMIN 전용 */
@RestController
@RequestMapping("/api/v1/admin/weather")
public class AdminWeatherController {

  private final AdminWeatherUseCase weather;

  public AdminWeatherController(AdminWeatherUseCase weather) {
    this.weather = weather;
  }

  @GetMapping("/settings")
  public WeatherScheduleResponse settings() {
    return WeatherScheduleResponse.from(weather.schedule());
  }

  @PutMapping("/settings")
  public WeatherScheduleResponse updateSettings(
      @CurrentUser long adminId, @RequestBody(required = false) WeatherSettingRequest request) {
    return WeatherScheduleResponse.from(
        weather.updateSetting(adminId, request == null ? null : request.toDto()));
  }

  @GetMapping("/providers")
  public List<WeatherProviderResponse> providers() {
    return weather.providers().stream().map(WeatherProviderResponse::from).toList();
  }

  @PatchMapping("/providers/{code}")
  public WeatherProviderResponse patchProvider(
      @CurrentUser long adminId,
      @PathVariable String code,
      @RequestBody(required = false) JsonNode body) {
    return WeatherProviderResponse.from(weather.patchProvider(adminId, code, toPatch(body)));
  }

  /** 차단 해제 (연속 실패·차단을 비운다) */
  @PostMapping("/providers/{code}/reset")
  public WeatherProviderResponse resetProvider(
      @CurrentUser long adminId, @PathVariable String code) {
    return WeatherProviderResponse.from(weather.resetProvider(adminId, code));
  }

  /** 공급자 하나로 한 지역 시험 호출. 저장된 날씨·공급자 상태는 바꾸지 않는다 */
  @PostMapping("/providers/{code}/test")
  public WeatherProviderTestResponse testProvider(
      @CurrentUser long adminId,
      @PathVariable String code,
      @RequestBody(required = false) WeatherRegionRequest request) {
    WeatherRegionRequest r = request == null ? WeatherRegionRequest.EMPTY : request;
    return WeatherProviderTestResponse.from(weather.testProvider(adminId, code, r.regionCode()));
  }

  /** 지금 실행 (요청 스레드 밖에서 돈다). 202 + runId */
  @PostMapping("/refresh")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public WeatherRefreshResponse refresh(
      @CurrentUser long adminId, @RequestBody(required = false) WeatherRegionRequest request) {
    WeatherRegionRequest r = request == null ? WeatherRegionRequest.EMPTY : request;
    return WeatherRefreshResponse.of(weather.refresh(adminId, r.regionCode()));
  }

  @GetMapping("/runs")
  public PageResponse<WeatherRunResponse> runs(
      @RequestParam(required = false) String trigger,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        weather.runs(trigger, status, PagingDto.of(page, size)), WeatherRunResponse::from);
  }

  @GetMapping("/runs/{runId}")
  public WeatherRunDetailResponse run(@PathVariable String runId) {
    return WeatherRunDetailResponse.from(weather.run(PathIds.parse(runId)));
  }

  @GetMapping("/calls")
  public PageResponse<WeatherCallResponse> calls(
      @RequestParam(required = false) String providerCode,
      @RequestParam(required = false) String callKind,
      @RequestParam(required = false) String regionCode,
      @RequestParam(required = false) String success,
      @RequestParam(required = false) String fallback,
      @RequestParam(required = false) String errorCode,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        weather.calls(
            new WeatherCallFilterDto(
                providerCode, callKind, regionCode, success, fallback, errorCode, from, to),
            PagingDto.of(page, size)),
        WeatherCallResponse::from);
  }

  @GetMapping("/calls/summary")
  public WeatherCallSummaryResponse callSummary() {
    return WeatherCallSummaryResponse.from(weather.callSummary());
  }

  @GetMapping("/snapshots")
  public List<WeatherSnapshotResponse> snapshots() {
    return weather.snapshots().stream().map(WeatherSnapshotResponse::from).toList();
  }

  @GetMapping("/snapshots/{regionCode}")
  public WeatherSnapshotResponse snapshot(@PathVariable String regionCode) {
    return WeatherSnapshotResponse.from(weather.snapshot(regionCode));
  }

  /** JSON → 공급자 부분 수정. 형식(JSON 타입) 판단은 여기서 한다 (오류는 INVALID_TYPE + params.expected) (M4) */
  private static PatchWeatherProviderDto toPatch(JsonNode body) {
    if (!JsonPatches.isObject(body)) {
      return null;
    }
    return new PatchWeatherProviderDto(
        JsonPatches.bool(body, "enabled"),
        JsonPatches.integer(body, "priority"),
        body.has("monthlyQuota") && body.get("monthlyQuota").isNull()
            ? Patch.<Integer>of(null)
            : JsonPatches.integer(body, "monthlyQuota"),
        JsonPatches.integer(body, "maxForecastDays"));
  }
}
