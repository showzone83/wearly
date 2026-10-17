package app.wearly.wardrobe.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.code.CodeGroups;
import app.wearly.common.domain.code.CodeSnapshot;
import app.wearly.common.error.ApiException;
import app.wearly.wardrobe.application.port.in.WardrobeUseCase;
import app.wearly.wardrobe.application.port.in.dto.CreateItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemSearchDto;
import app.wearly.wardrobe.application.port.in.dto.PatchItemDto;
import app.wearly.wardrobe.application.port.out.ItemFilter;
import app.wearly.wardrobe.application.port.out.ItemPort;
import app.wearly.wardrobe.application.port.out.ItemQueryPort;
import app.wearly.wardrobe.domain.ItemCodeValidator;
import app.wearly.wardrobe.domain.ItemInput;
import app.wearly.wardrobe.domain.ItemRules;
import app.wearly.wardrobe.domain.WardrobeItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 옷장 쓰기·단건 조회(JPA)와 목록 조회(MyBatis) */
@Service
public class WardrobeService implements WardrobeUseCase {

  private static final Set<String> SORTS = Set.of("recent", "worn", "name");

  private final ItemPort items;
  private final ItemQueryPort queries;
  private final CodeCatalog catalog;

  public WardrobeService(ItemPort items, ItemQueryPort queries, CodeCatalog catalog) {
    this.items = items;
    this.queries = queries;
    this.catalog = catalog;
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
      errors.add(new FieldError("sort", "recent, worn, name 중 하나여야 해요."));
    }
    ApiException.throwIfAny(errors);
    String query = blankToNull(q.q());
    String pattern = query == null ? null : "%" + escapeLike(query.strip()) + "%";
    return queries
        .findItems(
            new ItemFilter(q.userId(), categoryCode, statusCode, colorCode, pattern, sortKey))
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
    ApiException.throwIfAny(ItemRules.validate(input, ""));
    List<FieldError> errors = new ArrayList<>();
    ItemCodeValidator.validate(catalog.current(), input, null, "", errors);
    ApiException.throwIfAny(errors);
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
    ApiException.throwIfAny(invalid);
    List<FieldError> errors = new ArrayList<>();
    for (int i = 0; i < inputs.size(); i++) {
      ItemCodeValidator.validate(codes, inputs.get(i), null, "items[" + i + "].", errors);
    }
    ApiException.throwIfAny(errors);
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
    ApiException.throwIfAny(patch.inputErrors());
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
    ApiException.throwIfAny(ItemRules.validate(input, ""));
    List<FieldError> errors = new ArrayList<>();
    ItemCodeValidator.validate(catalog.current(), input, previous, "", errors);
    ApiException.throwIfAny(errors);
    item.apply(input);
    return ItemDto.from(items.save(item));
  }

  @Override
  @Transactional
  public void delete(long userId, long itemId) {
    items.delete(find(userId, itemId));
  }

  private WardrobeItem find(long userId, long itemId) {
    return items.find(userId, itemId).orElseThrow(ApiException::notFound);
  }

  private static String filterCode(
      CodeSnapshot codes, String group, String raw, String field, List<FieldError> errors) {
    String value = blankToNull(raw);
    if (value == null) {
      return null;
    }
    if (!codes.known(group, value.strip())) {
      errors.add(new FieldError(field, "허용되지 않는 값이에요."));
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
