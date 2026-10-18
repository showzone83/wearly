package app.wearly.image.domain;

import app.wearly.common.domain.FieldError;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 업로드 URL 요청 규칙 (17 문서 3장): purpose는 ITEM, contentType은 image/jpeg·image/png, size는 1~최대 바이트 */
public final class ImageRules {

  public static final String PURPOSE_ITEM = "ITEM";
  public static final Set<String> CONTENT_TYPES = Set.of(ImageFormats.JPEG, ImageFormats.PNG);

  private ImageRules() {}

  public static List<FieldError> validateUpload(
      String purpose, String contentType, Long size, long maxBytes) {
    List<FieldError> errors = new ArrayList<>();
    if (purpose == null) {
      errors.add(new FieldError("purpose", "필수 값이에요."));
    } else if (!PURPOSE_ITEM.equals(purpose)) {
      errors.add(new FieldError("purpose", "ITEM이어야 해요."));
    }
    if (contentType == null) {
      errors.add(new FieldError("contentType", "필수 값이에요."));
    } else if (!CONTENT_TYPES.contains(contentType)) {
      errors.add(new FieldError("contentType", "image/jpeg 또는 image/png여야 해요."));
    }
    if (size == null) {
      errors.add(new FieldError("size", "필수 값이에요."));
    } else if (size < 1) {
      errors.add(new FieldError("size", "1 이상이어야 해요."));
    } else if (size > maxBytes) {
      errors.add(new FieldError("size", "사진은 " + megabytes(maxBytes) + "MB까지 올릴 수 있어요."));
    }
    return errors;
  }

  /** imageId 문자열 → UUID. 형식이 틀리면 비어 있다 (없는 이미지로 본다) */
  public static Optional<UUID> parseId(String raw) {
    if (raw == null || raw.length() != 36) {
      return Optional.empty();
    }
    try {
      UUID id = UUID.fromString(raw);
      return id.toString().equalsIgnoreCase(raw) ? Optional.of(id) : Optional.empty();
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public static long megabytes(long bytes) {
    return Math.max(1, bytes / (1024 * 1024));
  }
}
