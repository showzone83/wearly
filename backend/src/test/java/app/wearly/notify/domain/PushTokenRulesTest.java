package app.wearly.notify.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PushTokenRulesTest {

  static final String TOKEN = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]";

  @Test
  void 올바른_입력은_오류가_없다() {
    assertThat(PushTokenRules.validate(TOKEN, "ios", "dev-1a2b")).isEmpty();
    assertThat(PushTokenRules.validate("ExpoPushToken[abc]", "ANDROID", "a.b:c_d-1")).isEmpty();
  }

  @Test
  void 빈_값은_REQUIRED() {
    List<FieldError> errors = PushTokenRules.validate(" ", null, "");
    assertThat(errors)
        .extracting(FieldError::field, FieldError::code)
        .containsExactly(
            tuple("token", FieldErrorCode.REQUIRED),
            tuple("platform", FieldErrorCode.REQUIRED),
            tuple("deviceId", FieldErrorCode.REQUIRED));
  }

  @Test
  void 형식이_틀린_토큰_모르는_기기_긴_기기ID() {
    List<FieldError> errors = PushTokenRules.validate("fcm-raw-token", "web", "x".repeat(65));
    assertThat(errors)
        .extracting(FieldError::field)
        .containsExactly("token", "platform", "deviceId");
    assertThat(errors.get(0).code()).isEqualTo(FieldErrorCode.INVALID_FORMAT);
    assertThat(errors.get(0).params()).containsEntry("format", "ExponentPushToken[…]");
    assertThat(errors.get(1).code()).isEqualTo(FieldErrorCode.NOT_ALLOWED);
    assertThat(errors.get(1).params().get("allowed")).isEqualTo(List.of("ios", "android"));
    assertThat(errors.get(2).code()).isEqualTo(FieldErrorCode.TOO_LONG);
    assertThat(errors.get(2).params()).containsEntry("max", 64);
  }

  @Test
  void 너무_긴_토큰과_기기ID_글자() {
    assertThat(PushTokenRules.validate("ExponentPushToken[" + "a".repeat(240) + "]", "ios", "d"))
        .extracting(FieldError::code)
        .containsExactly(FieldErrorCode.TOO_LONG);
    assertThat(PushTokenRules.validateDeviceId("기기 1"))
        .extracting(FieldError::code)
        .containsExactly(FieldErrorCode.INVALID_FORMAT);
  }
}
