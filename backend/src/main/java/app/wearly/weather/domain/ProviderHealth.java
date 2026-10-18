package app.wearly.weather.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 공급자 상태 (tb_weather_provider의 상태 칸). 실행이 바꾸고 관리자는 "차단 해제"로만 되돌린다 (17 문서 4장).
 *
 * <ul>
 *   <li>정상: 실패하면 연속 실패 수를 더하고, 기준에 닿으면 차단(쿨다운)한다
 *   <li>차단(FAILURES): 쿨다운 동안 건너뛴다. 끝나면 시험 상태가 되어 다음 실행의 첫 지역으로 한 번 부른다
 *   <li>시험: 성공하면 정상, 실패하면 바로 다시 차단
 *   <li>키 문제(AUTH): 401·403. 쿨다운으로 낫지 않으므로 관리자가 키를 고치고 차단 해제할 때까지 건너뛴다
 * </ul>
 *
 * @param circuitReason null(정상) · FAILURES · AUTH
 */
public record ProviderHealth(
    int consecutiveFailures,
    String circuitReason,
    Instant circuitOpenUntil,
    Instant lastSuccessAt,
    Instant lastFailureAt,
    String lastError) {

  public static final String FAILURES = "FAILURES";
  public static final String AUTH = "AUTH";

  public static ProviderHealth fresh() {
    return new ProviderHealth(0, null, null, null, null, null);
  }

  public ProviderStatus status(Instant now) {
    if (AUTH.equals(circuitReason)) {
      return ProviderStatus.KEY_INVALID;
    }
    if (FAILURES.equals(circuitReason)) {
      return circuitOpenUntil != null && circuitOpenUntil.isAfter(now)
          ? ProviderStatus.OPEN
          : ProviderStatus.HALF_OPEN;
    }
    return ProviderStatus.OK;
  }

  public ProviderHealth succeeded(Instant now) {
    return new ProviderHealth(0, null, null, now, lastFailureAt, lastError);
  }

  /**
   * @param errorCode 호출 오류 코드 (AUTH면 키 문제로 바로 막는다)
   * @param message 키를 지운 오류 문구
   */
  public ProviderHealth failed(
      Instant now, String errorCode, String message, int threshold, Duration cooldown) {
    String error = message == null ? errorCode : message;
    int failures = consecutiveFailures + 1;
    if (FetchErrors.AUTH.equals(errorCode)) {
      return new ProviderHealth(failures, AUTH, null, lastSuccessAt, now, error);
    }
    boolean trialFailed = status(now) == ProviderStatus.HALF_OPEN;
    if (trialFailed || failures >= threshold) {
      return new ProviderHealth(failures, FAILURES, now.plus(cooldown), lastSuccessAt, now, error);
    }
    return new ProviderHealth(failures, null, null, lastSuccessAt, now, error);
  }

  /** 관리자 "차단 해제": 연속 실패와 차단을 비운다 (마지막 성공·실패 기록은 둔다) */
  public ProviderHealth reset() {
    return new ProviderHealth(0, null, null, lastSuccessAt, lastFailureAt, lastError);
  }
}
