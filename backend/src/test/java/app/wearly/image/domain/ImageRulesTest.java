package app.wearly.image.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImageRulesTest {

  static final long MAX = 10L * 1024 * 1024;

  @Test
  void 실제_바이트로_JPEG_PNG만_알아본다() {
    assertThat(ImageFormats.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}))
        .contains(ImageFormats.JPEG);
    assertThat(ImageFormats.sniff(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}))
        .contains(ImageFormats.PNG);
    assertThat(ImageFormats.sniff("GIF89a".getBytes())).isEmpty();
    assertThat(ImageFormats.sniff(new byte[] {(byte) 0x89, 'P', 'N', 'G'})).isEmpty();
    assertThat(ImageFormats.sniff(new byte[0])).isEmpty();
    assertThat(ImageFormats.sniff(null)).isEmpty();
  }

  @Test
  void 업로드_요청_검증() {
    assertThat(ImageRules.validateUpload("ITEM", "image/jpeg", 1L, MAX)).isEmpty();
    assertThat(ImageRules.validateUpload("ITEM", "image/png", MAX, MAX)).isEmpty();
    assertThat(ImageRules.validateUpload("PROFILE", "image/gif", 0L, MAX))
        .extracting(FieldError::field)
        .containsExactly("purpose", "contentType", "size");
    assertThat(ImageRules.validateUpload("PROFILE", "image/gif", 0L, MAX))
        .extracting(FieldError::code)
        .containsExactly(
            FieldErrorCode.NOT_ALLOWED, FieldErrorCode.NOT_ALLOWED, FieldErrorCode.OUT_OF_RANGE);
    assertThat(ImageRules.validateUpload(null, null, null, MAX))
        .extracting(FieldError::code)
        .containsOnly(FieldErrorCode.REQUIRED);
    assertThat(ImageRules.validateUpload("ITEM", "image/jpeg", MAX + 1, MAX))
        .extracting(FieldError::code, FieldError::params)
        .containsExactly(tuple(FieldErrorCode.OUT_OF_RANGE, Map.of("min", 1, "max", MAX)));
  }

  @Test
  void imageId는_UUID_형식만() {
    UUID id = UUID.randomUUID();
    assertThat(ImageRules.parseId(id.toString())).contains(id);
    assertThat(ImageRules.parseId(id.toString().toUpperCase())).contains(id);
    assertThat(ImageRules.parseId("1-1-1-1-1")).isEmpty();
    assertThat(ImageRules.parseId("not-a-uuid")).isEmpty();
    assertThat(ImageRules.parseId(null)).isEmpty();
  }

  @Test
  void 오브젝트_키_규칙() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    assertThat(ImageKeys.upload(7, id)).isEqualTo("uploads/7/" + id);
    assertThat(ImageKeys.original(7, id)).isEqualTo("users/7/items/" + id + "/original.jpg");
    assertThat(ImageKeys.thumb(7, id)).isEqualTo("users/7/items/" + id + "/thumb.jpg");
    assertThat(ImageKeys.userPrefix(7)).isEqualTo("users/7/");
    assertThat(ImageKeys.uploadPrefix(7)).isEqualTo("uploads/7/");
  }
}
