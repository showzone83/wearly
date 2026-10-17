package app.wearly.wardrobe;

import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse;
import app.wearly.code.CodeCatalog;
import app.wearly.code.CodeGroups;
import app.wearly.code.CodeSnapshot;
import app.wearly.wardrobe.WardrobeDtos.ItemDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 옷장 쓰기·단건 조회(JPA)와 목록 조회(MyBatis) */
@Service
public class WardrobeService {

    /** PATCH로 바꿀 수 있는 필드 (Partial&lt;ItemInput&gt;) */
    private static final Set<String> PATCHABLE = Set.of("name", "category", "subType", "color", "pattern", "seasons",
            "styles", "formality", "status", "brand", "wornCount", "lastWornAt");

    private static final Set<String> SORTS = Set.of("recent", "worn", "name");

    private final WardrobeItemRepository items;
    private final WardrobeQueryMapper queries;
    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final CodeCatalog catalog;

    public WardrobeService(WardrobeItemRepository items, WardrobeQueryMapper queries, JsonMapper jsonMapper,
            Validator validator, CodeCatalog catalog) {
        this.items = items;
        this.queries = queries;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<ItemDto> list(long userId, String category, String status, String color, String q, String sort) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        CodeSnapshot codes = catalog.current();
        // 필터는 저장된 값을 찾는 것이라 비활성 코드도 허용한다. 모르는 값만 400
        String categoryCode = filterCode(codes, CodeGroups.CATEGORY, category, "category", errors);
        String statusCode = filterCode(codes, CodeGroups.ITEM_STATUS, status, "status", errors);
        String colorCode = filterCode(codes, CodeGroups.COLOR, color, "color", errors);
        String sortKey = blankToNull(sort) == null ? "recent" : sort.strip();
        if (!SORTS.contains(sortKey)) {
            errors.add(new ErrorResponse.FieldError("sort", "recent, worn, name 중 하나여야 해요."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
        String query = blankToNull(q);
        String pattern = query == null ? null : "%" + escapeLike(query.strip()) + "%";
        return queries.findItems(new ItemFilter(userId, categoryCode, statusCode, colorCode, pattern, sortKey))
                .stream().map(ItemMapping::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ItemDto get(long userId, long itemId) {
        return ItemMapping.toDto(find(userId, itemId));
    }

    @Transactional
    public ItemDto create(long userId, ItemInput input) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        ItemCodeValidator.validate(catalog.current(), input, null, "", errors);
        throwIfAny(errors);
        WardrobeItem item = new WardrobeItem(userId);
        ItemMapping.apply(item, input);
        return ItemMapping.toDto(items.saveAndFlush(item));
    }

    /** 요청 순서대로 저장하고 같은 순서로 돌려준다. 하나라도 틀리면 전부 저장하지 않는다. */
    @Transactional
    public List<ItemDto> bulkCreate(long userId, List<ItemInput> inputs) {
        CodeSnapshot codes = catalog.current();
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            ItemCodeValidator.validate(codes, inputs.get(i), null, "items[" + i + "].", errors);
        }
        throwIfAny(errors);
        List<ItemDto> created = new ArrayList<>(inputs.size());
        for (ItemInput input : inputs) {
            WardrobeItem item = new WardrobeItem(userId);
            ItemMapping.apply(item, input);
            created.add(ItemMapping.toDto(items.saveAndFlush(item)));
        }
        return created;
    }

    /** 저장된 값 위에 보낸 필드만 덮어쓴 뒤 등록과 같은 검증을 한다. null을 보내면 그 값을 비운다. */
    @Transactional
    public ItemDto patch(long userId, long itemId, JsonNode patch) {
        WardrobeItem item = find(userId, itemId);
        if (patch == null || !patch.isObject()) {
            throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
        }
        ObjectNode merged = jsonMapper.valueToTree(ItemMapping.toInput(item));
        for (var entry : patch.properties()) {
            if (PATCHABLE.contains(entry.getKey())) {
                merged.set(entry.getKey(), entry.getValue());
            }
        }
        ItemInput input;
        try {
            input = jsonMapper.treeToValue(merged, ItemInput.class);
        } catch (DatabindException e) {
            String field = e.getPath().isEmpty() || e.getPath().get(0).getPropertyName() == null
                    ? "body" : e.getPath().get(0).getPropertyName();
            throw ApiException.validation(field, "허용되지 않는 값이에요.");
        }
        validate(input);
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        ItemCodeValidator.validate(catalog.current(), input, ItemMapping.toInput(item), "", errors);
        throwIfAny(errors);
        ItemMapping.apply(item, input);
        return ItemMapping.toDto(items.saveAndFlush(item));
    }

    @Transactional
    public void delete(long userId, long itemId) {
        items.delete(find(userId, itemId));
    }

    private WardrobeItem find(long userId, long itemId) {
        return items.findByIdAndUserId(itemId, userId).orElseThrow(ApiException::notFound);
    }

    private void validate(ItemInput input) {
        Set<ConstraintViolation<ItemInput>> violations = validator.validate(input);
        if (violations.isEmpty()) {
            return;
        }
        List<ErrorResponse.FieldError> errors = violations.stream()
                .map(v -> new ErrorResponse.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field))
                .toList();
        throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
    }

    private static void throwIfAny(List<ErrorResponse.FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
    }

    private static String filterCode(CodeSnapshot codes, String group, String raw, String field,
            List<ErrorResponse.FieldError> errors) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        if (!codes.known(group, value.strip())) {
            errors.add(new ErrorResponse.FieldError(field, "허용되지 않는 값이에요."));
            return null;
        }
        return value.strip();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
