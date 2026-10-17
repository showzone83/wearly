package app.wearly.common.web;

import app.wearly.common.domain.PatchBody;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** PATCH 본문(JSON)을 PatchBody로 감싼다. 본문이 없으면 isObject()가 false */
public final class JsonPatchBody implements PatchBody {

  private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};

  private final JsonNode body;
  private final JsonMapper jsonMapper;

  public JsonPatchBody(JsonNode body, JsonMapper jsonMapper) {
    this.body = body;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public boolean isObject() {
    return body != null && body.isObject();
  }

  @Override
  public boolean has(String field) {
    return isObject() && body.has(field);
  }

  @Override
  public boolean isNull(String field) {
    return has(field) && body.get(field).isNull();
  }

  @Override
  public boolean isString(String field) {
    return has(field) && body.get(field).isString();
  }

  @Override
  public String string(String field) {
    return has(field) ? body.get(field).asString(null) : null;
  }

  @Override
  public String text(String field) {
    JsonNode n = has(field) ? body.get(field) : null;
    return n == null || n.isNull() ? null : n.isString() ? n.asString() : n.toString();
  }

  @Override
  public boolean isBoolean(String field) {
    return has(field) && body.get(field).isBoolean();
  }

  @Override
  public boolean bool(String field) {
    return body.get(field).asBoolean();
  }

  @Override
  public boolean isInt(String field) {
    return has(field) && body.get(field).isIntegralNumber() && body.get(field).canConvertToInt();
  }

  @Override
  public int intValue(String field) {
    return body.get(field).asInt();
  }

  @Override
  public boolean isObject(String field) {
    return has(field) && body.get(field).isObject();
  }

  @Override
  public Map<String, Object> object(String field) {
    return jsonMapper.convertValue(body.get(field), MAP);
  }
}
