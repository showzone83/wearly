package app.wearly.weather.domain;

/** 공급자 키 상태 (관리자 화면). 키 값은 어디에도 내보내지 않는다 */
public enum KeyState {
  /** 서버 설정(wearly.weather.keys.&lt;키 이름&gt;) 또는 같은 이름의 OS 환경변수에 키가 있다 */
  CONFIGURED,
  /** 키가 없다. 이 공급자는 건너뛴다 */
  MISSING,
  /** 목업 실행 (WEARLY_WEATHER_PROVIDER=mock). 키 없이 목업 날씨를 준다 */
  MOCK,
  /** 서버에 이 공급자 구현이 없다 */
  UNSUPPORTED;

  public boolean callable() {
    return this == CONFIGURED || this == MOCK;
  }
}
