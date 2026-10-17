package app.wearly.admin.domain;

import java.util.Set;

/** 변경 이력(tb_admin_audit_log)의 targetType (07 문서 4장) */
public final class AuditTargets {

  public static final String CODE = "CODE";
  public static final String AI_PROVIDER = "AI_PROVIDER";
  public static final String USER = "USER";

  /** 앱 설정 (12 문서 3장). targetId는 설정 키 (예: theme) */
  public static final String APP_SETTING = "APP_SETTING";

  public static final Set<String> ALL = Set.of(CODE, AI_PROVIDER, USER, APP_SETTING);

  private AuditTargets() {}
}
