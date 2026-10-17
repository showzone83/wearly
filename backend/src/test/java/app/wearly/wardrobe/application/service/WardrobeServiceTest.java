package app.wearly.wardrobe.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.application.Patch;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.sharedkernel.code.CodeChecks;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import app.wearly.wardrobe.application.port.in.dto.CreateItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemSearchDto;
import app.wearly.wardrobe.application.port.in.dto.PatchItemDto;
import app.wearly.wardrobe.application.port.out.ItemFilter;
import app.wearly.wardrobe.application.port.out.ItemPort;
import app.wearly.wardrobe.application.port.out.ItemQueryPort;
import app.wearly.wardrobe.application.port.out.dto.CodeCountDto;
import app.wearly.wardrobe.application.port.out.dto.KeyCountDto;
import app.wearly.wardrobe.domain.WardrobeItem;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 목록 필터 검증, 등록·PATCH 병합과 코드 검증 (가짜 포트, Spring 없이) */
class WardrobeServiceTest {

  static final long USER = 7L;

  /** navy는 비활성 (이미 저장된 값이면 그대로 둘 수 있다) */
  final CodeSnapshot codes = TestCodes.withInactive("COLOR:navy");

  final Map<Long, WardrobeItem> rows = new LinkedHashMap<>();
  final List<ItemFilter> filters = new ArrayList<>();

  final ItemPort items =
      new ItemPort() {
        @Override
        public Optional<WardrobeItem> find(long userId, long itemId) {
          return Optional.ofNullable(rows.get(itemId)).filter(i -> i.userId() == userId);
        }

        @Override
        public WardrobeItem save(WardrobeItem item) {
          long id = item.id() == null ? rows.size() + 1L : item.id();
          WardrobeItem saved =
              new WardrobeItem(
                  id,
                  item.userId(),
                  item.name(),
                  item.category(),
                  item.subType(),
                  item.color(),
                  item.pattern(),
                  item.seasons(),
                  item.styles(),
                  item.formality(),
                  item.status(),
                  item.brand(),
                  item.wornCount(),
                  item.lastWornAt(),
                  Instant.EPOCH);
          rows.put(id, saved);
          return saved;
        }

        @Override
        public void delete(WardrobeItem item) {
          rows.remove(item.id());
        }
      };

  final ItemQueryPort queries =
      new ItemQueryPort() {
        @Override
        public List<WardrobeItem> findItems(ItemFilter filter) {
          filters.add(filter);
          return List.copyOf(rows.values());
        }

        @Override
        public long countAll(long userId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<KeyCountDto> countByCategory(long userId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<KeyCountDto> countByColor(long userId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<KeyCountDto> countByStyle(long userId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<KeyCountDto> countByStatus(long userId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public long countWornSince(long userId, LocalDate since) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<Long> findSleepingIds(long userId, LocalDate before, int limit) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<CodeCountDto> countByCode() {
          throw new UnsupportedOperationException();
        }

        @Override
        public long countUsing(String group, String code) {
          throw new UnsupportedOperationException();
        }
      };

  final WardrobeService service = new WardrobeService(items, queries, () -> codes);

  @Test
  void 목록_필터는_모르는_값만_거부하고_비활성_코드는_허용한다() {
    assertThatThrownBy(
            () -> service.list(new ItemSearchDto(USER, "HAT", null, "fuchsia", null, "price")))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.fieldErrors())
                    .extracting(FieldError::field)
                    .containsExactly("category", "color", "sort"));
    assertThat(filters).isEmpty();

    service.list(new ItemSearchDto(USER, " TOP ", "", "navy", "  ", null));
    assertThat(filters)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.userId()).isEqualTo(USER);
              assertThat(f.category()).isEqualTo("TOP");
              assertThat(f.status()).isNull();
              assertThat(f.color()).isEqualTo("navy");
              assertThat(f.sort()).isEqualTo("recent");
            });
  }

  @Test
  void 등록은_불변식과_코드를_검증하고_통과해야_저장한다() {
    assertThatThrownBy(() -> service.create(USER, create("navy")))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.fieldErrors())
                    .containsExactly(new FieldError("color", CodeChecks.INACTIVE)));
    assertThat(rows).isEmpty();

    ItemDto created = service.create(USER, create("white"));
    assertThat(created.name()).isEqualTo("화이트 셔츠");
    assertThat(created.wornCount()).isZero();
  }

  @Test
  void PATCH는_보낸_필드만_바꾸고_저장돼_있던_비활성_코드는_그대로_둔다() {
    long id = givenItemWithColor("navy");

    ItemDto patched = service.patch(USER, id, patch(Patch.of("새 이름"), Patch.absent()));

    assertThat(patched.name()).isEqualTo("새 이름");
    assertThat(patched.color()).isEqualTo("navy");
    assertThat(patched.category()).isEqualTo("TOP");

    // 새로 고르는 값은 활성이어야 한다
    long other = givenItemWithColor("white");
    assertThatThrownBy(() -> service.patch(USER, other, patch(Patch.absent(), Patch.of("navy"))))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.fieldErrors())
                    .containsExactly(new FieldError("color", CodeChecks.INACTIVE)));
    assertThat(rows.get(other).color()).isEqualTo("white");
  }

  @Test
  void PATCH_입력_오류는_다른_검증보다_먼저_그대로_돌려준다() {
    long id = givenItemWithColor("white");
    FieldError typeError = new FieldError("formality", "숫자여야 해요.");
    PatchItemDto bad =
        new PatchItemDto(
            Patch.absent(),
            Patch.absent(),
            Patch.absent(),
            Patch.of("fuchsia"),
            Patch.absent(),
            Patch.absent(),
            Patch.absent(),
            Patch.absent(),
            Patch.absent(),
            Patch.absent(),
            Patch.absent(),
            Patch.absent(),
            List.of(typeError));

    assertThatThrownBy(() -> service.patch(USER, id, bad))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.fieldErrors()).containsExactly(typeError));
  }

  @Test
  void 다른_회원의_아이템은_없는_것처럼_404() {
    long id = givenItemWithColor("white");

    assertThatThrownBy(() -> service.patch(99L, id, patch(Patch.of("x"), Patch.absent())))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    assertThatThrownBy(() -> service.delete(99L, id)).isInstanceOf(ApiException.class);
    assertThat(rows).containsKey(id);
  }

  private long givenItemWithColor(String color) {
    // 비활성이 되기 전에 저장된 아이템
    return items.save(WardrobeItem.create(USER, create(color).toInput())).id();
  }

  private static CreateItemDto create(String color) {
    return new CreateItemDto(
        "화이트 셔츠",
        "TOP",
        "SHIRT",
        color,
        "SOLID",
        List.of("SPRING"),
        List.of("NEAT"),
        2,
        "OWNED",
        null,
        null,
        null);
  }

  private static PatchItemDto patch(Patch<String> name, Patch<String> color) {
    return new PatchItemDto(
        name,
        Patch.absent(),
        Patch.absent(),
        color,
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        List.of());
  }
}
