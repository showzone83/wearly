package app.wearly.wardrobe.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.application.Patch;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.image.application.port.in.ItemImageUseCase;
import app.wearly.image.application.port.in.dto.ItemImageRefDto;
import app.wearly.sharedkernel.code.CodeChecks;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import app.wearly.wardrobe.application.port.in.dto.CreateItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemSearchDto;
import app.wearly.wardrobe.application.port.in.dto.PatchItemDto;
import app.wearly.wardrobe.application.port.in.dto.RetireItemDto;
import app.wearly.wardrobe.application.port.out.ItemFilter;
import app.wearly.wardrobe.application.port.out.ItemPort;
import app.wearly.wardrobe.application.port.out.ItemQueryPort;
import app.wearly.wardrobe.application.port.out.dto.CodeCountDto;
import app.wearly.wardrobe.application.port.out.dto.KeyCountDto;
import app.wearly.wardrobe.domain.WardrobeItem;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 목록 필터 검증, 등록·PATCH 병합과 코드 검증 (가짜 포트, Spring 없이) */
class WardrobeServiceTest {

  static final long USER = 7L;

  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T01:02:03.456Z"), ZoneOffset.UTC);

  /** 저장 시각: 2026-03-01 16:30 UTC = 서울 2026-03-02 */
  static final Instant CREATED = Instant.parse("2026-03-01T16:30:00Z");

