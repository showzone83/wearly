package app.wearly.weather.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.weather.application.port.in.WeatherAdminUseCase;
import app.wearly.weather.application.port.in.dto.CallSearchDto;
import app.wearly.weather.application.port.in.dto.CurrentWeatherDto;
import app.wearly.weather.application.port.in.dto.DailyForecastDto;
import app.wearly.weather.application.port.in.dto.PageResultDto;
import app.wearly.weather.application.port.in.dto.PatchWeatherProviderDto;
import app.wearly.weather.application.port.in.dto.ProviderTestResultDto;
import app.wearly.weather.application.port.in.dto.RefreshRunDetailDto;
import app.wearly.weather.application.port.in.dto.RefreshRunDto;
import app.wearly.weather.application.port.in.dto.RunSearchDto;
import app.wearly.weather.application.port.in.dto.UpdateWeatherSettingDto;
import app.wearly.weather.application.port.in.dto.WeatherCallDto;
import app.wearly.weather.application.port.in.dto.WeatherCallSummaryDto;
import app.wearly.weather.application.port.in.dto.WeatherCallSummaryDto.MonthUsageDto;
import app.wearly.weather.application.port.in.dto.WeatherProviderChangeDto;
import app.wearly.weather.application.port.in.dto.WeatherProviderDto;
import app.wearly.weather.application.port.in.dto.WeatherScheduleDto;
import app.wearly.weather.application.port.in.dto.WeatherSettingChangeDto;
import app.wearly.weather.application.port.in.dto.WeatherSettingDto;
import app.wearly.weather.application.port.in.dto.WeatherSnapshotDto;
import app.wearly.weather.application.port.out.RefreshExecutorPort;
import app.wearly.weather.application.port.out.RefreshLockPort;
import app.wearly.weather.application.port.out.WeatherLogQueryPort;
import app.wearly.weather.application.port.out.WeatherLogQueryPort.CallFilter;
import app.wearly.weather.application.port.out.WeatherLogQueryPort.RunFilter;
import app.wearly.weather.application.port.out.WeatherProviderPort;
import app.wearly.weather.application.port.out.WeatherRunPort;
import app.wearly.weather.application.port.out.WeatherSettingPort;
import app.wearly.weather.application.port.out.WeatherSnapshotPort;
import app.wearly.weather.application.port.out.WeatherSourcePort;
import app.wearly.weather.application.port.out.dto.CallRowDto;
import app.wearly.weather.application.port.out.dto.CallSummaryRowDto;
import app.wearly.weather.application.port.out.dto.RunRowDto;
import app.wearly.weather.domain.FetchResult;
import app.wearly.weather.domain.KeyState;
import app.wearly.weather.domain.RefreshRun;
import app.wearly.weather.domain.RunStatus;
import app.wearly.weather.domain.RunTrigger;
import app.wearly.weather.domain.WeatherOptions;
import app.wearly.weather.domain.WeatherProvider;
import app.wearly.weather.domain.WeatherRegion;
import app.wearly.weather.domain.WeatherSetting;
import app.wearly.weather.domain.WeatherSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 날씨 관리 (17 문서 6장). 변경 이력은 admin 컨텍스트가 남긴다 */
@Service
public class WeatherAdminService implements WeatherAdminUseCase {

  /** 실행 상세에 보여 줄 호출 수 상한 (지역 17개 × 공급자 몇 곳이면 충분하다) */
  static final int RUN_CALLS_LIMIT = 500;

  private final WeatherRefreshRunner runner;
  private final WeatherSettingPort settings;
  private final WeatherProviderPort providers;
  private final WeatherSnapshotPort snapshots;
  private final WeatherRunPort runs;
  private final WeatherLogQueryPort logs;
  private final WeatherSourcePort sources;
  private final RefreshLockPort lock;
  private final RefreshExecutorPort executor;
  private final CodeCatalog catalog;
  private final WeatherOptions options;
  private final Clock clock;

