package app.wearly.wardrobe;

import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse;
import app.wearly.domain.Category;
import app.wearly.domain.ColorKey;
import app.wearly.domain.ItemStatus;
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

    public WardrobeService(WardrobeItemRepository items, WardrobeQueryMapper queries, JsonMapper jsonMapper,
            Validator validator) {
        this.items = items;
        this.queries = queries;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<ItemDto> list(long userId, String category, String status, String color, String q, String sort) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        String categoryCode = parse(category, "category", errors, v -> Category.valueOf(v).name());
        String statusCode = parse(status, "status", errors, v -> ItemStatus.valueOf(v).name());
        String colorCode = parse(color, "color", errors, v -> ColorKey.fromCode(v).code());
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
        WardrobeItem item = new WardrobeItem(userId);
        ItemMapping.apply(item, input);
        return ItemMapping.toDto(items.saveAndFlush(item));
    }

    /** 요청 순서대로 저장하고 같은 순서로 돌려준다. 하나라도 틀리면 전부 저장하지 않는다(@Valid에서 먼저 걸러짐). */
    @Transactional
    public List<ItemDto> bulkCreate(long userId, List<ItemInput> inputs) {
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

    private interface Parser {
        String apply(String value);
    }

    private static String parse(String raw, String field, List<ErrorResponse.FieldError> errors, Parser parser) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return parser.apply(value.strip());
        } catch (IllegalArgumentException e) {
            errors.add(new ErrorResponse.FieldError(field, "허용되지 않는 값이에요."));
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
