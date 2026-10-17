package app.wearly.common.domain;

import java.util.Map;

/**
 * 부분 수정(PATCH) 요청 본문. 웹 어댑터가 JSON으로 구현하고, 유스케이스는 이 인터페이스로 "보낸 필드만" 읽는다. 보내지 않은 필드와 null을 보낸 필드를
 * 구분한다.
 */
public interface PatchBody {

  /** 본문이 JSON 객체인지 (배열·문자열·빈 본문이면 false) */
  boolean isObject();

  /** 필드 값이 JSON 객체인지 */
  boolean isObject(String field);

  boolean has(String field);

  boolean isNull(String field);

  boolean isString(String field);

  /** 값 노드면 그 문자열 표기, 객체·배열·null이면 null (Jackson asString(null)과 같다) */
  String string(String field);

  /** null이면 null, 문자열이면 그 값, 그 밖은 JSON 표기 그대로 (예: 숫자 123 → "123") */
  String text(String field);

  boolean isBoolean(String field);

  boolean bool(String field);

  /** int 범위의 정수인지 */
  boolean isInt(String field);

  int intValue(String field);

  /** JSON 객체 필드를 Map으로 (키 순서 유지) */
  Map<String, Object> object(String field);
}
