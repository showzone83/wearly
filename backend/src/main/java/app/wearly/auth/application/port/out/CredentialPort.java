package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.LocalCredential;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 이메일 자격 증명 저장·로드 (JPA) */
public interface CredentialPort {

  Optional<LocalCredential> findByEmail(String email);

  boolean existsByEmail(String email);

  Optional<LocalCredential> findByUserId(long userId);

  /** 새 자격 증명. 같은 이메일이 동시에 들어오면 409 EMAIL_TAKEN */
  void create(LocalCredential credential);

  void markLogin(long credentialId, Instant at);

  /** 비밀번호 해시를 바꾼다. 비밀번호를 정한 시각을 at으로 하고 변경 알림 연장·발송 기록을 지운다 (28 문서) */
  void changePassword(long credentialId, String passwordHash, Instant at);

  /** 비밀번호 변경 알림 연장 (28 문서) */
  void postpone(long credentialId, Instant at);

  /**
   * 변경 알림을 보낼 회원 (28 문서 2단계): 활성 회원이고, 기준 시각(정한 시각과 연장 시각 중 늦은 쪽)이 dueBy 이전이고, 그 기준 시각 뒤로 알림을 보낸 적이
   * 없는 회원. 기준 시각이 이른 순으로 limit명까지
   *
   * @param dueBy 지금 - 주기 (이 시각 이전에 정한 비밀번호는 기한이 지났다)
   */
  List<Long> findReminderDueUserIds(Instant dueBy, int limit);

  /**
   * 그 회원에게 알림을 보낸다고 표시한다(password_reminded_at = now). 조건은 {@link #findReminderDueUserIds}와 같다. 그새
   * 바꿨거나 연장했거나 다른 서버가 먼저 잡았으면 false
   */
  boolean claimReminder(long userId, Instant dueBy, Instant now);
}
