package app.wearly.notify.application.service;

import app.wearly.common.domain.Zones;
import app.wearly.notify.application.port.in.DayBeforeNotifyUseCase;
import app.wearly.notify.application.port.in.dto.DayBeforeRunDto;
import app.wearly.notify.application.port.out.PushMessage;
import app.wearly.notify.application.port.out.PushResult;
import app.wearly.notify.application.port.out.PushSendLogPort;
import app.wearly.notify.application.port.out.PushSenderPort;
import app.wearly.notify.application.port.out.PushTokenPort;
import app.wearly.notify.application.port.out.PushTokenQueryPort;
import app.wearly.notify.application.port.out.dto.PushTargetRowDto;
import app.wearly.notify.domain.DayBeforeMessage;
import app.wearly.notify.domain.DayBeforeWeather;
import app.wearly.notify.domain.PushSettings;
import app.wearly.plan.application.port.in.PlanReminderUseCase;
import app.wearly.plan.application.port.in.dto.PlanPlaceDto;
import app.wearly.plan.application.port.in.dto.PlanReminderDto;
import app.wearly.plan.application.port.in.dto.PlanWeatherDto;
import app.wearly.user.application.port.in.NotifySettingUseCase;
import app.wearly.weather.application.port.in.PlaceWeatherUseCase;
import app.wearly.weather.application.port.in.dto.WeatherDayDto;
import app.wearly.weather.application.port.in.dto.WeatherDayQueryDto;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 예정 코디 D-1 알림. 순서: 내일 예정 → 알림을 켠 회원 → 그 회원의 토큰 → 예정마다 오늘 보낼 차례 잡기(발송 기록) → 장소 날씨 다시 확인(기존
 * /weather/day 로직) → 문구 → 한 번에 보내기(어댑터가 100개씩 나눈다) → DeviceNotRegistered 토큰 지우기 → 모든 기기에 실패한 예정은
 * 기록을 푼다.
 *
 * <p>외부 호출(날씨·푸시)이 길 수 있어 전체를 한 트랜잭션으로 묶지 않는다. 각 포트·유스케이스 호출이 자기 트랜잭션을 쓴다. 로그에는 예정 제목·토큰 같은 입력 값을
 * 남기지 않고 개수만 남긴다.
 */
@Service
public class DayBeforeNotifyService implements DayBeforeNotifyUseCase {

  private static final Logger log = LoggerFactory.getLogger(DayBeforeNotifyService.class);

  /** 발송 기록 보관 기간 */
  static final Duration LOG_RETENTION = Duration.ofDays(30);

  /** 알림 data.type (앱이 눌렀을 때 예정 PLAN 모드를 연다) */
  public static final String DATA_TYPE = "plan-d1";

  private final PlanReminderUseCase reminders;
  private final NotifySettingUseCase notifySettings;
  private final PlaceWeatherUseCase weather;
  private final PushTokenQueryPort tokenQueries;
  private final PushTokenPort tokens;
  private final PushSendLogPort sendLog;
  private final PushSenderPort sender;
  private final PushSettings settings;
  private final Clock clock;

  public DayBeforeNotifyService(
      PlanReminderUseCase reminders,
      NotifySettingUseCase notifySettings,
      PlaceWeatherUseCase weather,
      PushTokenQueryPort tokenQueries,
      PushTokenPort tokens,
      PushSendLogPort sendLog,
      PushSenderPort sender,
      PushSettings settings,
      Clock clock) {
    this.reminders = reminders;
    this.notifySettings = notifySettings;
    this.weather = weather;
    this.tokenQueries = tokenQueries;
    this.tokens = tokens;
    this.sendLog = sendLog;
    this.sender = sender;
    this.settings = settings;
    this.clock = clock;
  }

  @Override
  public DayBeforeRunDto runDayBefore() {
    return runDayBefore(Zones.today(clock));
  }

