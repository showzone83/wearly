package app.wearly.common.web;

import app.wearly.common.application.Patch;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** PATCH 본문(JSON) 필드 → Patch (M4). 본문이 JSON 객체가 아니면 웹 어댑터가 명령 대신 null을 넘긴다 */
public final class JsonPatches {

  private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};

  private JsonPatches() {}

  public static boolean isObject(JsonNode body) {
    return body != null && body.isObject();
  }

  /** 값 노드면 그 문자열 표기, 객체·배열·null이면 null */
  public static Patch<String> scalar(JsonNode body, String field) {
    return body.has(field) ? Patch.of(body.get(field).asString(null)) : Patch.absent();
  }

  /** 문자열이면 그 값, 그 밖(null·숫자·객체 …)은 null */
  public static Patch<String> string(JsonNode body, String field) {
    if (!body.has(field)) {
      return Patch.absent();
    }
    JsonNode n = body.get(field);
    return Patch.of(n.isString() ? n.asString() : null);
  }

  /** null이면 null, 문자열이면 그 값, 그 밖은 JSON 표기 그대로 (예: 숫자 123 → "123") */
  public static Patch<String> text(JsonNode body, String field) {
    if (!body.has(field)) {
      return Patch.absent();
    }
    JsonNode n = body.get(field);
    return Patch.of(n.isNull() ? null : n.isString() ? n.asString() : n.toString());
  }

  /** int 범위의 정수만, 아니면 형식 오류 */
  public static Patch<Integer> integer(JsonNode body, String field, String message) {
    if (!body.has(field)) {
      return Patch.absent();
    }
    JsonNode n = body.get(field);
    return n.isIntegralNumber() && n.canConvertToInt()
        ? Patch.of(n.asInt())
        : Patch.rejected(message);
  }

  public static Patch<Boolean> bool(JsonNode body, String field, String message) {
    if (!body.has(field)) {
      return Patch.absent();
    }
    JsonNode n = body.get(field);
    return n.isBoolean() ? Patch.of(n.asBoolean()) : Patch.rejected(message);
  }

  /** JSON 객체만 Map으로 (키 순서 유지), 아니면 형식 오류 */
  public static Patch<Map<String, Object>> object(
      JsonNode body, String field, JsonMapper mapper, String message) {
    if (!body.has(field)) {
      return Patch.absent();
    }
    JsonNode n = body.get(field);
    return n.isObject() ? Patch.of(mapper.convertValue(n, MAP)) : Patch.rejected(message);
  }
}
