package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminSsoAuditUseCase;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** SSO가 바꾼 계정을 변경 이력에 남긴다 (관리자 = 시스템, adminUserId null). auth의 SSO 트랜잭션 안에서만 부른다 */
@Service
public class AdminSsoAuditService implements AdminSsoAuditUseCase {

  static final String SOURCE = "SSO";

  private final RecordAuditPort audit;

  public AdminSsoAuditService(RecordAuditPort audit) {
    this.audit = audit;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void accountCreated(long userId, String email, String name, String providerId) {
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("email", email);
    after.put("name", name);
    after.put("role", "ADMIN");
    after.put("source", SOURCE);
    after.put("provider", providerId);
    audit.record(null, "SSO_ACCOUNT_CREATE", AuditTargets.USER, Long.toString(userId), null, after);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void accountLinked(long userId, String email, String providerId) {
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("email", email);
    after.put("source", SOURCE);
    after.put("provider", providerId);
    audit.record(null, "SSO_ACCOUNT_LINK", AuditTargets.USER, Long.toString(userId), null, after);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void roleChanged(
      long userId,
      String email,
      String status,
      String beforeRole,
      String afterRole,
      String providerId) {
    Map<String, Object> before = new LinkedHashMap<>();
    before.put("email", email);
    before.put("status", status);
    before.put("role", beforeRole);
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("email", email);
    after.put("status", status);
    after.put("role", afterRole);
    after.put("source", SOURCE);
    after.put("provider", providerId);
    audit.record(null, "USER_ROLE_CHANGE", AuditTargets.USER, Long.toString(userId), before, after);
  }
}
