package app.wearly.common.application;

import app.wearly.common.domain.FieldError;
import java.util.List;

/**
 * 부분 수정(PATCH) 필드 하나 (M4): 보내지 않음 · 값(null 포함) · 형식 오류 세 상태. 입력 어댑터가 요청 형식(JSON 타입 등)을 보고 만들고,
 * 유스케이스는 업무 검증만 한다. 형식 오류 문구도 입력 어댑터가 정한다 (유스케이스는 다른 업무 오류와 같은 자리에 넣기만 한다).
 */
public final class Patch<T> {

  private static final Patch<?> ABSENT = new Patch<>(false, null, null);

  private final boolean present;
  private final T value;
  private final String error;

  private Patch(boolean present, T value, String error) {
    this.present = present;
    this.value = value;
    this.error = error;
  }

  @SuppressWarnings("unchecked")
  public static <T> Patch<T> absent() {
    return (Patch<T>) ABSENT;
  }

  /** 보낸 값 (null이면 비운다는 뜻) */
  public static <T> Patch<T> of(T value) {
    return new Patch<>(true, value, null);
  }

  /** 보냈지만 형식이 틀림 */
  public static <T> Patch<T> rejected(String message) {
    return new Patch<>(true, null, message);
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

  public String error() {
    return error;
  }

  /** 보냈으면 그 값(null 포함), 아니면 지금 값 */
  public T orElse(T current) {
    return present && error == null ? value : current;
  }

  /** 형식 오류면 필드 오류를 더하고 지금 값을, 아니면 orElse(current) */
  public T apply(String field, T current, List<FieldError> errors) {
    if (invalid()) {
      errors.add(new FieldError(field, error));
      return current;
    }
    return orElse(current);
  }

  @Override
  public String toString() {
    return !present ? "Patch[없음]" : invalid() ? "Patch[형식 오류]" : "Patch[" + value + "]";
  }
}
