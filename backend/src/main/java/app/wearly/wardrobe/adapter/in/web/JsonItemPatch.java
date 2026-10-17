package app.wearly.wardrobe.adapter.in.web;

import app.wearly.common.domain.FieldError;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.wardrobe.adapter.in.web.WardrobeDtos.ItemRequest;
import app.wearly.wardrobe.application.port.in.ItemPatch;
import app.wearly.wardrobe.domain.ItemInput;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * PATCH 본문(JSON)을 저장된 값 위에 덮어쓴다. 보낸 필드만 바꾸고 null을 보내면 그 값을 비운다. 덮어쓴 결과는 등록과 같은 요청 검증(Bean
 * Validation)을 거친다.
 */
final class JsonItemPatch implements ItemPatch {

  /** PATCH로 바꿀 수 있는 필드 (Partial&lt;ItemInput&gt;) */
  private static final Set<String> PATCHABLE =
      Set.of(
          "name",
          "category",
          "subType",
          "color",
          "pattern",
          "seasons",
          "styles",
          "formality",
          "status",
          "brand",
          "wornCount",
          "lastWornAt");

  private final JsonNode patch;
  private final JsonMapper jsonMapper;
  private final Validator validator;

  JsonItemPatch(JsonNode patch, JsonMapper jsonMapper, Validator validator) {
    this.patch = patch;
    this.jsonMapper = jsonMapper;
    this.validator = validator;
  }

  @Override
  public ItemInput applyTo(ItemInput current) {
    if (patch == null || !patch.isObject()) {
      throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
    }
    ObjectNode merged = jsonMapper.valueToTree(ItemRequest.of(current));
    for (var entry : patch.properties()) {
      if (PATCHABLE.contains(entry.getKey())) {
        merged.set(entry.getKey(), entry.getValue());
      }
    }
    ItemRequest input;
    try {
      input = jsonMapper.treeToValue(merged, ItemRequest.class);
    } catch (DatabindException e) {
      String field =
          e.getPath().isEmpty() || e.getPath().get(0).getPropertyName() == null
              ? "body"
              : e.getPath().get(0).getPropertyName();
      throw ApiException.validation(field, "허용되지 않는 값이에요.");
    }
    validate(input);
    return input.toInput();
  }

  private void validate(ItemRequest input) {
    Set<ConstraintViolation<ItemRequest>> violations = validator.validate(input);
    if (violations.isEmpty()) {
      return;
    }
    List<FieldError> errors =
        violations.stream()
            .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
            .sorted(Comparator.comparing(FieldError::field))
            .toList();
    throw new ApiException(
        ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
  }
}
