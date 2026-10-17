package app.wearly.admin.domain;

import java.util.Set;

/** 변경 이력(tb_admin_audit_log)의 targetType (07 문서 4장) */
public final class AuditTargets {

    public static final String CODE = "CODE";
    public static final String AI_PROVIDER = "AI_PROVIDER";
    public static final String USER = "USER";

    public static final Set<String> ALL = Set.of(CODE, AI_PROVIDER, USER);

    private AuditTargets() {
    }
}
