package app.wearly.common.domain;

import java.util.List;

/**
 * 필드 오류 코드 (오류 응답 fieldErrors[].code). 응답에는 문장을 넣지 않고 이 코드와 params만 보낸다. 화면 문구는 앱·관리자가 코드별로 정한다 (권장
 * 문구·설명은 resources/error-codes.json). 이름은 앱·관리자가 쓰므로 바꾸지 말고 추가만 한다.
 *
 * <p>괄호 안은 이 코드가 쓸 수 있는 params 이름이다 (없는 이름을 넣으면 FieldError가 거부한다).
 */
public enum FieldErrorCode {
  /** 값이 없음 (null·빈 문자열·빈 배열) */
  REQUIRED,
  /** 너무 짧음 (글자 수·개수) */
  TOO_SHORT("min"),
  /** 너무 김 (글자 수) */
  TOO_LONG("max"),
  /** 형식이 틀림 (정규식·날짜·주소·JSON 파싱 등). format은 기대 형식 이름 */
  INVALID_FORMAT("format"),
  /** JSON 타입이 틀림. expected는 boolean·string·integer·number·object·array */
  INVALID_TYPE("expected"),
  /** 숫자 범위 밖. min·max 중 정해진 것만 */
  OUT_OF_RANGE("min", "max"),
  /** 허용 목록·코드 테이블에 없는 값. allowed는 고정 목록일 때만 */
  NOT_ALLOWED("allowed"),
  /** 코드 테이블에 있지만 지금은 고를 수 없는(비활성) 값 */
  INACTIVE,
  /** 같은 값이 이미 있음·중복 */
  DUPLICATE,
  /** 가리키는 대상이 없음 (지역 등) */
  NOT_FOUND,
  /** 배열 항목이 너무 많음 */
  TOO_MANY("max"),
  /** 바꿀 수 없는 값 (ID·코드) */
  IMMUTABLE,
  /** 예약된 이름이라 쓸 수 없음 */
  RESERVED,
  /** 자기 자신(지금 세션·자기 계정·자기 코드)에는 할 수 없음 */
  SELF_NOT_ALLOWED,
  /** 목록이 빠짐없이 한 번씩 오지 않음. expected는 기대 개수 */
  INCOMPLETE("expected"),
  /** 사용하지 않는(사용 중지) 옷 */
  ITEM_RETIRED,
  /** 내 옷장에 없는 옷 */
  NOT_OWNED,
  /** 그 칸(slot)에 넣을 수 없는 옷 */
  SLOT_MISMATCH("slot"),
  /** 카테고리와 맞지 않는 세부 종류 */
  SUBTYPE_MISMATCH,
  /** 다른 입력과 함께 쓸 수 없음 */
  CONFLICT,
  /** 서버 설정에서 꺼져 있어 쓸 수 없음 (관리자 API) */
  DISABLED_BY_SERVER;

  private final List<String> params;

  FieldErrorCode(String... params) {
    this.params = List.of(params);
  }

  /** 이 코드가 쓸 수 있는 params 이름 */
  public List<String> params() {
    return params;
  }
}
