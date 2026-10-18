package app.wearly.notify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.notify.application.port.in.dto.DayBeforeRunDto;
import app.wearly.notify.application.port.out.PushMessage;
import app.wearly.notify.application.port.out.PushResult;
import app.wearly.notify.application.port.out.PushSendLogPort;
import app.wearly.notify.application.port.out.PushSenderPort;
import app.wearly.notify.application.port.out.PushTokenPort;
import app.wearly.notify.application.port.out.PushTokenQueryPort;
import app.wearly.notify.application.port.out.dto.PushTargetRowDto;
import app.wearly.notify.application.service.DayBeforeNotifyService;
import app.wearly.notify.domain.PushPlatform;
import app.wearly.notify.domain.PushSettings;
import app.wearly.plan.application.port.in.PlanReminderUseCase;
import app.wearly.plan.application.port.in.dto.PlanPlaceDto;
import app.wearly.plan.application.port.in.dto.PlanReminderDto;
import app.wearly.plan.application.port.in.dto.PlanWeatherDto;
import app.wearly.user.application.port.in.NotifySettingUseCase;
import app.wearly.weather.application.port.in.PlaceWeatherUseCase;
import app.wearly.weather.application.port.in.dto.DayWeatherDto;
import app.wearly.weather.application.port.in.dto.PlaceCandidateDto;
import app.wearly.weather.application.port.in.dto.PlaceDto;
import app.wearly.weather.application.port.in.dto.WeatherDayDto;
import app.wearly.weather.application.port.in.dto.WeatherDayQueryDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** D-1 작업 규칙 (가짜 포트): 대상 고르기·날짜 경계·알림 꺼짐·이미 보냄·날씨 차이·DeviceNotRegistered·실패 시 기록 풀기 */
class DayBeforeNotifyServiceTest {

  static final LocalDate TODAY = LocalDate.of(2026, 10, 18);
  static final LocalDate TOMORROW = TODAY.plusDays(1);

  // ── 가짜 포트 ──

  final List<PlanReminderDto> plans = new ArrayList<>();
  final List<LocalDate> askedDates = new ArrayList<>();
  final Set<Long> enabledUsers = new HashSet<>();
  final List<PushTargetRowDto> tokenRows = new ArrayList<>();
  final Set<String> deletedTokens = new HashSet<>();
  final Set<String> sentLog = new HashSet<>();
  final List<WeatherDayQueryDto> weatherQueries = new ArrayList<>();
  Function<WeatherDayQueryDto, WeatherDayDto> weatherAnswer = q -> forecast(18, 10);
  Function<PushMessage, PushResult> sendAnswer = m -> PushResult.ok();
  final List<PushMessage> sent = new ArrayList<>();

  PlanReminderUseCase reminders =
      date -> {
        askedDates.add(date);
        return plans.stream().filter(p -> p.date().equals(date)).toList();
      };

  NotifySettingUseCase notifySettings =
      new NotifySettingUseCase() {
        @Override
        public Set<Long> dayBeforeEnabled(Collection<Long> userIds) {
          Set<Long> out = new HashSet<>(userIds);
          out.retainAll(enabledUsers);
          return out;
        }
      };

  PlaceWeatherUseCase weather =
      new PlaceWeatherUseCase() {
        @Override
        public WeatherDayDto day(WeatherDayQueryDto query) {
          weatherQueries.add(query);
          return weatherAnswer.apply(query);
        }

        @Override
        public List<PlaceCandidateDto> searchPlaces(long userId, String query) {
          return List.of();
        }
      };

  PushTokenQueryPort tokenQueries =
      userIds -> tokenRows.stream().filter(t -> userIds.contains(t.userId())).toList();

  PushTokenPort tokens =
      new PushTokenPort() {
        @Override
        public void upsert(
            long userId, String deviceId, PushPlatform platform, String token, Instant now) {}

        @Override
        public void delete(long userId, String deviceId) {}

        @Override
        public int deleteTokens(Collection<String> t) {
          deletedTokens.addAll(t);
          tokenRows.removeIf(r -> t.contains(r.token()));
          return t.size();
        }
      };

