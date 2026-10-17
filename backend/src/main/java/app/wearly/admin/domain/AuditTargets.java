package app.wearly.admin.domain;

import java.util.Set;

/** 변경 이력(tb_admin_audit_log)의 targetType (07 문서 4장) */
public final class AuditTargets {

  public static final String CODE = "CODE";
  public static final String AI_PROVIDER = "AI_PROVIDER";
  public static final String USER = "USER";

  /** 앱 설정 (12 문서 3장). targetId는 설정 키 (예: theme) */
  public static final String APP_SETTING = "APP_SETTING";

  /** 로그인 세션 (16 문서 4장). targetId는 세션 ID. 회원 전체 끊기는 USER(targetId = 회원 ID) */
  public static final String SESSION = "SESSION";

  public static final Set<String> ALL = Set.of(CODE, AI_PROVIDER, USER, APP_SETTING, SESSION);

  /** 허용 값 오류 문구 */
  public static final String NAMES = "CODE, AI_PROVIDER, USER, APP_SETTING, SESSION";

  private AuditTargets() {}
}
