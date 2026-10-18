package app.wearly.wardrobe.adapter.in.web.request;

import app.wearly.common.application.Patch;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.common.error.ConstraintCodes;
import app.wearly.wardrobe.application.port.in.dto.PatchItemDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PATCH /wardrobe/items/{id} 본문(JSON) → 아이템 부분 수정 명령 (M4). 보낸 필드마다 등록 요청(ItemRequest)과 같은 타입으로 바꾸고
 * 같은 Bean Validation을 건다 (안 보낸 필드는 저장된 값이라 이미 맞다). 예전처럼 형식 오류는 하나만(필드 순서상 첫 번째), 그 밖의 검증 오류는 필드 이름
 * 순으로 모은다. 병합과 업무 검증(코드 테이블)은 유스케이스가 트랜잭션 안에서 한다.
 */
public final class PatchItemRequest {

  private static final List<RecordComponent> FIELDS =
      List.of(ItemRequest.class.getRecordComponents());

  private PatchItemRequest() {}

  public static PatchItemDto read(JsonNode body, JsonMapper jsonMapper, Validator validator) {
    Map<String, Patch<Object>> fields = new HashMap<>();
    List<FieldError> invalid = new ArrayList<>();
    FieldError typeError = null;
    boolean object = body != null && body.isObject();
    for (RecordComponent c : FIELDS) {
      String name = c.getName();
      if (!object || !body.has(name)) {
        fields.put(name, Patch.absent());
        continue;
      }
      Object value;
      try {
        JavaType type = jsonMapper.getTypeFactory().constructType(c.getGenericType());
        value = jsonMapper.treeToValue(body.get(name), type);
      } catch (DatabindException e) {
        if (typeError == null) {
          typeError = FieldError.invalidFormat(name);
        }
        fields.put(name, Patch.rejected(FieldErrorCode.INVALID_FORMAT));
        continue;
      }
      fields.put(name, Patch.of(value));
      for (ConstraintViolation<ItemRequest> v :
          validator.validateValue(ItemRequest.class, name, value)) {
        invalid.add(ConstraintCodes.of(ConstraintCodes.path(v), v));
      }
    }
    List<FieldError> errors;
    if (!object) {
      errors = List.of(FieldError.invalidType("body", "object"));
    } else if (typeError != null) {
      errors = List.of(typeError);
    } else {
      invalid.sort(Comparator.comparing(FieldError::field));
      errors = List.copyOf(invalid);
    }
    return new PatchItemDto(
        get(fields, "name"),
        get(fields, "category"),
        get(fields, "subType"),
        get(fields, "color"),
        get(fields, "pattern"),
        get(fields, "seasons"),
        get(fields, "styles"),
        get(fields, "formality"),
        get(fields, "status"),
        get(fields, "brand"),
        get(fields, "wornCount"),
        PatchItemRequest.<LocalDate>get(fields, "lastWornAt"),
        get(fields, "imageId"),
        errors);
  }

  @SuppressWarnings("unchecked")
  private static <T> Patch<T> get(Map<String, Patch<Object>> fields, String name) {
    return (Patch<T>) fields.get(name);
  }
}