  PushSendLogPort sendLog =
      new PushSendLogPort() {
        @Override
        public boolean claim(long planId, long userId, LocalDate plannedDate, LocalDate sendDate) {
          return sentLog.add(planId + "@" + sendDate);
        }

        @Override
        public void release(long planId, LocalDate sendDate) {
          sentLog.remove(planId + "@" + sendDate);
        }

        @Override
        public int purgeBefore(Instant cutoff) {
          return 0;
        }
      };

  PushSenderPort sender =
      messages -> {
        sent.addAll(messages);
        return messages.stream().map(sendAnswer).toList();
      };

  DayBeforeNotifyService service(boolean enabled, Clock clock) {
    return new DayBeforeNotifyService(
        reminders,
        notifySettings,
        weather,
        tokenQueries,
        tokens,
        sendLog,
        sender,
        new PushSettings(enabled, PushSettings.Provider.EXPO, LocalTime.of(19, 30)),
        clock);
  }

  DayBeforeNotifyService service() {
    return service(true, Clock.fixed(Instant.parse("2026-10-18T10:30:00Z"), ZoneOffset.UTC));
  }

  static PlanReminderDto plan(
      long id, long userId, String clientId, PlanPlaceDto place, double temp, String source) {
    return new PlanReminderDto(
        id,
        userId,
        clientId,
        TOMORROW,
        "PM",
        "결혼식 하객",
        place,
        new PlanWeatherDto(temp, temp - 4, temp + 4, 10.0, "CLOUDY", "MANUAL".equals(source)),
        source);
  }

  static final PlanPlaceDto TOKYO = new PlanPlaceDto(null, "도쿄", 35.68, 139.69);
  static final PlanPlaceDto BUSAN = new PlanPlaceDto("BUSAN", "부산", 35.18, 129.08);

  static WeatherDayDto forecast(double temp, int rain) {
    return new WeatherDayDto(
        new PlaceDto(null, "Tokyo", 35.7, 139.7),
        TOMORROW,
        WeatherDayDto.FORECAST,
        new DayWeatherDto(temp, temp - 4, temp + 4, rain, "CLOUDY", null),
        TOMORROW.plusDays(1),
        Instant.parse("2026-10-18T09:00:00Z"),
        null);
  }

  void token(long userId, String device, String token) {
    tokenRows.add(new PushTargetRowDto(userId, device, "IOS", token));
  }

  // ── 테스트 ──

  @Test
  void 서버_푸시가_꺼져_있으면_아무것도_묻지_않는다() {
    plans.add(plan(1, 10, "c1", TOKYO, 18, "FORECAST"));
    DayBeforeRunDto r =
        service(false, Clock.fixed(Instant.parse("2026-10-18T10:30:00Z"), ZoneOffset.UTC))
            .runDayBefore();
    assertThat(r.disabled()).isTrue();
    assertThat(askedDates).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  void 내일은_한국_날짜로_정한다() {
    // 2026-10-18 23:59:59 KST → 내일 10-19
    service(true, Clock.fixed(Instant.parse("2026-10-18T14:59:59Z"), ZoneOffset.UTC))
        .runDayBefore();
    // 2026-10-19 00:00:00 KST (UTC로는 아직 10-18) → 내일 10-20
    service(true, Clock.fixed(Instant.parse("2026-10-18T15:00:00Z"), ZoneOffset.UTC))
        .runDayBefore();
    assertThat(askedDates).containsExactly(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20));
  }

