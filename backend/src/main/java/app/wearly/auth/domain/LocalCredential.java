package app.wearly.auth.domain;

import java.util.Locale;

/** 이메일·비밀번호 자격 증명 (임시 수단, 05 문서 1장). 이메일은 소문자로 정규화해 저장한다 */
public record LocalCredential(Long id, long userId, String email, String passwordHash) {

  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
