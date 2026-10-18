package app.wearly.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 응답 params는 카탈로그(ErrorCode·FieldErrorCode)가 정한 이름만 받는다 */
class BusinessExceptionTest {

  @Test
  void 정하지_않은_params_이름은_거부한다() {
    assertThatThrownBy(() -> new BusinessException(ErrorCode.NOT_FOUND, Map.of("detail", "x")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FieldError("name", FieldErrorCode.REQUIRED, Map.of("max", 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 로그용_메시지는_코드와_필드_코드뿐이다() {
    BusinessException e =
        new BusinessException(
            ErrorCode.VALIDATION_FAILED,
            List.of(FieldError.required("name"), FieldError.tooLong("memo", 200)));
    assertThat(e.getMessage()).isEqualTo("VALIDATION_FAILED [name=REQUIRED, memo=TOO_LONG]");
    assertThat(new BusinessException(ErrorCode.EMAIL_TAKEN).getMessage()).isEqualTo("EMAIL_TAKEN");
  }

  @Test
  void 응답_본문은_문장_없이_코드와_params_traceId() {
    ErrorResponse r =
        ErrorResponse.of(
            ErrorCode.WARDROBE_INSUFFICIENT,
            Map.of("missing", List.of("SHOES")),
            List.of(FieldError.outOfRange("count", 1, null)));
    assertThat(r.body().keySet())
        .containsExactly("status", "code", "params", "fieldErrors", "traceId");
    assertThat(r.body().get("fieldErrors"))
        .isEqualTo(
            List.of(Map.of("field", "count", "code", "OUT_OF_RANGE", "params", Map.of("min", 1))));
    assertThat(ErrorResponse.of(ErrorCode.NOT_FOUND).body()).doesNotContainKey("params");
  }
}
