package app.wearly.ai.domain;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 업로드 형식 판별: jpeg·png·webp·heic (계약 6장). 선언된 형식이 없으면 첫 바이트로 알아낸다. */
public final class ImageTypes {

  private static final Map<String, String> ALIASES =
      Map.of(
          "image/jpeg", "image/jpeg",
          "image/jpg", "image/jpeg",
          "image/pjpeg", "image/jpeg",
          "image/png", "image/png",
          "image/webp", "image/webp",
          "image/heic", "image/heic",
          "image/heif", "image/heic");

  /** 올릴 수 있는 형식 이름 (오류 응답 params.allowed) */
  public static final List<String> NAMES = List.of("jpeg", "png", "webp", "heic");

  private ImageTypes() {}

  public static Optional<String> resolve(String declared, byte[] bytes) {
    if (declared != null) {
      String base = declared.split(";")[0].strip().toLowerCase(Locale.ROOT);
      String known = ALIASES.get(base);
      if (known != null) {
        return Optional.of(known);
      }
    }
    return sniff(bytes);
  }

  public static Optional<String> sniff(byte[] b) {
    if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
      return Optional.of("image/jpeg");
    }
    if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
      return Optional.of("image/png");
    }
    if (b.length >= 12
        && b[0] == 'R'
        && b[1] == 'I'
        && b[2] == 'F'
        && b[3] == 'F'
        && b[8] == 'W'
        && b[9] == 'E'
        && b[10] == 'B'
        && b[11] == 'P') {
      return Optional.of("image/webp");
    }
    if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
      String brand = new String(b, 8, 4, StandardCharsets.US_ASCII);
      if (brand.startsWith("hei")
          || brand.startsWith("hev")
          || brand.equals("mif1")
          || brand.equals("msf1")) {
        return Optional.of("image/heic");
      }
    }
    return Optional.empty();
  }
}
