package app.wearly.notify.config;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 알림 설정 (wearly.notify.*, application.yml). 값이 틀리면 서버가 시작하지 않는다.
 *
 * <pre>
 * wearly.notify.push.enabled              서버 푸시 사용 (기본 false)
 * wearly.notify.push.provider             expo | log (기본 log: 보내지 않고 로그만)
 * wearly.notify.push.expo.base-url        Expo 주소 (기본 https://exp.host, 테스트는 가짜 서버)
 * wearly.notify.push.expo.access-token    Expo 액세스 토큰 (enhanced push security를 켰을 때만, 비우면 생략)
 * wearly.notify.push.expo.timeout         읽기 시간 제한 (기본 10초)
 * wearly.notify.day-before.time           D-1 작업 시각 HH:mm (한국 시간, 기본 19:30)
 * wearly.notify.day-before.scheduler-enabled  false면 D-1 작업을 예약하지 않는다 (테스트용, 기본 true)
 * </pre>
 */
@ConfigurationProperties("wearly.notify")
public record NotifyProperties(@DefaultValue Push push, @DefaultValue DayBefore dayBefore) {

  public record Push(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("log") String provider,
      @DefaultValue Expo expo) {

    public Push {
      String p = provider == null ? "" : provider.strip().toLowerCase(Locale.ROOT);
      if (!p.equals("expo") && !p.equals("log")) {
        throw new IllegalArgumentException(
            "wearly.notify.push.provider는 expo 또는 log여야 한다: " + provider);
      }
      provider = p;
    }
  }

  public record Expo(
      @DefaultValue("https://exp.host") String baseUrl,
      @DefaultValue("") String accessToken,
      @DefaultValue("PT10S") Duration timeout) {}

  public record DayBefore(
      @DefaultValue("19:30") LocalTime time, @DefaultValue("true") boolean schedulerEnabled) {}
}
