package app.wearly.wardrobe.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.code.CodeGroups;
import app.wearly.common.domain.code.CodeSnapshot;
import app.wearly.common.error.ApiException;
import app.wearly.wardrobe.application.port.in.ItemPatch;
import app.wearly.wardrobe.application.port.in.ItemView;
import app.wearly.wardrobe.application.port.in.ListItemsQuery;
import app.wearly.wardrobe.application.port.in.WardrobeUseCase;
import app.wearly.wardrobe.application.port.out.ItemFilter;
import app.wearly.wardrobe.application.port.out.ItemPort;
import app.wearly.wardrobe.application.port.out.ItemQueryPort;
import app.wearly.wardrobe.domain.ItemCodeValidator;
import app.wearly.wardrobe.domain.ItemInput;
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
  public List<ItemView> list(ListItemsQuery q) {
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
        .map(ItemView::of)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ItemView get(long userId, long itemId) {
    return ItemView.of(find(userId, itemId));
  }

  @Override
  @Transactional
  public ItemView create(long userId, ItemInput input) {
    List<FieldError> errors = new ArrayList<>();
    ItemCodeValidator.validate(catalog.current(), input, null, "", errors);
    ApiException.throwIfAny(errors);
    return ItemView.of(items.save(WardrobeItem.create(userId, input)));
  }

  @Override
  @Transactional
  public List<ItemView> bulkCreate(long userId, List<ItemInput> inputs) {
    CodeSnapshot codes = catalog.current();
    List<FieldError> errors = new ArrayList<>();
    for (int i = 0; i < inputs.size(); i++) {
      ItemCodeValidator.validate(codes, inputs.get(i), null, "items[" + i + "].", errors);
    }
    ApiException.throwIfAny(errors);
    List<ItemView> created = new ArrayList<>(inputs.size());
    for (ItemInput input : inputs) {
      created.add(ItemView.of(items.save(WardrobeItem.create(userId, input))));
    }
    return created;
  }

  @Override
  @Transactional
  public ItemView patch(long userId, long itemId, ItemPatch patch) {
    WardrobeItem item = find(userId, itemId);
    ItemInput previous = item.toInput();
    ItemInput input = patch.applyTo(previous);
    List<FieldError> errors = new ArrayList<>();
    ItemCodeValidator.validate(catalog.current(), input, previous, "", errors);
    ApiException.throwIfAny(errors);
    item.apply(input);
    return ItemView.of(items.save(item));
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
