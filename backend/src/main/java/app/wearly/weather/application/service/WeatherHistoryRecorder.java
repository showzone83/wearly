package app.wearly.weather.application.service;

import app.wearly.weather.application.port.out.WeatherHistoryPort;
import app.wearly.weather.domain.WeatherHistory;
import app.wearly.weather.domain.WeatherReport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 받은 날씨를 지역·날짜별 이력에 남긴다 (17 문서 2장). 실행이 저장값(스냅샷)을 바꾼 바로 뒤에 부른다.
 *
 * <ul>
 *   <li>예보: 받은 예보 중 오늘(한국 날짜) 칸으로 오늘 행의 예보를 덮어쓴다. 그날 마지막으로 받은 예보가 남는다
 *   <li>실측: 현재 날씨를 관측 시각이 속한 날짜 행에 더한다 (이미 더한 관측은 건너뛴다)
 * </ul>
 *
 * <p>이력 저장이 실패해도 저장값과 실행 결과는 그대로다 (경고만 남긴다).
 */
@Service
public class WeatherHistoryRecorder {

  private static final Logger log = LoggerFactory.getLogger(WeatherHistoryRecorder.class);

  private final WeatherHistoryPort histories;

  public WeatherHistoryRecorder(WeatherHistoryPort histories) {
    this.histories = histories;
  }

  /**
   * @param fetchedAt 받은 시각. 예보 날짜(오늘)는 이 시각의 한국 날짜다
   */
  public void record(
      String regionCode, String providerCode, WeatherReport report, Instant fetchedAt) {
    try {
      // 예보 날짜와 관측 날짜가 같으면 한 행을 한 번만 저장한다
      Map<LocalDate, WeatherHistory> rows = new LinkedHashMap<>();
      LocalDate today = WeatherHistory.dateOf(fetchedAt);
      report.days().stream()
          .filter(d -> d.date().equals(today))
          .findFirst()
          .ifPresent(
              d ->
                  rows.put(
                      today,
                      load(rows, regionCode, today).withForecast(providerCode, d, fetchedAt)));
      if (report.current() != null) {
        Instant observedAt = WeatherHistory.observedAt(report.current(), fetchedAt);
        LocalDate day = WeatherHistory.dateOf(observedAt);
        rows.put(day, load(rows, regionCode, day).observe(report.current(), observedAt));
      }
      rows.values().forEach(histories::save);
    } catch (RuntimeException e) {
      log.warn("날씨 이력 {} 저장 실패 ({})", regionCode, e.getClass().getSimpleName(), e);
    }
  }

  private WeatherHistory load(
      Map<LocalDate, WeatherHistory> rows, String regionCode, LocalDate day) {
    WeatherHistory h = rows.get(day);
    return h != null
        ? h
        : histories.find(regionCode, day).orElseGet(() -> WeatherHistory.empty(regionCode, day));
  }
}