  @Test
  void 알림을_켠_회원의_모든_기기에_보내고_꺼졌거나_토큰이_없으면_건너뛴다() {
    plans.add(plan(1, 10, "c1", TOKYO, 18, "FORECAST"));
    plans.add(plan(2, 20, "c2", TOKYO, 18, "FORECAST")); // 알림 끔
    plans.add(plan(3, 30, null, TOKYO, 18, "FORECAST")); // 토큰 없음
    plans.add(
        new PlanReminderDto(
            4,
            10,
            "c4",
            TOMORROW.plusDays(1),
            "AM",
            "모레",
            TOKYO,
            new PlanWeatherDto(18.0, 14.0, 22.0, 10.0, "CLOUDY", false),
            "FORECAST")); // 모레 예정
    enabledUsers.addAll(List.of(10L, 30L));
    token(10, "phone", "ExponentPushToken[a]");
    token(10, "tablet", "ExponentPushToken[b]");
    token(20, "phone", "ExponentPushToken[c]");

    DayBeforeRunDto r = service().runDayBefore(TODAY);

    assertThat(r.planDate()).isEqualTo(TOMORROW);
    assertThat(r.plans()).isEqualTo(3);
    assertThat(r.notified()).isEqualTo(1);
    assertThat(r.skipped()).isEqualTo(2);
    assertThat(r.messages()).isEqualTo(2);
    assertThat(sent)
        .extracting(PushMessage::token)
        .containsExactly("ExponentPushToken[a]", "ExponentPushToken[b]");
    PushMessage m = sent.getFirst();
    assertThat(m.title()).isEqualTo("내일 예정 코디");
    assertThat(m.body()).isEqualTo("내일 ‘결혼식 하객’ 예정이에요 · 도쿄 · 예보 18°C");
    assertThat(m.data())
        .containsEntry("type", "plan-d1")
        .containsEntry("planId", "c1")
        .containsEntry("serverId", "1")
        .containsEntry("date", "2026-10-19")
        .containsEntry("changed", "0");
  }

  @Test
  void 같은_예정은_하루에_한_번만_보낸다() {
    plans.add(plan(1, 10, null, TOKYO, 18, "FORECAST"));
    enabledUsers.add(10L);
    token(10, "phone", "ExponentPushToken[a]");

    DayBeforeRunDto first = service().runDayBefore(TODAY);
    DayBeforeRunDto again = service().runDayBefore(TODAY);

    assertThat(first.notified()).isEqualTo(1);
    assertThat(again.notified()).isZero();
    assertThat(again.alreadySent()).isEqualTo(1);
    assertThat(sent).hasSize(1);
    assertThat(sent.getFirst().data()).containsEntry("planId", "srv-1");
  }

  @Test
  void 다시_확인한_날씨가_크게_다르면_예보가_바뀌었어요() {
    plans.add(plan(1, 10, "c1", TOKYO, 14, "CLIMATE"));
    enabledUsers.add(10L);
    token(10, "phone", "ExponentPushToken[a]");
    weatherAnswer = q -> forecast(18, 70);

    DayBeforeRunDto r = service().runDayBefore(TODAY);

    assertThat(r.changed()).isEqualTo(1);
    assertThat(sent.getFirst().title()).isEqualTo("예보가 바뀌었어요");
    assertThat(sent.getFirst().body()).contains("예보 18°C (저장할 때 14°C)", "비 소식이 있어요");
    assertThat(sent.getFirst().data()).containsEntry("changed", "1");
    // 좌표 장소는 그 회원으로 묻는다 (회원별 한도)
    WeatherDayQueryDto q = weatherQueries.getFirst();
    assertThat(q.lat()).isEqualTo(35.68);
    assertThat(q.regionCode()).isNull();
    assertThat(q.userId()).isEqualTo(10L);
    assertThat(q.date()).isEqualTo("2026-10-19");
  }

  @Test
  void 지역_장소는_지역_코드로_묻고_같은_장소는_한_번만_묻는다() {
    plans.add(plan(1, 10, "c1", BUSAN, 18, "FORECAST"));
    plans.add(plan(2, 20, "c2", BUSAN, 18, "FORECAST"));
    enabledUsers.addAll(List.of(10L, 20L));
    token(10, "phone", "ExponentPushToken[a]");
    token(20, "phone", "ExponentPushToken[b]");

    service().runDayBefore(TODAY);

    assertThat(weatherQueries).hasSize(1);
    assertThat(weatherQueries.getFirst().regionCode()).isEqualTo("BUSAN");
    assertThat(weatherQueries.getFirst().lat()).isNull();
  }

