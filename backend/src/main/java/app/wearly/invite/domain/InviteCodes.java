package app.wearly.invite.domain;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 초대 코드 (doc/25). 8자, 헷갈리는 0·1·I·L·O를 뺀 대문자·숫자 31자에서 고른다 (약 8.5×10^11가지). DB에는 정리한 8자로 두고 화면에는
 * {@code ABCD-EFGH}로 보인다. 입력은 대소문자·공백·하이픈을 무시한다.
 */
public final class InviteCodes {

  static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  public static final int LENGTH = 8;

  /** 가입 이력에 남기는 입력 코드 최대 길이 (tb_invite_signup.entered_code) */
  public static final int ENTERED_MAX = 20;

  private static final SecureRandom RANDOM = new SecureRandom();

  private InviteCodes() {}

  /** 새 코드 (정리한 8자) */
  public static String generate() {
    StringBuilder sb = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }

  /** 대소문자·공백·하이픈을 무시한다. null이면 빈 문자열 */
  public static String normalize(String code) {
    return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
  }

  /** 정리했을 때 8자이고 모두 쓰는 글자인지 */
  public static boolean isFormat(String code) {
    String n = normalize(code);
    if (n.length() != LENGTH) {
      return false;
    }
    for (int i = 0; i < n.length(); i++) {
      if (ALPHABET.indexOf(n.charAt(i)) < 0) {
        return false;
      }
    }
    return true;
  }

  /** 가입 이력에 남길 입력 코드: 정리한 값을 {@link #ENTERED_MAX}자까지 */
  public static String entered(String code) {
    String n = normalize(code);
    return n.length() > ENTERED_MAX ? n.substring(0, ENTERED_MAX) : n;
  }
}
