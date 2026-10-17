package app.wearly.weather.domain;

/** 실행을 건너뛴 이유 */
public enum SkipReason {
  /** 관리자가 스케줄을 껐다 */
  DISABLED,
  /** 부를 수 있는 공급자가 없다 (꺼짐·키 없음·차단·월 한도) */
  NO_PROVIDER,
  /** 다른 실행이 아직 돌고 있다 */
  LOCKED
}
