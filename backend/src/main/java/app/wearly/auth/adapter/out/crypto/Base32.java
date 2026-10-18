package app.wearly.auth.adapter.out.crypto;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Optional;

/** RFC 4648 base32 (대문자 A-Z2-7, 채움 문자 없음). TOTP 비밀을 인증 앱에 넣는 형식이다 */
final class Base32 {

  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  private Base32() {}

  public static String encode(byte[] bytes) {
    StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
    int buffer = 0;
    int bits = 0;
    for (byte b : bytes) {
      buffer = (buffer << 8) | (b & 0xff);
      bits += 8;
      while (bits >= 5) {
        out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1f));
        bits -= 5;
      }
    }
    if (bits > 0) {
      out.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
    }
    return out.toString();
  }

  /** 공백·하이픈·끝의 '='는 무시하고 대소문자를 가리지 않는다. 형식이 틀리면 빈 값 */
  public static Optional<byte[]> decode(String text) {
    if (text == null) {
      return Optional.empty();
    }
    String s = text.replaceAll("[\\s-]", "").replaceAll("=+$", "").toUpperCase(Locale.ROOT);
    ByteArrayOutputStream out = new ByteArrayOutputStream(s.length() * 5 / 8);
    int buffer = 0;
    int bits = 0;
    for (int i = 0; i < s.length(); i++) {
      int v = ALPHABET.indexOf(s.charAt(i));
      if (v < 0) {
        return Optional.empty();
      }
      buffer = (buffer << 5) | v;
      bits += 5;
      if (bits >= 8) {
        out.write((buffer >> (bits - 8)) & 0xff);
        bits -= 8;
      }
    }
    return Optional.of(out.toByteArray());
  }
}
