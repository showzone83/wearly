package app.wearly.weather.application.port.out;

import app.wearly.weather.application.port.out.dto.CallRowDto;
import app.wearly.weather.application.port.out.dto.CallSummaryRowDto;
import app.wearly.weather.application.port.out.dto.HistoryRowDto;
import app.wearly.weather.application.port.out.dto.ProviderUsageRowDto;
import app.wearly.weather.application.port.out.dto.RunRowDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 실행·호출 이력과 날씨 이력 목록, 집계 (MyBatis). 최신순 */
public interface WeatherLogQueryPort {

  /** 실행 목록 조건. null이면 거르지 않는다 */
  record RunFilter(Long runId, String trigger, String status) {}

  /** 호출 목록 조건. null이면 거르지 않는다. 기간은 [from, to) */
  record CallFilter(
      Long runId,
      String providerCode,
      String regionCode,
      Boolean success,
      Boolean fallback,
      String errorCode,
      Instant from,
      Instant to) {}

  /** 날씨 이력 조건. regionCode가 null이면 모든 지역. 기간은 [from, to] (한국 날짜, 둘 다 포함) */
  record HistoryFilter(String regionCode, LocalDate from, LocalDate to) {}

  List<RunRowDto> findRuns(RunFilter filter, int limit, long offset);

  long countRuns(RunFilter filter);

  List<CallRowDto> findCalls(CallFilter filter, int limit, long offset);

  long countCalls(CallFilter filter);

  /** 공급자별 호출 수 (실패 포함), 기간 [from, to) */
  List<ProviderUsageRowDto> usage(Instant from, Instant to);

  /** 기간 [from, to)의 호출·실패·예비 전환 수와 평균 소요 시간 */
  CallSummaryRowDto summary(Instant from, Instant to);

  /** 날씨 이력 (최신 날짜순, 같은 날짜는 지역 코드순) */
  List<HistoryRowDto> findHistory(HistoryFilter filter, int limit, long offset);

  long countHistory(HistoryFilter filter);
}
