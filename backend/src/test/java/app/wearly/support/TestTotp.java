package app.wearly.support;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 통합 테스트용 TOTP 계산 (인증 앱 역할. RFC 6238 HMAC-SHA1 6자리 30초). 서버 구현과 따로 둔다 */
public final class TestTotp {

  private TestTotp() {}

  public static long step(Instant at) {
    return Math.floorDiv(at.getEpochSecond(), 30);
  }

  public static String code(String base32Secret, long step) {
    try {
      byte[] key = decode(base32Secret);
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
      int o = h[h.length - 1] & 0x0f;
      int bin =
          ((h[o] & 0x7f) << 24)
              | ((h[o + 1] & 0xff) << 16)
              | ((h[o + 2] & 0xff) << 8)
              | (h[o + 3] & 0xff);
      return String.format(Locale.ROOT, "%06d", bin % 1_000_000);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] decode(String s) {
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    ByteBuffer out = ByteBuffer.allocate(s.length() * 5 / 8);
    int buffer = 0;
    int bits = 0;
    for (char c : s.toUpperCase(Locale.ROOT).toCharArray()) {
      buffer = (buffer << 5) | alphabet.indexOf(c);
      bits += 5;
      if (bits >= 8) {
        out.put((byte) (buffer >> (bits - 8)));
        bits -= 8;
      }
    }
    return out.array();
  }
}