  public WeatherAdminService(
      WeatherRefreshRunner runner,
      WeatherSettingPort settings,
      WeatherProviderPort providers,
      WeatherSnapshotPort snapshots,
      WeatherRunPort runs,
      WeatherLogQueryPort logs,
      WeatherSourcePort sources,
      RefreshLockPort lock,
      RefreshExecutorPort executor,
      CodeCatalog catalog,
      WeatherOptions options,
      Clock clock) {
    this.runner = runner;
    this.settings = settings;
    this.providers = providers;
    this.snapshots = snapshots;
    this.runs = runs;
    this.logs = logs;
    this.sources = sources;
    this.lock = lock;
    this.executor = executor;
    this.catalog = catalog;
    this.options = options;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public WeatherScheduleDto schedule() {
    WeatherSetting setting = runner.setting();
    RefreshRunDto last = runs.latestFinished().flatMap(r -> runRow(r.runId())).orElse(null);
    Instant nextRunAt = null;
    if (options.schedulerEnabled() && setting.enabled()) {
      nextRunAt =
          last == null || last.finishedAt() == null
              ? null
              : setting.nextRunAfter(last.finishedAt());
    }
    return new WeatherScheduleDto(
        WeatherSettingDto.from(setting),
        nextRunAt,
        lock.held(),
        last,
        runner.activeRegions().size(),
        options.schedulerEnabled());
  }

  @Override
  @Transactional
  public WeatherSettingChangeDto updateSetting(long adminId, UpdateWeatherSettingDto in) {
    List<FieldError> errors = new ArrayList<>();
    if (in == null) {
      throw BusinessException.validation(FieldError.invalidType("body", "object"));
    }
    required(errors, "enabled", in.enabled());
    required(errors, "intervalMinutes", in.intervalMinutes());
    required(errors, "forecastDays", in.forecastDays());
    required(errors, "failureThreshold", in.failureThreshold());
    required(errors, "cooldownMinutes", in.cooldownMinutes());
    BusinessException.throwIfAny(errors);
    BusinessException.throwIfAny(
        WeatherSetting.validate(
            in.intervalMinutes(), in.forecastDays(), in.failureThreshold(), in.cooldownMinutes()));
    WeatherSetting before = runner.setting();
    boolean changed =
        before.enabled() != in.enabled()
            || before.intervalMinutes() != in.intervalMinutes()
            || before.forecastDays() != in.forecastDays()
            || before.failureThreshold() != in.failureThreshold()
            || before.cooldownMinutes() != in.cooldownMinutes();
    if (!changed) {
      WeatherSettingDto same = WeatherSettingDto.from(before);
      return new WeatherSettingChangeDto(same, same, false);
    }
    WeatherSetting after =
        settings.save(
            new WeatherSetting(
                in.enabled(),
                in.intervalMinutes(),
                in.forecastDays(),
                in.failureThreshold(),
                in.cooldownMinutes(),
                adminId,
                clock.instant()));
    return new WeatherSettingChangeDto(
        WeatherSettingDto.from(before), WeatherSettingDto.from(after), true);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WeatherProviderDto> providers() {
    Map<String, Long> used = runner.monthUsage();
    Instant now = clock.instant();
    return providers.findAll().stream().map(p -> view(p, used, now)).toList();
  }

  @Override
  @Transactional
  public WeatherProviderChangeDto patchProvider(
      long adminId, String providerCode, PatchWeatherProviderDto patch) {
    if (patch == null) {
      throw BusinessException.validation(FieldError.invalidType("body", "object"));
    }
    WeatherProvider current = provider(providerCode);
    List<FieldError> errors = new ArrayList<>();
    final Boolean enabled = patch.enabled().apply("enabled", current.enabled(), errors);
    final Integer priority = patch.priority().apply("priority", current.priority(), errors);
    final Integer quota =
        patch.monthlyQuota().apply("monthlyQuota", current.monthlyQuota(), errors);
    final Integer days =
        patch.maxForecastDays().apply("maxForecastDays", current.maxForecastDays(), errors);
    if (enabled == null) {
      errors.add(FieldError.required("enabled"));
    }
    if (priority == null) {
      errors.add(FieldError.required("priority"));
    }
    if (days == null) {
      errors.add(FieldError.required("maxForecastDays"));
    }
    BusinessException.throwIfAny(errors);
    BusinessException.throwIfAny(WeatherProvider.validate(priority, quota, days));
    Map<String, Long> used = runner.monthUsage();
    Instant now = clock.instant();
    WeatherProviderDto before = view(current, used, now);
    WeatherProvider saved =
        providers.saveConfig(current.configured(enabled, priority, quota, days, adminId, now));
    return new WeatherProviderChangeDto(before, view(saved, used, now));
  }

  @Override
  @Transactional
  public WeatherProviderChangeDto resetProvider(long adminId, String providerCode) {
    WeatherProvider current = provider(providerCode);
    Map<String, Long> used = runner.monthUsage();
    Instant now = clock.instant();
    WeatherProviderDto before = view(current, used, now);
    providers.saveHealth(current.code(), current.health().reset());
    return new WeatherProviderChangeDto(before, view(provider(providerCode), used, now));
  }

  /** 트랜잭션 없이: 외부 HTTP 동안 DB 연결을 잡지 않는다 */
  @Override
  public ProviderTestResultDto testProvider(long adminId, String providerCode, String regionCode) {
    WeatherProvider p = provider(providerCode);
    WeatherRegion region = region(regionCode);
    KeyState key = sources.keyState(p);
    if (!key.callable()) {
      // 관리자 API라 키 이름(서버 설정 이름)은 알려 준다. reason: KEY_MISSING(키 없음) · NOT_IMPLEMENTED(서버에 구현 없음)
      throw new BusinessException(
          ErrorCode.WEATHER_NO_PROVIDER,
          key == KeyState.MISSING
              ? Map.of("reason", "KEY_MISSING", "keyName", p.keyName())
              : Map.of("reason", "NOT_IMPLEMENTED"));
    }
    Instant now = clock.instant();
    RefreshRun run = runs.create(RefreshRun.start(RunTrigger.TEST, adminId, region.code(), now));
    FetchResult r = runner.fetch(p, region, runner.setting().daysFor(p));
    runs.saveCalls(run.runId(), r.calls());
    runs.update(run.finish(r.ok() ? 1 : 0, r.ok() ? 0 : 1, 0, clock.instant()));
    return new ProviderTestResultDto(
        run.runId(),
        p.code(),
        region.code(),
        r.ok(),
        r.errorCode(),
        r.errorMessage(),
        r.lastHttpStatus(),
        r.durationMs(),
        r.ok() ? r.report().locationName() : null,
        r.ok() ? CurrentWeatherDto.from(r.report().current()) : null,
        r.ok() ? DailyForecastDto.from(r.report().days()) : List.of(),
        r.ok() ? r.report().raw() : Map.of());
  }

  /** 트랜잭션 없이 잠금을 잡고 실행 행을 만든 뒤 실행은 다른 스레드에 넘긴다. 잠금은 실행이 끝나면(실패해도) 푼다. */
  @Override
  public long requestRefresh(long adminId, String regionCode) {
    List<WeatherRegion> regions =
        regionCode == null || regionCode.isBlank()
            ? runner.activeRegions()
            : List.of(region(regionCode));
    if (!runner.anyUsable()) {
      throw new BusinessException(ErrorCode.WEATHER_NO_PROVIDER);
    }
    String owner = UUID.randomUUID().toString();
    if (!lock.tryAcquire(owner, WeatherOptions.LOCK_TTL)) {
      throw new BusinessException(ErrorCode.WEATHER_REFRESH_RUNNING);
    }
    RefreshRun run;
    try {
      String single = regions.size() == 1 && regionCode != null ? regions.getFirst().code() : null;
      run = runs.create(RefreshRun.start(RunTrigger.MANUAL, adminId, single, clock.instant()));
    } catch (RuntimeException e) {
      lock.release(owner);
      throw e;
    }
    try {
      executor.submit(
          () -> {
            try {
              runner.executeSafely(run, regions);
            } finally {
              lock.release(owner);
            }
          });
    } catch (RuntimeException e) {
      runner.executeSafely(run, List.of());
      lock.release(owner);
      throw e;
    }
    return run.runId();
  }

  @Override
  @Transactional(readOnly = true)
  public PageResultDto<RefreshRunDto> runs(RunSearchDto search, int limit, long offset) {
    RunFilter filter = runFilter(null, search);
    return new PageResultDto<>(
        logs.findRuns(filter, limit, offset).stream().map(WeatherAdminService::toRunDto).toList(),
        logs.countRuns(filter));
  }

  @Override
  @Transactional(readOnly = true)
  public RefreshRunDetailDto run(long runId) {
    RefreshRunDto run = runRow(runId).orElseThrow(BusinessException::notFound);
    List<WeatherCallDto> calls =
        new ArrayList<>(
            logs
                .findCalls(
                    new CallFilter(runId, null, null, null, null, null, null, null),
                    RUN_CALLS_LIMIT,
                    0)
                .stream()
                .map(WeatherAdminService::call)
                .toList());
    // 목록은 최신순이라 상세는 호출한 순서로 뒤집는다
    Collections.reverse(calls);
    return new RefreshRunDetailDto(run, calls);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResultDto<WeatherCallDto> calls(CallSearchDto s, int limit, long offset) {
    CallFilter filter =
        new CallFilter(
            null,
            s.providerCode(),
            s.regionCode(),
            s.success(),
            s.fallback(),
            s.errorCode(),
            s.from(),
            s.to());
    return new PageResultDto<>(
        logs.findCalls(filter, limit, offset).stream().map(WeatherAdminService::call).toList(),
        logs.countCalls(filter));
  }

  @Override
  @Transactional(readOnly = true)
  public WeatherCallSummaryDto callSummary() {
    LocalDate today = Zones.today(clock);
    CallSummaryRowDto t = logs.summary(Zones.startOf(today), Zones.startOf(today.plusDays(1)));
    Map<String, Long> used = runner.monthUsage();
    List<MonthUsageDto> months =
        providers.findAll().stream()
            .map(
                p ->
                    new MonthUsageDto(
                        p.code(),
                        p.displayName(),
                        used.getOrDefault(p.code(), 0L),
                        p.monthlyQuota()))
            .toList();
    return new WeatherCallSummaryDto(
        t.calls(), t.failures(), t.fallbacks(), t.avgDurationMs(), months);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WeatherSnapshotDto> snapshots() {
    WeatherSetting setting = runner.setting();
    Map<String, WeatherSnapshot> saved =
        snapshots.findAll().stream()
            .collect(Collectors.toMap(WeatherSnapshot::regionCode, Function.identity()));
    Instant now = clock.instant();
    return WeatherRegion.all(catalog.current()).stream()
        .map(r -> toSnapshotDto(r, saved.get(r.code()), setting, now, false))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public WeatherSnapshotDto snapshot(String regionCode) {
    WeatherRegion region =
        WeatherRegion.find(catalog.current(), regionCode).orElseThrow(BusinessException::notFound);
    return toSnapshotDto(
        region,
        snapshots.find(region.code()).orElse(null),
        runner.setting(),
        clock.instant(),
        true);
  }

  private WeatherProvider provider(String code) {
    return providers.find(code).orElseThrow(BusinessException::notFound);
  }

  private WeatherRegion region(String code) {
    if (code == null || code.isBlank()) {
      throw BusinessException.validation(FieldError.required("regionCode"));
    }
    return WeatherRegion.find(catalog.current(), code.strip())
        .orElseThrow(() -> BusinessException.validation("regionCode", FieldErrorCode.NOT_FOUND));
  }

  private WeatherProviderDto view(WeatherProvider p, Map<String, Long> used, Instant now) {
    long u = used.getOrDefault(p.code(), 0L);
    int calls = sources.callsPerFetch(p.code());
    KeyState key = sources.keyState(p);
    return new WeatherProviderDto(
        p.code(),
        p.displayName(),
        p.enabled(),
        p.priority(),
        p.keyName(),
        key.name(),
        p.health().status(now).name(),
        p.health().consecutiveFailures(),
        p.health().circuitOpenUntil(),
        p.health().lastSuccessAt(),
        p.health().lastFailureAt(),
        p.health().lastError(),
        u,
        p.monthlyQuota(),
        p.maxForecastDays(),
        calls,
        p.usable(key.callable(), now, u, calls),
        p.updatedBy(),
        p.updatedAt());
  }

  private Optional<RefreshRunDto> runRow(long runId) {
    RunFilter filter = new RunFilter(runId, null, null);
    return logs.findRuns(filter, 1, 0).stream().findFirst().map(WeatherAdminService::toRunDto);
  }

  private static RunFilter runFilter(Long runId, RunSearchDto search) {
    return new RunFilter(
        runId,
        search == null ? null : name("trigger", search.trigger(), RunTrigger.class),
        search == null ? null : name("status", search.status(), RunStatus.class));
  }

  /** 열거형 이름 검증 (대소문자 무시). 비었으면 null, 틀리면 400 */
  private static <E extends Enum<E>> String name(String field, String raw, Class<E> type) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String v = raw.strip().toUpperCase(Locale.ROOT);
    for (E e : type.getEnumConstants()) {
      if (e.name().equals(v)) {
        return v;
      }
    }
    throw BusinessException.validation(
        FieldError.notAllowed(
            field, Arrays.stream(type.getEnumConstants()).map(Enum::name).toList()));
  }

  private static WeatherSnapshotDto toSnapshotDto(
      WeatherRegion region, WeatherSnapshot s, WeatherSetting setting, Instant now, boolean raw) {
    if (s == null) {
      return new WeatherSnapshotDto(
          region.code(),
          region.label(),
          region.active(),
          null,
          null,
          List.of(),
          null,
          null,
          true,
          null,
          null,
          raw ? Map.of() : null);
    }
    return new WeatherSnapshotDto(
        region.code(),
        region.label(),
        region.active(),
        s.providerCode(),
        CurrentWeatherDto.from(s.current()),
        DailyForecastDto.from(s.days()),
        s.locationName(),
        s.fetchedAt(),
        setting.stale(s.fetchedAt(), now),
        s.lastError(),
        s.lastErrorAt(),
        raw ? s.raw() : null);
  }

  private static RefreshRunDto toRunDto(RunRowDto r) {
    return new RefreshRunDto(
        r.runId(),
        r.triggerType(),
        r.requestedBy(),
        r.requestedByEmail(),
        r.regionCode(),
        r.status(),
        r.skipReason(),
        r.successCount(),
        r.failCount(),
        r.fallbackCount(),
        r.startedAt(),
        r.finishedAt());
  }

  private static WeatherCallDto call(CallRowDto r) {
    return new WeatherCallDto(
        r.callId(),
        r.runId(),
        r.triggerType(),
        r.regionCode(),
        r.providerCode(),
        r.callKind(),
        r.fallback(),
        r.httpStatus(),
        r.success(),
        r.errorCode(),
        r.errorMessage(),
        r.durationMs(),
        r.responseBytes(),
        r.calledAt());
  }

  private static void required(List<FieldError> errors, String field, Object value) {
    if (Objects.isNull(value)) {
      errors.add(FieldError.required(field));
    }
  }
}
