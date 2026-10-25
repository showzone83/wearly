package app.wearly.sharedkernel.shortcode;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 사람이 옮겨 적는 짧은 코드 (친구 초대 doc/25, 옷장 도우미 연결 doc/26). 헷갈리는 0·1·I·L·O를 뺀 대문자·숫자 31자에서 고른다. 저장은 정리한 값으로
 * 하고 화면에는 {@code ABCD-EFGH}처럼 보인다. 입력은 대소문자·공백·하이픈을 무시한다.
 */
public final class ShortCodes {

  public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

  private static final SecureRandom RANDOM = new SecureRandom();

  private ShortCodes() {}

  /** 새 코드 (정리한 값) */
  public static String generate(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }

  /** 대소문자·공백·하이픈을 무시한다. null이면 빈 문자열 */
  public static String normalize(String code) {
    return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
  }

  /** 정리했을 때 length자이고 모두 쓰는 글자인지 */
  public static boolean isFormat(String code, int length) {
    String n = normalize(code);
    if (n.length() != length) {
      return false;
    }
    for (int i = 0; i < n.length(); i++) {
      if (ALPHABET.indexOf(n.charAt(i)) < 0) {
        return false;
      }
    }
    return true;
  }
}
