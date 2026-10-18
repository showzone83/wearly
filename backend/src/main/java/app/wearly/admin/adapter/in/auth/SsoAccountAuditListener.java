package app.wearly.admin.adapter.in.auth;

import app.wearly.admin.application.port.in.AdminSsoAuditUseCase;
import app.wearly.auth.application.port.in.SsoAccountListener;
import org.springframework.stereotype.Component;

/**
 * auth가 정의한 SSO 계정 알림(SsoAccountListener)을 받아 관리자 변경 이력에 남긴다 (22 문서 4.2). 의존은 admin → auth 한 방향이다
 * (admin은 이미 auth의 세션·2단계 인증 유스케이스를 쓴다).
 */
@Component
class SsoAccountAuditListener implements SsoAccountListener {

  private final AdminSsoAuditUseCase audit;

  SsoAccountAuditListener(AdminSsoAuditUseCase audit) {
    this.audit = audit;
  }

  @Override
  public void accountCreated(long userId, String email, String name, String providerId) {
    audit.accountCreated(userId, email, name, providerId);
  }

  @Override
  public void accountLinked(long userId, String email, String providerId) {
    audit.accountLinked(userId, email, providerId);
  }

  @Override
  public void roleChanged(
      long userId,
      String email,
      String status,
      String beforeRole,
      String afterRole,
      String providerId) {
    audit.roleChanged(userId, email, status, beforeRole, afterRole, providerId);
  }
}