  @Test
  void 직접_고른_날씨와_장소가_없는_예정은_다시_확인하지_않고_실패하면_저장값으로_보낸다() {
    plans.add(plan(1, 10, "c1", TOKYO, 21, "MANUAL"));
    plans.add(plan(2, 10, "c2", null, 9.6, "CLIMATE"));
    plans.add(plan(3, 10, "c3", BUSAN, 15, "FORECAST"));
    enabledUsers.add(10L);
    token(10, "phone", "ExponentPushToken[a]");
    weatherAnswer =
        q -> {
          throw new BusinessException(ErrorCode.WEATHER_UNAVAILABLE);
        };

    DayBeforeRunDto r = service().runDayBefore(TODAY);

    assertThat(r.notified()).isEqualTo(3);
    assertThat(weatherQueries).hasSize(1); // 부산만 물었다 (실패)
    assertThat(sent)
        .extracting(PushMessage::body)
        .containsExactly(
            "내일 ‘결혼식 하객’ 예정이에요 · 도쿄 · 직접 입력 21°C",
            "내일 ‘결혼식 하객’ 예정이에요 · 평년 10°C",
            "내일 ‘결혼식 하객’ 예정이에요 · 부산 · 예보 15°C");
  }

  @Test
  void DeviceNotRegistered_토큰은_지우고_모든_기기에_실패한_예정은_기록을_푼다() {
    plans.add(plan(1, 10, "c1", null, 18, "FORECAST"));
    plans.add(plan(2, 20, "c2", null, 18, "FORECAST"));
    enabledUsers.addAll(List.of(10L, 20L));
    token(10, "old", "ExponentPushToken[gone]");
    token(10, "new", "ExponentPushToken[ok]");
    token(20, "phone", "ExponentPushToken[fail]");
    Map<String, PushResult> answers = new HashMap<>();
    answers.put(
        "ExponentPushToken[gone]",
        new PushResult(PushResult.Status.DEVICE_NOT_REGISTERED, "DeviceNotRegistered"));
    answers.put("ExponentPushToken[fail]", PushResult.failed("MessageRateExceeded"));
    sendAnswer = m -> answers.getOrDefault(m.token(), PushResult.ok());

    DayBeforeRunDto r = service().runDayBefore(TODAY);

    assertThat(r.notified()).isEqualTo(1);
    assertThat(r.failed()).isEqualTo(1);
    assertThat(r.removedTokens()).isEqualTo(1);
    assertThat(deletedTokens).containsExactly("ExponentPushToken[gone]");
    // 실패한 예정(2)은 기록을 풀어 같은 날 다시 실행하면 다시 보낸다. 보낸 예정(1)은 다시 보내지 않는다
    assertThat(sentLog).containsExactly("1@" + TODAY);
    sent.clear();
    sendAnswer = m -> PushResult.ok();
    DayBeforeRunDto retry = service().runDayBefore(TODAY);
    assertThat(retry.notified()).isEqualTo(1);
    assertThat(retry.alreadySent()).isEqualTo(1);
    assertThat(sent).extracting(PushMessage::token).containsExactly("ExponentPushToken[fail]");
  }

  @Test
  void 보내기가_예외를_던지면_잡은_기록을_모두_푼다() {
    plans.add(plan(1, 10, "c1", null, 18, "FORECAST"));
    enabledUsers.add(10L);
    token(10, "phone", "ExponentPushToken[a]");
    sendAnswer =
        m -> {
          throw new IllegalStateException("boom");
        };
    // 예외는 스케줄러가 로그로 남긴다
    assertThatThrownBy(() -> service().runDayBefore(TODAY))
        .isInstanceOf(IllegalStateException.class);
    assertThat(sentLog).isEmpty();
  }
}
