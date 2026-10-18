package app.wearly.common.application;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import java.util.List;
import java.util.Map;

/**
 * 부분 수정(PATCH) 필드 하나 (M4): 보내지 않음 · 값(null 포함) · 형식 오류 세 상태. 입력 어댑터가 요청 형식(JSON 타입 등)을 보고 만들고,
 * 유스케이스는 업무 검증만 한다. 형식 오류 코드도 입력 어댑터가 정한다 (유스케이스는 다른 업무 오류와 같은 자리에 넣기만 한다).
 */
public final class Patch<T> {

  private static final Patch<?> ABSENT = new Patch<>(false, null, null, Map.of());

  private final boolean present;
  private final T value;
  private final FieldErrorCode error;
  private final Map<String, Object> params;

  private Patch(boolean present, T value, FieldErrorCode error, Map<String, Object> params) {
    this.present = present;
    this.value = value;
    this.error = error;
    this.params = params;
  }

  @SuppressWarnings("unchecked")
  public static <T> Patch<T> absent() {
    return (Patch<T>) ABSENT;
  }

  /** 보낸 값 (null이면 비운다는 뜻) */
  public static <T> Patch<T> of(T value) {
    return new Patch<>(true, value, null, Map.of());
  }

  /** 보냈지만 형식이 틀림 */
  public static <T> Patch<T> rejected(FieldErrorCode code) {
    return new Patch<>(true, null, code, Map.of());
  }

  /** 보냈지만 JSON 타입이 틀림. expected: boolean·string·integer·number·object·array */
  public static <T> Patch<T> rejectedType(String expected) {
    return new Patch<>(true, null, FieldErrorCode.INVALID_TYPE, Map.of("expected", expected));
  }

  public boolean present() {
    return present;
  }

  public boolean invalid() {
    return error != null;
  }

  public T value() {
    return value;
  }

  /** 형식 오류를 그 필드의 오류로 (형식 오류가 아니면 null) */
  public FieldError error(String field) {
    return error == null ? null : new FieldError(field, error, params);
  }

  /** 보냈으면 그 값(null 포함), 아니면 지금 값 */
  public T orElse(T current) {
    return present && error == null ? value : current;
  }

  /** 형식 오류면 필드 오류를 더하고 지금 값을, 아니면 orElse(current) */
  public T apply(String field, T current, List<FieldError> errors) {
    if (invalid()) {
      errors.add(error(field));
      return current;
    }
    return orElse(current);
  }

  @Override
  public String toString() {
    return !present ? "Patch[없음]" : invalid() ? "Patch[형식 오류]" : "Patch[" + value + "]";
  }
}
