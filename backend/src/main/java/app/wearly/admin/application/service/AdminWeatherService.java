package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminWeatherUseCase;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.in.dto.WeatherCallFilterDto;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.admin.domain.SearchText;
import app.wearly.common.error.ApiException;
import app.wearly.weather.application.port.in.WeatherAdminUseCase;
import app.wearly.weather.application.port.in.dto.CallSearchDto;
import app.wearly.weather.application.port.in.dto.PageResultDto;
import app.wearly.weather.application.port.in.dto.PatchWeatherProviderDto;
import app.wearly.weather.application.port.in.dto.ProviderTestResultDto;
import app.wearly.weather.application.port.in.dto.RefreshRunDetailDto;
import app.wearly.weather.application.port.in.dto.RefreshRunDto;
import app.wearly.weather.application.port.in.dto.RunSearchDto;
import app.wearly.weather.application.port.in.dto.UpdateWeatherSettingDto;
import app.wearly.weather.application.port.in.dto.WeatherCallDto;
import app.wearly.weather.application.port.in.dto.WeatherCallSummaryDto;
import app.wearly.weather.application.port.in.dto.WeatherProviderChangeDto;
import app.wearly.weather.application.port.in.dto.WeatherProviderDto;
import app.wearly.weather.application.port.in.dto.WeatherScheduleDto;
import app.wearly.weather.application.port.in.dto.WeatherSettingChangeDto;
import app.wearly.weather.application.port.in.dto.WeatherSnapshotDto;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 날씨 관리 (17 문서 6장). 설정·공급자 변경은 weather 유스케이스와 같은 트랜잭션에 변경 이력을 남긴다. 지금 실행·시험 호출은 외부 HTTP라 트랜잭션
 * 없이 부른다.
 */
@Service
public class AdminWeatherService implements AdminWeatherUseCase {

  static final String SETTING_TARGET_ID = "default";

  private final WeatherAdminUseCase weather;
  private final RecordAuditPort audit;
  private final AdminAuditRecorder recorder;

  public AdminWeatherService(
      WeatherAdminUseCase weather, RecordAuditPort audit, AdminAuditRecorder recorder) {
    this.weather = weather;
    this.audit = audit;
    this.recorder = recorder;
  }

  @Override
  public WeatherScheduleDto schedule() {
    return weather.schedule();
  }

  @Override
  @Transactional
  public WeatherScheduleDto updateSetting(long adminId, UpdateWeatherSettingDto in) {
    WeatherSettingChangeDto change = weather.updateSetting(adminId, in);
    if (change.changed()) {
      audit.record(
          adminId,
          "WEATHER_SETTING_UPDATE",
          AuditTargets.WEATHER_SETTING,
          SETTING_TARGET_ID,
          AuditSnapshots.weatherSetting(change.before()),
          AuditSnapshots.weatherSetting(change.after()));
    }
    return weather.schedule();
  }

  @Override
  public List<WeatherProviderDto> providers() {
    return weather.providers();
  }

  @Override
  @Transactional
  public WeatherProviderDto patchProvider(
      long adminId, String code, PatchWeatherProviderDto patch) {
    WeatherProviderChangeDto change = weather.patchProvider(adminId, code, patch);
    audit.record(
        adminId,
        "WEATHER_PROVIDER_UPDATE",
        AuditTargets.WEATHER_PROVIDER,
        code,
        AuditSnapshots.weatherProvider(change.before()),
        AuditSnapshots.weatherProvider(change.after()));
    return change.after();
  }

  @Override
  @Transactional
  public WeatherProviderDto resetProvider(long adminId, String code) {
    WeatherProviderChangeDto change = weather.resetProvider(adminId, code);
    audit.record(
        adminId,
        "WEATHER_PROVIDER_RESET",
        AuditTargets.WEATHER_PROVIDER,
        code,
        AuditSnapshots.weatherProvider(change.before()),
        AuditSnapshots.weatherProvider(change.after()));
    return change.after();
  }

  @Override
  public ProviderTestResultDto testProvider(long adminId, String code, String regionCode) {
    return weather.testProvider(adminId, code, regionCode);
  }

  /** 실행 행은 다른 스레드가 바로 읽어야 해서 먼저 커밋되고, 변경 이력은 그 뒤 따로 남긴다 */
  @Override
  public long refresh(long adminId, String regionCode) {
    String region = SearchText.blankToNull(regionCode);
    long runId = weather.requestRefresh(adminId, region);
    recorder.record(
        adminId,
        "WEATHER_REFRESH",
        AuditTargets.WEATHER_REFRESH,
        Long.toString(runId),
        null,
        region == null ? Map.of("scope", "ALL") : Map.of("scope", "REGION", "regionCode", region));
    return runId;
  }

  @Override
  public PageDto<RefreshRunDto> runs(String trigger, String status, PagingDto paging) {
    RunSearchDto search =
        new RunSearchDto(SearchText.blankToNull(trigger), SearchText.blankToNull(status));
    return page(weather.runs(search, paging.size(), paging.offset()), paging);
  }

  @Override
  public RefreshRunDetailDto run(long runId) {
    return weather.run(runId);
  }

  @Override
  public PageDto<WeatherCallDto> calls(WeatherCallFilterDto f, PagingDto paging) {
    CallSearchDto search =
        new CallSearchDto(
            upper(f.providerCode()),
            upper(f.regionCode()),
            bool("success", f.success()),
            bool("fallback", f.fallback()),
            upper(f.errorCode()),
            SearchTimes.parse("from", f.from(), false),
            SearchTimes.parse("to", f.to(), true));
    return page(weather.calls(search, paging.size(), paging.offset()), paging);
  }

  @Override
  public WeatherCallSummaryDto callSummary() {
    return weather.callSummary();
  }

  @Override
  public List<WeatherSnapshotDto> snapshots() {
    return weather.snapshots();
  }

  @Override
  public WeatherSnapshotDto snapshot(String regionCode) {
    return weather.snapshot(regionCode);
  }

  private static <T> PageDto<T> page(PageResultDto<T> r, PagingDto paging) {
    return new PageDto<>(r.items(), paging.page(), paging.size(), r.total());
  }

  private static String upper(String raw) {
    String v = SearchText.blankToNull(raw);
    return v == null ? null : v.toUpperCase(Locale.ROOT);
  }

  private static Boolean bool(String field, String raw) {
    String v = SearchText.blankToNull(raw);
    if (v == null) {
      return null;
    }
    return switch (v.toLowerCase(Locale.ROOT)) {
      case "true" -> true;
      case "false" -> false;
      default -> throw ApiException.validation(field, "true 또는 false여야 해요.");
    };
  }
}
