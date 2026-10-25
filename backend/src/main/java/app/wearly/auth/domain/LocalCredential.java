package app.wearly.auth.domain;

import java.time.Instant;
import java.util.Locale;

/**
 * 이메일·비밀번호 자격 증명 (임시 수단, 05 문서 1장). 이메일은 소문자로 정규화해 저장한다.
 *
 * @param passwordChangedAt 비밀번호를 마지막으로 정한 시각 (28 문서). 저장 전이면 null
 * @param passwordPostponedAt 비밀번호 변경 알림을 마지막으로 연장한 시각. 없으면 null
 */
public record LocalCredential(
    Long id,
    long userId,
    String email,
    String passwordHash,
    Instant passwordChangedAt,
    Instant passwordPostponedAt) {

  /** 새로 저장할 자격 증명 (시각은 저장할 때 정한다) */
  public LocalCredential(Long id, long userId, String email, String passwordHash) {
    this(id, userId, email, passwordHash, null, null);
  }

  /** 변경 알림을 at에 연장한 모습 */
  public LocalCredential postponedAt(Instant at) {
    return new LocalCredential(id, userId, email, passwordHash, passwordChangedAt, at);
  }

  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
