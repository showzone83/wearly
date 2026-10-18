package app.wearly.wardrobe.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.BusinessException;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.wardrobe.application.port.in.WardrobeUseCase;
import app.wearly.wardrobe.application.port.in.dto.CreateItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemSearchDto;
import app.wearly.wardrobe.application.port.in.dto.PatchItemDto;
import app.wearly.wardrobe.application.port.in.dto.RetireItemDto;
import app.wearly.wardrobe.application.port.out.ItemFilter;
import app.wearly.wardrobe.application.port.out.ItemPort;
import app.wearly.wardrobe.application.port.out.ItemQueryPort;
import app.wearly.wardrobe.domain.ItemCodeValidator;
import app.wearly.wardrobe.domain.ItemInput;
import app.wearly.wardrobe.domain.ItemRules;
import app.wearly.wardrobe.domain.RetireRules;
import app.wearly.wardrobe.domain.WardrobeItem;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 옷장 쓰기·단건 조회(JPA)와 목록 조회(MyBatis), 사용 중지·다시 사용 (13 문서) */
@Service
public class WardrobeService implements WardrobeUseCase {

  private static final List<String> SORTS = List.of("recent", "worn", "name");

  private final ItemPort items;
  private final ItemQueryPort queries;
  private final CodeCatalog catalog;
  private final Clock clock;

  public WardrobeService(ItemPort items, ItemQueryPort queries, CodeCatalog catalog, Clock clock) {
    this.items = items;
    this.queries = queries;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ItemDto> list(ItemSearchDto q) {
    List<FieldError> errors = new ArrayList<>();
    CodeSnapshot codes = catalog.current();
    // 필터는 저장된 값을 찾는 것이라 비활성 코드도 허용한다. 모르는 값만 400
    String categoryCode = filterCode(codes, CodeGroups.CATEGORY, q.category(), "category", errors);
    String statusCode = filterCode(codes, CodeGroups.ITEM_STATUS, q.status(), "status", errors);
    String colorCode = filterCode(codes, CodeGroups.COLOR, q.color(), "color", errors);
    String sortKey = blankToNull(q.sort()) == null ? "recent" : q.sort().strip();
    if (!SORTS.contains(sortKey)) {
      errors.add(FieldError.notAllowed("sort", SORTS));
    }
    Boolean retired = retiredFilter(q.retired(), errors);
    BusinessException.throwIfAny(errors);
    String query = blankToNull(q.q());
    return queries
        .findItems(
            new ItemFilter(
                q.userId(),
                categoryCode,
                statusCode,
                colorCode,
                query == null ? null : query.strip(),
                sortKey,
                retired))
        .stream()
        .map(ItemDto::from)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ItemDto get(long userId, long itemId) {
    return ItemDto.from(find(userId, itemId));
  }

  @Override
  @Transactional
  public ItemDto create(long userId, CreateItemDto dto) {
    ItemInput input = dto.toInput();
    BusinessException.throwIfAny(ItemRules.validate(input, ""));
    List<FieldError> errors = new ArrayList<>();
    ItemCodeValidator.validate(catalog.current(), input, null, "", errors);
    BusinessException.throwIfAny(errors);
    return ItemDto.from(items.save(WardrobeItem.create(userId, input)));
  }

  @Override
  @Transactional
  public List<ItemDto> bulkCreate(long userId, List<CreateItemDto> dtos) {
    List<ItemInput> inputs = dtos.stream().map(CreateItemDto::toInput).toList();
    CodeSnapshot codes = catalog.current();
    List<FieldError> invalid = new ArrayList<>();
    for (int i = 0; i < inputs.size(); i++) {
      invalid.addAll(ItemRules.validate(inputs.get(i), "items[" + i + "]."));
    }
    BusinessException.throwIfAny(invalid);
    List<FieldError> errors = new ArrayList<>();
    for (int i = 0; i < inputs.size(); i++) {
      ItemCodeValidator.validate(codes, inputs.get(i), null, "items[" + i + "].", errors);
    }
    BusinessException.throwIfAny(errors);
    List<ItemDto> created = new ArrayList<>(inputs.size());
    for (ItemInput input : inputs) {
      created.add(ItemDto.from(items.save(WardrobeItem.create(userId, input))));
    }
    return created;
  }

  @Override
  @Transactional
  public ItemDto patch(long userId, long itemId, PatchItemDto patch) {
    WardrobeItem item = find(userId, itemId);
    BusinessException.throwIfAny(patch.inputErrors());
    ItemInput previous = item.toInput();
    ItemInput input =
        new ItemInput(
            patch.name().orElse(previous.name()),
            patch.category().orElse(previous.category()),
            patch.subType().orElse(previous.subType()),
            patch.color().orElse(previous.color()),
            patch.pattern().orElse(previous.pattern()),
            patch.seasons().orElse(previous.seasons()),
            patch.styles().orElse(previous.styles()),
            patch.formality().orElse(previous.formality()),
            patch.status().orElse(previous.status()),
            patch.brand().orElse(previous.brand()),
            patch.wornCount().orElse(previous.wornCount()),
            patch.lastWornAt().orElse(previous.lastWornAt()));
    BusinessException.throwIfAny(ItemRules.validate(input, ""));
    List<FieldError> errors = new ArrayList<>();
    ItemCodeValidator.validate(catalog.current(), input, previous, "", errors);
    BusinessException.throwIfAny(errors);
    item.apply(input);
    return ItemDto.from(items.save(item));
  }

  @Override
  @Transactional
  public void delete(long userId, long itemId) {
    items.delete(find(userId, itemId));
  }

  @Override
  @Transactional
  public ItemDto retire(long userId, long itemId, RetireItemDto input) {
    WardrobeItem item = find(userId, itemId);
    String reason = input == null ? null : input.reason();
    String memo = input == null ? null : input.memo();
    BusinessException.throwIfAny(RetireRules.validate(catalog.current(), reason, memo));
    item.retire(reason, memo, clock.instant());
    return ItemDto.from(items.save(item));
  }

  @Override
  @Transactional
  public ItemDto restore(long userId, long itemId) {
    WardrobeItem item = find(userId, itemId);
    if (!item.isRetired()) {
      return ItemDto.from(item);
    }
    item.restore();
    return ItemDto.from(items.save(item));
  }

  private WardrobeItem find(long userId, long itemId) {
    return items.find(userId, itemId).orElseThrow(BusinessException::notFound);
  }

  private static String filterCode(
      CodeSnapshot codes, String group, String raw, String field, List<FieldError> errors) {
    String value = blankToNull(raw);
    if (value == null) {
      return null;
    }
    if (!codes.known(group, value.strip())) {
      errors.add(FieldError.notAllowed(field));
      return null;
    }
    return value.strip();
  }

  /** retired 쿼리: 없으면 false(사용 중인 옷만), true(사용 중지한 옷만), all(전부 → null) */
  private static Boolean retiredFilter(String raw, List<FieldError> errors) {
    String value = blankToNull(raw);
    if (value == null) {
      return Boolean.FALSE;
    }
    return switch (value.strip()) {
      case "false" -> Boolean.FALSE;
      case "true" -> Boolean.TRUE;
      case ItemSearchDto.RETIRED_ALL -> null;
      default -> {
        errors.add(
            FieldError.notAllowed("retired", List.of("false", "true", ItemSearchDto.RETIRED_ALL)));
        yield Boolean.FALSE;
      }
    };
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
