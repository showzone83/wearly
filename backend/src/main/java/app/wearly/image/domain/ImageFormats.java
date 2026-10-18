package app.wearly.image.domain;

import java.util.Optional;

/** 실제 바이트로 형식을 판별한다 (선언된 Content-Type은 믿지 않는다). 저장 사진은 JPEG·PNG만 받는다 */
public final class ImageFormats {

  public static final String JPEG = "image/jpeg";
  public static final String PNG = "image/png";

  private ImageFormats() {}

  public static Optional<String> sniff(byte[] b) {
    if (b == null) {
      return Optional.empty();
    }
    if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
      return Optional.of(JPEG);
    }
    if (b.length >= 8
        && (b[0] & 0xFF) == 0x89
        && b[1] == 'P'
        && b[2] == 'N'
        && b[3] == 'G'
        && b[4] == 0x0D
        && b[5] == 0x0A
        && b[6] == 0x1A
        && b[7] == 0x0A) {
      return Optional.of(PNG);
    }
    return Optional.empty();
  }
}
