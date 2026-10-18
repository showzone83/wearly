package app.wearly.admin.domain;

import java.util.List;

/** 변경 이력(tb_admin_audit_log)의 targetType (07 문서 4장) */
public final class AuditTargets {

  public static final String CODE = "CODE";
  public static final String AI_PROVIDER = "AI_PROVIDER";
  public static final String USER = "USER";

  /** 앱 설정 (12 문서 3장). targetId는 설정 키 (예: theme) */
  public static final String APP_SETTING = "APP_SETTING";

  /** 로그인 세션 (16 문서 4장). targetId는 세션 ID. 회원 전체 끊기는 USER(targetId = 회원 ID) */
  public static final String SESSION = "SESSION";

  /** 날씨 스케줄 설정 (17 문서 6장). targetId는 default */
  public static final String WEATHER_SETTING = "WEATHER_SETTING";

  /** 날씨 공급자 설정·차단 해제. targetId는 공급자 코드 (WEATHERAPI) */
  public static final String WEATHER_PROVIDER = "WEATHER_PROVIDER";

  /** 날씨 지금 실행. targetId는 실행 ID */
  public static final String WEATHER_REFRESH = "WEATHER_REFRESH";

  /** 허용 값 (오류 응답 params.allowed에도 이 순서로 쓴다) */
  public static final List<String> ALL =
      List.of(
          CODE,
          AI_PROVIDER,
          USER,
          APP_SETTING,
          SESSION,
          WEATHER_SETTING,
          WEATHER_PROVIDER,
          WEATHER_REFRESH);

  private AuditTargets() {}
}
