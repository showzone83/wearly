package app.wearly.image.domain;

import app.wearly.common.domain.FieldError;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 업로드 URL 요청 규칙 (18 문서 3장): purpose는 ITEM, contentType은 image/jpeg·image/png, size는 1~최대 바이트 */
public final class ImageRules {

  public static final String PURPOSE_ITEM = "ITEM";
  public static final Set<String> CONTENT_TYPES = Set.of(ImageFormats.JPEG, ImageFormats.PNG);

  private ImageRules() {}

  public static List<FieldError> validateUpload(
      String purpose, String contentType, Long size, long maxBytes) {
    List<FieldError> errors = new ArrayList<>();
    if (purpose == null) {
      errors.add(FieldError.required("purpose"));
    } else if (!PURPOSE_ITEM.equals(purpose)) {
      errors.add(FieldError.notAllowed("purpose", List.of(PURPOSE_ITEM)));
    }
    if (contentType == null) {
      errors.add(FieldError.required("contentType"));
    } else if (!CONTENT_TYPES.contains(contentType)) {
      errors.add(
          FieldError.notAllowed("contentType", List.of(ImageFormats.JPEG, ImageFormats.PNG)));
    }
    if (size == null) {
      errors.add(FieldError.required("size"));
    } else if (size < 1 || size > maxBytes) {
      // 바이트 단위 범위 (최대는 설정값, 기본 10MB)
      errors.add(FieldError.outOfRange("size", 1, maxBytes));
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
}
