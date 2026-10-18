package app.wearly.admin.adapter.in.web.response;

import java.util.List;

/** POST /admin/me/mfa/enable: 복구 코드 10개 (한 번만 보여 준다) */
public record RecoveryCodesResponse(List<String> recoveryCodes) {}