  /** CLOCK의 서울 날짜 */
  static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

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
                  item.subColors(),
                  item.pattern(),
                  item.seasons(),
                  item.styles(),
                  item.formality(),
                  item.status(),
                  item.brand(),
                  item.wornCount(),
                  item.lastWornAt(),
                  item.purchasedAt(),
                  item.purchasedAtEstimated(),
                  item.createdAt() == null ? CREATED : item.createdAt(),
                  item.retirement());
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
        public long countRetired(long userId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<KeyCountDto> countRetiredByReason(long userId) {
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

  /** 사진이 없는 아이템만 다루는 테스트용 (사진 붙이기는 통합 테스트 ItemImageIntegrationTest) */
  final ItemImageUseCase noImages =
      new ItemImageUseCase() {
        @Override
        public ItemImageRefDto attach(long userId, long itemId, String imageId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void detach(long userId, long itemId) {}

        @Override
        public Optional<ItemImageRefDto> find(long userId, long itemId) {
          return Optional.empty();
        }

        @Override
        public Map<Long, ItemImageRefDto> findAll(long userId, Collection<Long> itemIds) {
          return Map.of();
        }
      };

  final WardrobeService service = new WardrobeService(items, queries, () -> codes, noImages, CLOCK);

  @Test
  void 목록_필터는_모르는_값만_거부하고_비활성_코드는_허용한다() {
    assertThatThrownBy(
            () ->
                service.list(new ItemSearchDto(USER, "HAT", null, "fuchsia", null, "price", null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .extracting(FieldError::field)
                    .containsExactly("category", "color", "sort"));
    assertThat(filters).isEmpty();

    service.list(new ItemSearchDto(USER, " TOP ", "", "navy", " 100%_셔츠 ", null, null));
    assertThat(filters)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.userId()).isEqualTo(USER);
              assertThat(f.category()).isEqualTo("TOP");
              assertThat(f.status()).isNull();
              assertThat(f.color()).isEqualTo("navy");
              assertThat(f.sort()).isEqualTo("recent");
              // LIKE 패턴은 쿼리 어댑터가 만든다 (L3). 포트에는 원래 검색어가 간다
              assertThat(f.query()).isEqualTo("100%_셔츠");
            });
  }

  @Test
  void 등록은_불변식과_코드를_검증하고_통과해야_저장한다() {
    assertThatThrownBy(() -> service.create(USER, create("navy")))
        .isInstanceOfSatisfying(
            BusinessException.class,
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
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .containsExactly(new FieldError("color", CodeChecks.INACTIVE)));
    assertThat(rows.get(other).color()).isEqualTo("white");
  }

  @Test
  void PATCH_입력_오류는_다른_검증보다_먼저_그대로_돌려준다() {
    long id = givenItemWithColor("white");
    FieldError typeError = FieldError.invalidFormat("formality");
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
            Patch.absent(),
            Patch.absent(),
            Patch.absent(),
            List.of(typeError));

    assertThatThrownBy(() -> service.patch(USER, id, bad))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.fieldErrors()).containsExactly(typeError));
  }

  @Test
  void 다른_회원의_아이템은_없는_것처럼_404() {
    long id = givenItemWithColor("white");

    assertThatThrownBy(() -> service.patch(99L, id, patch(Patch.of("x"), Patch.absent())))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    assertThatThrownBy(() -> service.delete(99L, id)).isInstanceOf(BusinessException.class);
    assertThat(rows).containsKey(id);
  }

  @Test
  void 목록은_기본으로_사용_중인_옷만_보고_retired로_고를_수_있다() {
    service.list(new ItemSearchDto(USER, null, null, null, null, null, null));
    service.list(new ItemSearchDto(USER, null, null, null, null, null, " true "));
    service.list(new ItemSearchDto(USER, null, null, null, null, null, "all"));
    service.list(new ItemSearchDto(USER, null, null, null, null, null, "false"));
    assertThat(filters)
        .extracting(ItemFilter::retired)
        .containsExactly(Boolean.FALSE, Boolean.TRUE, null, Boolean.FALSE);

    assertThatThrownBy(
            () -> service.list(new ItemSearchDto(USER, null, null, null, null, null, "yes")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .extracting(FieldError::field)
                    .containsExactly("retired"));
  }

  @Test
  void 사용_중지는_아이템을_먼저_찾고_이유_코드를_검증한_뒤_저장한다() {
    long id = givenItemWithColor("white");

    assertThatThrownBy(() -> service.retire(99L, id, new RetireItemDto("BAD", null)))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    assertThatThrownBy(() -> service.retire(USER, id, new RetireItemDto("LOST", "x".repeat(201))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .extracting(FieldError::field)
                    .containsExactlyInAnyOrder("reason", "memo"));
    assertThatThrownBy(() -> service.retire(USER, id, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .extracting(FieldError::field)
                    .containsExactly("reason"));
    assertThat(rows.get(id).isRetired()).isFalse();

    ItemDto retired = service.retire(USER, id, new RetireItemDto("DONATED", " 기부 "));
    assertThat(retired.retiredAt()).isEqualTo(CLOCK.instant());
    assertThat(retired.retireReason()).isEqualTo("DONATED");
    assertThat(retired.retireMemo()).isEqualTo("기부");
    assertThat(retired.status()).isEqualTo("OWNED");

    ItemDto restored = service.restore(USER, id);
    assertThat(restored.retiredAt()).isNull();
    assertThat(restored.retireReason()).isNull();
    assertThat(restored.retireMemo()).isNull();
    assertThat(rows.get(id).isRetired()).isFalse();
    // 사용 중이면 그대로 200
    assertThat(service.restore(USER, id).retiredAt()).isNull();
    assertThatThrownBy(() -> service.restore(99L, id)).isInstanceOf(BusinessException.class);
  }

  @Test
  void 사용_중지한_옷도_PATCH로_고칠_수_있다() {
    long id = givenItemWithColor("white");
    service.retire(USER, id, new RetireItemDto("NOT_WORN", null));

    ItemDto patched = service.patch(USER, id, patch(Patch.of("기록용 이름"), Patch.absent()));

    assertThat(patched.name()).isEqualTo("기록용 이름");
    assertThat(patched.retireReason()).isEqualTo("NOT_WORN");
  }

  @Test
  void 색은_colors가_예전_color보다_우선이고_첫_색이_주_색상() {
    // 둘 다 오면 colors를 쓴다 (비활성 navy는 무시된다)
    ItemDto created = service.create(USER, create("navy", List.of("white", "gray", "black"), null));
    assertThat(created.color()).isEqualTo("white");
    assertThat(created.colors()).containsExactly("white", "gray", "black");

    // 예전 color 하나도 받는다
    ItemDto legacy = service.create(USER, create("gray", null, null));
    assertThat(legacy.colors()).containsExactly("gray");

    assertThatThrownBy(
            () -> service.create(USER, create(null, List.of("white", "gray", "white"), null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .containsExactly(new FieldError("colors[2]", FieldErrorCode.DUPLICATE)));
    assertThatThrownBy(() -> service.create(USER, create(null, List.of("white", "navy"), null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .containsExactly(new FieldError("colors[1]", CodeChecks.INACTIVE)));
    assertThatThrownBy(() -> service.create(USER, create(null, null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.fieldErrors()).containsExactly(FieldError.required("colors")));
  }

  @Test
  void PATCH의_예전_color는_주_색상만_바꾸고_colors는_전부_바꾼다() {
    long id = service.create(USER, create(null, List.of("white", "gray", "black"), null)).id();

    ItemDto primary = service.patch(USER, id, patch(b -> b.color = Patch.of("black")));
    assertThat(primary.colors()).containsExactly("black", "gray");
    assertThat(primary.color()).isEqualTo("black");

    ItemDto replaced =
        service.patch(
            USER,
            id,
            patch(
                b -> {
                  b.colors = Patch.of(List.of("gray", "white"));
                  b.color = Patch.of("black");
                }));
    assertThat(replaced.colors()).containsExactly("gray", "white");

    // 저장돼 있던 비활성 색은 보조 색 자리에 그대로 둘 수 있다
    long old = givenItemWithColor("navy");
    ItemDto withSub =
        service.patch(USER, old, patch(b -> b.colors = Patch.of(List.of("white", "navy"))));
    assertThat(withSub.colors()).containsExactly("white", "navy");

    assertThatThrownBy(() -> service.patch(USER, id, patch(b -> b.color = Patch.of(null))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.fieldErrors()).containsExactly(FieldError.required("color")));
  }

  @Test
  void 구입일을_비우면_등록일로_넣고_추정_표시한다() {
    ItemDto unknown = service.create(USER, create("white", null, null));
    assertThat(unknown.purchasedAt()).isEqualTo(TODAY);
    assertThat(unknown.purchasedAtEstimated()).isTrue();

    LocalDate bought = LocalDate.of(2024, 5, 1);
    ItemDto known = service.patch(USER, unknown.id(), patch(b -> b.purchasedAt = Patch.of(bought)));
    assertThat(known.purchasedAt()).isEqualTo(bought);
    assertThat(known.purchasedAtEstimated()).isFalse();

    // 다른 필드만 고치면 구입일은 그대로
    assertThat(
            service
                .patch(USER, unknown.id(), patch(Patch.of("새 이름"), Patch.absent()))
                .purchasedAt())
        .isEqualTo(bought);

    // null을 보내면 모름 → 등록일(저장 시각의 서울 날짜)
    ItemDto reset = service.patch(USER, unknown.id(), patch(b -> b.purchasedAt = Patch.of(null)));
    assertThat(reset.purchasedAt()).isEqualTo(LocalDate.of(2026, 3, 2));
    assertThat(reset.purchasedAtEstimated()).isTrue();

    assertThatThrownBy(() -> service.create(USER, create("white", null, TODAY.plusDays(1))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .containsExactly(
                        new FieldError(
                            "purchasedAt",
                            FieldErrorCode.OUT_OF_RANGE,
                            Map.of("min", "1990-01-01", "max", "2026-10-17"))));
    assertThat(service.create(USER, create("white", null, TODAY)).purchasedAtEstimated()).isFalse();
  }

  private long givenItemWithColor(String color) {
    // 비활성이 되기 전에 저장된 아이템
    return items.save(WardrobeItem.create(USER, create(color).toInput(), TODAY)).id();
  }

  private static CreateItemDto create(String color) {
    return create(color, null, null);
  }

  private static CreateItemDto create(String color, List<String> colors, LocalDate purchasedAt) {
    return new CreateItemDto(
        "화이트 셔츠",
        "TOP",
        "SHIRT",
        color,
        colors,
        "SOLID",
        List.of("SPRING"),
        List.of("NEAT"),
        2,
        "OWNED",
        null,
        null,
        null,
        purchasedAt,
        null);
  }

  private static PatchItemDto patch(Patch<String> name, Patch<String> color) {
    return patch(
        b -> {
          b.name = name;
          b.color = color;
        });
  }

  /** 보낼 필드만 채운 PATCH (나머지는 안 보냄) */
  private static PatchItemDto patch(java.util.function.Consumer<PatchBuilder> fill) {
    PatchBuilder b = new PatchBuilder();
    fill.accept(b);
    return new PatchItemDto(
        b.name,
        Patch.absent(),
        Patch.absent(),
        b.color,
        b.colors,
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        b.purchasedAt,
        Patch.absent(),
        List.of());
  }

  private static final class PatchBuilder {
    Patch<String> name = Patch.absent();
    Patch<String> color = Patch.absent();
    Patch<List<String>> colors = Patch.absent();
    Patch<LocalDate> purchasedAt = Patch.absent();
  }
}
