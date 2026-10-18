package app.wearly.auth.adapter.out.crypto;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP (RFC 6238, HOTP RFC 4226 위). 관리자 2단계 인증에 쓴다: HMAC-SHA1, 6자리, 30초 단계, 앞뒤 1단계 허용. 같은 단계의 코드는 한
 * 번만 받는다 (마지막으로 받은 단계보다 큰 단계만 인정).
 */
final class Totp {

  public static final int DIGITS = 6;
  public static final long PERIOD_SECONDS = 30;

  /** 앞뒤로 허용하는 단계 수 (기기 시계 차이) */
  public static final int WINDOW = 1;

  /** 비밀 길이 (RFC 4226 권장 160비트) */
  public static final int SECRET_BYTES = 20;

  static final String HMAC_SHA1 = "HmacSHA1";
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int[] POW10 = {
    1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000
  };

  private Totp() {}

  /** 새 비밀 (base32 문자열, 32자) */
  public static String newSecret() {
    byte[] bytes = new byte[SECRET_BYTES];
    RANDOM.nextBytes(bytes);
    return Base32.encode(bytes);
  }

  /** 시각의 단계 번호 (유닉스 시각 / 30초) */
  public static long step(Instant at) {
    return Math.floorDiv(at.getEpochSecond(), PERIOD_SECONDS);
  }

  /** 그 단계의 6자리 코드 */
  public static String code(byte[] key, long step) {
    return code(key, step, DIGITS, HMAC_SHA1);
  }

  /** RFC 6238 부록 B 시험용: 자릿수·알고리즘(HmacSHA1·HmacSHA256·HmacSHA512)을 고른다 */
  static String code(byte[] key, long step, int digits, String algorithm) {
    byte[] hash;
    try {
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(key, algorithm));
      hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("TOTP 계산 실패: " + e.getClass().getSimpleName());
    }
    int offset = hash[hash.length - 1] & 0x0f;
    int binary =
        ((hash[offset] & 0x7f) << 24)
            | ((hash[offset + 1] & 0xff) << 16)
            | ((hash[offset + 2] & 0xff) << 8)
            | (hash[offset + 3] & 0xff);
    String s = Integer.toString(binary % POW10[digits]);
    return "0".repeat(digits - s.length()) + s;
  }

  /** 숫자 6자리인지 (공백은 지운 뒤) */
  public static boolean isCodeFormat(String code) {
    return code != null && normalize(code).matches("\\d{" + DIGITS + "}");
  }

  public static String normalize(String code) {
    return code == null ? "" : code.replaceAll("\\s", "");
  }

  /**
   * 코드가 지금 단계 앞뒤 {@link #WINDOW} 안의 어느 단계와 맞으면 그 단계. lastUsedStep보다 크지 않은 단계는 인정하지 않는다(재사용 막기).
   *
   * @param lastUsedStep 마지막으로 받은 단계 (없으면 null)
   */
  public static OptionalLong verify(byte[] key, String code, Instant now, Long lastUsedStep) {
    if (!isCodeFormat(code)) {
      return OptionalLong.empty();
    }
    byte[] given = normalize(code).getBytes(StandardCharsets.US_ASCII);
    long current = step(now);
    for (long s = current - WINDOW; s <= current + WINDOW; s++) {
      if (lastUsedStep != null && s <= lastUsedStep) {
        continue;
      }
      byte[] expected = code(key, s).getBytes(StandardCharsets.US_ASCII);
      if (MessageDigest.isEqual(expected, given)) {
        return OptionalLong.of(s);
      }
    }
    return OptionalLong.empty();
  }

  /**
   * 인증 앱 등록 주소 (Google Authenticator Key Uri Format). 예: {@code
   * otpauth://totp/Wearly:admin%40wearly.app?secret=…&issuer=Wearly&algorithm=SHA1&digits=6
   * &period=30}
   */
  public static String otpauthUrl(String issuer, String account, String base32Secret) {
    String label = encode(issuer) + ":" + encode(account);
    return "otpauth://totp/"
        + label
        + "?secret="
        + base32Secret
        + "&issuer="
        + encode(issuer)
        + "&algorithm=SHA1&digits="
        + DIGITS
        + "&period="
        + PERIOD_SECONDS;
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
