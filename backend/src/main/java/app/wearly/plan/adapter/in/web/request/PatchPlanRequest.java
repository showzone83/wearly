package app.wearly.plan.adapter.in.web.request;

import app.wearly.common.application.Patch;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.plan.application.port.in.dto.PatchPlanDto;
import app.wearly.plan.application.port.in.dto.PlanPlaceDto;
import app.wearly.plan.application.port.in.dto.PlanWeatherDto;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PATCH /plans/{id} 본문(JSON) → 예정 부분 수정 명령. 보낸 필드마다 등록 요청(PlanRequest)과 같은 타입으로 바꾼다 (형식 오류는 필드 순서상
 * 첫 번째 하나만). clientId는 바꿀 수 없어 읽지 않는다. 병합과 값 검증은 유스케이스가 한다.
 */
public final class PatchPlanRequest {

  private static final List<RecordComponent> FIELDS =
      List.of(PlanRequest.class.getRecordComponents()).stream()
          .filter(c -> !c.getName().equals("clientId"))
          .toList();

  private PatchPlanRequest() {}

  public static PatchPlanDto read(JsonNode body, JsonMapper jsonMapper) {
    Map<String, Patch<Object>> fields = new HashMap<>();
    FieldError typeError = null;
    boolean object = body != null && body.isObject();
    for (RecordComponent c : FIELDS) {
      String name = c.getName();
      if (!object || !body.has(name)) {
        fields.put(name, Patch.absent());
        continue;
      }
      try {
        JavaType type = jsonMapper.getTypeFactory().constructType(c.getGenericType());
        Object value = jsonMapper.treeToValue(body.get(name), type);
        fields.put(name, Patch.of(toDto(value)));
      } catch (DatabindException e) {
        if (typeError == null) {
          typeError = FieldError.invalidFormat(name);
        }
        fields.put(name, Patch.rejected(FieldErrorCode.INVALID_FORMAT));
      }
    }
    List<FieldError> errors =
        !object
            ? List.of(FieldError.invalidType("body", "object"))
            : typeError == null ? List.of() : List.of(typeError);
    return new PatchPlanDto(
        get(fields, "date"),
        get(fields, "timeOfDay"),
        get(fields, "title"),
        get(fields, "occasion"),
        get(fields, "concepts"),
        get(fields, "memo"),
        PatchPlanRequest.<PlanPlaceDto>get(fields, "place"),
        PatchPlanRequest.<PlanWeatherDto>get(fields, "weather"),
        get(fields, "weatherSource"),
        get(fields, "weatherCheckedAt"),
        get(fields, "forecastReady"),
        get(fields, "outfit"),
        errors);
  }

  /** 중첩 요청(장소·날씨)은 유스케이스 DTO로 바꾼다 */
  private static Object toDto(Object value) {
    if (value instanceof PlanPlaceRequest p) {
      return p.toDto();
    }
    if (value instanceof PlanWeatherRequest w) {
      return w.toDto();
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private static <T> Patch<T> get(Map<String, Patch<Object>> fields, String name) {
    return (Patch<T>) fields.get(name);
  }
}
