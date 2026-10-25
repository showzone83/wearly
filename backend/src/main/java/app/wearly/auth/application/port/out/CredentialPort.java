package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.LocalCredential;
import java.time.Instant;
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
}