  @Override
  public DayBeforeRunDto runDayBefore(LocalDate today) {
    LocalDate tomorrow = today.plusDays(1);
    if (!settings.enabled()) {
      return DayBeforeRunDto.off(tomorrow);
    }
    List<PlanReminderDto> plans = reminders.plansOn(tomorrow);
    Set<Long> userIds = new LinkedHashSet<>();
    plans.forEach(p -> userIds.add(p.userId()));
    Set<Long> enabled = userIds.isEmpty() ? Set.of() : notifySettings.dayBeforeEnabled(userIds);
    Map<Long, List<PushTargetRowDto>> targets = new HashMap<>();
    if (!enabled.isEmpty()) {
      for (PushTargetRowDto t : tokenQueries.findByUsers(enabled)) {
        targets.computeIfAbsent(t.userId(), k -> new ArrayList<>()).add(t);
      }
    }

    int skipped = 0;
    int alreadySent = 0;
    List<Claimed> claimed = new ArrayList<>();
    List<PushMessage> messages = new ArrayList<>();
    Map<String, Optional<DayBeforeWeather>> weatherCache = new HashMap<>();
    try {
      for (PlanReminderDto plan : plans) {
        List<PushTargetRowDto> devices = targets.getOrDefault(plan.userId(), List.of());
        if (!enabled.contains(plan.userId()) || devices.isEmpty()) {
          skipped++;
          continue;
        }
        if (!sendLog.claim(plan.id(), plan.userId(), plan.date(), today)) {
          alreadySent++;
          continue;
        }
        DayBeforeWeather saved = savedWeather(plan);
        DayBeforeWeather fresh = recheck(plan, saved, weatherCache);
        DayBeforeMessage message =
            DayBeforeMessage.of(plan.title(), placeLabel(plan.place()), saved, fresh);
        int from = messages.size();
        for (PushTargetRowDto device : devices) {
          messages.add(
              new PushMessage(
                  device.token(), message.title(), message.body(), data(plan, message)));
        }
        claimed.add(new Claimed(plan, message.changed(), from, messages.size()));
      }
    } catch (RuntimeException e) {
      releaseAll(claimed, today);
      throw e;
    }

    List<PushResult> results;
    try {
      results = messages.isEmpty() ? List.of() : sender.send(messages);
    } catch (RuntimeException e) {
      releaseAll(claimed, today);
      throw e;
    }

    int notified = 0;
    int changed = 0;
    int failed = 0;
    Set<String> gone = new LinkedHashSet<>();
    for (Claimed c : claimed) {
      boolean anyOk = false;
      for (int i = c.from(); i < c.to(); i++) {
        PushResult r = i < results.size() ? results.get(i) : PushResult.failed("NO_RESULT");
        if (r.status() == PushResult.Status.OK) {
          anyOk = true;
        } else if (r.status() == PushResult.Status.DEVICE_NOT_REGISTERED) {
          gone.add(messages.get(i).token());
        }
      }
      if (anyOk) {
        notified++;
        if (c.changed()) {
          changed++;
        }
      } else {
        failed++;
        sendLog.release(c.plan().id(), today);
      }
    }
    int removed = gone.isEmpty() ? 0 : tokens.deleteTokens(gone);
    int purged = sendLog.purgeBefore(clock.instant().minus(LOG_RETENTION));

    DayBeforeRunDto out =
        new DayBeforeRunDto(
            tomorrow,
            plans.size(),
            notified,
            changed,
            alreadySent,
            skipped,
            failed,
            messages.size(),
            removed,
            false);
    logSummary(out, purged);
    return out;
  }

  private void releaseAll(List<Claimed> claimed, LocalDate today) {
    for (Claimed c : claimed) {
      try {
        sendLog.release(c.plan().id(), today);
      } catch (RuntimeException e) {
        // 기록을 못 풀면 오늘은 다시 보내지 않는다 (중복 발송보다 낫다)
        log.warn("D-1 알림 기록을 풀지 못했어요 ({})", e.getClass().getSimpleName());
      }
    }
  }

