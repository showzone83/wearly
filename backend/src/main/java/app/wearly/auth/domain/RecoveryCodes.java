package app.wearly.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 2단계 인증 복구 코드. 10자(헷갈리는 0·1·l·o를 뺀 소문자·숫자 32자, 50비트)를 5자씩 하이픈으로 잇는다: {@code abcde-fghjk}. 등록할 때 한
 * 번만 보여 주고 DB에는 SHA-256 hex("{userId}:{정규화한 코드}")만 둔다. 한 번 쓰면 다시 쓸 수 없다.
 */
public final class RecoveryCodes {

  public static final int COUNT = 10;
  static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
  static final int LENGTH = 10;
  private static final SecureRandom RANDOM = new SecureRandom();

  private RecoveryCodes() {}

  /** 새 복구 코드 {@link #COUNT}개 (서로 다르다) */
  public static List<String> generate() {
    List<String> codes = new ArrayList<>(COUNT);
    while (codes.size() < COUNT) {
      StringBuilder sb = new StringBuilder(LENGTH + 1);
      for (int i = 0; i < LENGTH; i++) {
        if (i == LENGTH / 2) {
          sb.append('-');
        }
        sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
      }
      String code = sb.toString();
      if (!codes.contains(code)) {
        codes.add(code);
      }
    }
    return codes;
  }

  /** 대소문자·공백·하이픈을 무시한다 */
  public static String normalize(String code) {
    return code == null ? "" : code.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
  }

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

  public static String hash(long userId, String code) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] d = digest.digest((userId + ":" + normalize(code)).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(d);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
