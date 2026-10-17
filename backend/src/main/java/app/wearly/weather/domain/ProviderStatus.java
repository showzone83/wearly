package app.wearly.weather.domain;

/** 공급자 상태 (관리자 화면 배지). OPEN·KEY_INVALID면 실행이 건너뛴다 */
public enum ProviderStatus {
  /** 정상 */
  OK,
  /** 연속 실패로 차단 중 (circuitOpenUntil까지) */
  OPEN,
  /** 차단 시간이 끝나 다음 실행에서 한 지역으로 시험한다 */
  HALF_OPEN,
  /** 401·403. 관리자가 키를 고치고 차단 해제해야 한다 */
  KEY_INVALID
}
