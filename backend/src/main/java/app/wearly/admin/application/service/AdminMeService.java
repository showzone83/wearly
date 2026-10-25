package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminMeUseCase;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.auth.application.port.in.MfaUseCase;
import app.wearly.auth.application.port.in.PasswordUseCase;
import app.wearly.auth.application.port.in.dto.MfaSetupDto;
import app.wearly.auth.application.port.in.dto.MfaStatusDto;
import app.wearly.auth.application.port.in.dto.PasswordStatusDto;
import app.wearly.common.error.BusinessException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 자신의 계정 (22 문서 4.1). 비밀번호(변경 주기 28 문서)·2단계 인증은 auth 컨텍스트 유스케이스로 바꾸고 변경 이력을 남긴다. 2단계 인증 확인 실패는
 * auth가 로그인 이력(MFA_FAILED)에 남기므로, 업무 오류로 끝나도 롤백하지 않는다.
 */
@Service
public class AdminMeService implements AdminMeUseCase {

  private final PasswordUseCase passwords;
  private final MfaUseCase mfa;
  private final RecordAuditPort audit;

  public AdminMeService(PasswordUseCase passwords, MfaUseCase mfa, RecordAuditPort audit) {
    this.passwords = passwords;
    this.mfa = mfa;
    this.audit = audit;
  }

  @Override
  @Transactional
  public void changePassword(
      long adminId, String sessionId, String currentPassword, String newPassword) {
    int ended = passwords.change(adminId, sessionId, currentPassword, newPassword);
    audit.record(
        adminId,
        "ADMIN_PASSWORD_CHANGE",
        AuditTargets.USER,
        Long.toString(adminId),
        null,
        Map.of("endedSessions", ended));
  }

  @Override
  public PasswordStatusDto passwordStatus(long adminId) {
    return passwords.status(adminId);
  }

  @Override
  @Transactional
  public PasswordStatusDto postponePassword(long adminId, String sessionId) {
    PasswordStatusDto before = passwords.status(adminId);
    PasswordStatusDto after = passwords.postpone(adminId, sessionId);
    audit.record(
        adminId,
        "ADMIN_PASSWORD_POSTPONE",
        AuditTargets.USER,
        Long.toString(adminId),
        dueOf(before),
        dueOf(after));
    return after;
  }

  @Override
  public MfaStatusDto mfaStatus(long adminId) {
    return mfa.status(adminId);
  }

  @Override
  public MfaSetupDto startMfaSetup(long adminId) {
    return mfa.startSetup(adminId);
  }

  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public List<String> enableMfa(long adminId, String sessionId, String code) {
    List<String> codes = mfa.enable(adminId, sessionId, code);
    audit.record(
        adminId,
        "ADMIN_MFA_ENROLL",
        AuditTargets.USER,
        Long.toString(adminId),
        Map.of("enrolled", false),
        Map.of("enrolled", true));
    return codes;
  }

  /** 변경 이력 값: 기한 (기한이 없으면 null을 담을 수 있게 HashMap) */
  private static Map<String, Object> dueOf(PasswordStatusDto s) {
    Map<String, Object> m = new HashMap<>();
    m.put("dueAt", s.dueAt() == null ? null : s.dueAt().truncatedTo(ChronoUnit.MILLIS).toString());
    return m;
  }

  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public void resetMfa(
      long adminId, String sessionId, String currentPassword, String code, String recoveryCode) {
    int ended = mfa.reset(adminId, sessionId, currentPassword, code, recoveryCode);
    audit.record(
        adminId,
        "ADMIN_MFA_RESET",
        AuditTargets.USER,
        Long.toString(adminId),
        Map.of("enrolled", true),
        Map.of("enrolled", false, "endedSessions", ended));
  }
}