  /** 저장된 날씨. 출처가 없으면(예전 앱 데이터) manual이면 MANUAL, 아니면 FORECAST (앱 planSource와 같다) */
  static DayBeforeWeather savedWeather(PlanReminderDto plan) {
    PlanWeatherDto w = plan.weather();
    String source = plan.weatherSource();
    if (source == null) {
      source =
          Boolean.TRUE.equals(w.manual()) ? DayBeforeWeather.MANUAL : DayBeforeWeather.FORECAST;
    }
    return new DayBeforeWeather(
        w.temp() == null ? 0 : w.temp(), w.rainProb() == null ? 0 : w.rainProb(), source);
  }

  /**
   * 장소 날씨를 다시 확인한다 (GET /weather/day와 같은 유스케이스). 직접 고른 날씨(MANUAL)와 장소가 없는 예정(앱의 "내 지역", 서버는 그 지역을
   * 모른다)은 확인하지 않는다. 실패하면(한도·공급자 없음 등) null → 저장된 날씨로 보낸다. 같은 장소는 한 번만 묻는다.
   */
  private DayBeforeWeather recheck(
      PlanReminderDto plan, DayBeforeWeather saved, Map<String, Optional<DayBeforeWeather>> cache) {
    PlanPlaceDto place = plan.place();
    if (DayBeforeWeather.MANUAL.equals(saved.source()) || place == null) {
      return null;
    }
    WeatherDayQueryDto query =
        place.regionCode() != null
            ? new WeatherDayQueryDto(
                place.regionCode(), null, null, plan.date().toString(), plan.userId())
            : new WeatherDayQueryDto(
                null, place.lat(), place.lon(), plan.date().toString(), plan.userId());
    String key =
        place.regionCode() != null
            ? "R:" + place.regionCode()
            : "P:" + place.lat() + "," + place.lon() + ":" + plan.userId();
    return cache.computeIfAbsent(key, k -> fetch(query)).orElse(null);
  }

  private Optional<DayBeforeWeather> fetch(WeatherDayQueryDto query) {
    try {
      WeatherDayDto day = weather.day(query);
      return Optional.of(
          new DayBeforeWeather(day.day().temp(), day.day().rainProb(), day.source()));
    } catch (RuntimeException e) {
      logRecheckFailure(e);
      return Optional.empty();
    }
  }

  private static void logRecheckFailure(RuntimeException e) {
    log.info("D-1 알림: 날씨를 다시 확인하지 못해 저장된 날씨로 보내요 ({})", e.getClass().getSimpleName());
  }

  private static String placeLabel(PlanPlaceDto place) {
    return place == null ? null : place.label();
  }

  /** 앱이 알림을 눌렀을 때 쓰는 값. planId는 기기 예정 ID(clientId, 없으면 srv-서버ID) */
  static Map<String, String> data(PlanReminderDto plan, DayBeforeMessage message) {
    Map<String, String> data = new LinkedHashMap<>();
    data.put("type", DATA_TYPE);
    data.put("planId", plan.clientId() != null ? plan.clientId() : "srv-" + plan.id());
    data.put("serverId", String.valueOf(plan.id()));
    data.put("date", plan.date().toString());
    data.put("changed", message.changed() ? "1" : "0");
    return data;
  }

  private static void logSummary(DayBeforeRunDto r, int purged) {
    log.info(
        "D-1 알림 {}: 예정 {}개, 보냄 {}개(예보 바뀜 {}), 이미 보냄 {}, 건너뜀 {}, 실패 {}, 푸시 {}건, 지운 토큰 {}, 지운 기록 {}",
        r.planDate(),
        r.plans(),
        r.notified(),
        r.changed(),
        r.alreadySent(),
        r.skipped(),
        r.failed(),
        r.messages(),
        r.removedTokens(),
        purged);
  }

  private record Claimed(PlanReminderDto plan, boolean changed, int from, int to) {}
}
