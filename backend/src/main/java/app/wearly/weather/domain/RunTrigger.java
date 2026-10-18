package app.wearly.weather.domain;

/** 실행을 시작한 쪽 */
public enum RunTrigger {
  /** 주기 실행 */
  SCHEDULE,
  /** 관리자 "지금 실행" */
  MANUAL,
  /** 서버 시작 직후 (저장된 값이 오래됐을 때만) */
  STARTUP,
  /** 관리자 시험 호출 (저장 값을 바꾸지 않는다) */
  TEST
}
