package app.wearly.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 예정 입력 규칙 (Spring 없이): 필수·형식·범위·코드, PATCH의 바꾸지 않은 값, 코디 JSON, 저장 값 정리 */
class PlanRulesTest {

  static final LocalDate TODAY = LocalDate.parse("2026-10-18");

  /** MINIMAL·GYEONGGI는 비활성 (이미 저장된 값이면 그대로 둘 수 있다) */
  final CodeSnapshot codes = TestCodes.withInactive("STYLE:MINIMAL", "REGION:GYEONGGI");

  static PlanDraft draft() {
    return new PlanDraft(
        "plan-abc",
        "2026-10-25",
        "PM",
        " 결혼식 하객 ",
        "WEDDING",
        List.of("CLASSIC"),
        "  ",
        new PlanDraft.Place("BUSAN", " 부산 ", 35.18, 129.08),
        new PlanDraft.Weather(18.5, 12.0, 22.0, 30.0, "SUNNY", null),
        "CLIMATE",
        "2026-10-18T09:00:00+09:00",
        null,
        outfit(Map.of("TOP", "42", "BOTTOM", "43")));
  }

  static Map<String, Object> outfit(Map<String, Object> items) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", "outfit-1");
    m.put("items", items);
    m.put("score", 87);
    m.put("reasons", List.of("색 조합이 좋아요"));
    m.put("ai", null);
    return m;
  }

  List<FieldError> validate(PlanDraft d) {
    return PlanRules.validate(d, null, TODAY, codes, "", false);
  }

  static List<String> fields(List<FieldError> errors) {
    return errors.stream().map(e -> e.field() + ":" + e.code()).toList();
  }

  @Test
  void 올바른_입력은_오류가_없고_저장_값으로_정리한다() {
    PlanDraft d = draft();
    assertThat(validate(d)).isEmpty();

    PlanInput in = PlanRules.toInput(d);
    assertThat(in.date()).isEqualTo(LocalDate.parse("2026-10-25"));
    assertThat(in.title()).isEqualTo("결혼식 하객");
    assertThat(in.memo()).as("빈 메모는 null").isNull();
    assertThat(in.place()).isEqualTo(new PlanPlace("BUSAN", "부산", 35.18, 129.08));
    assertThat(in.weather().manual()).isFalse();
    assertThat(in.weather().source()).isEqualTo("CLIMATE");
    assertThat(in.weather().checkedAt()).isEqualTo(Instant.parse("2026-10-18T00:00:00Z"));
    assertThat(in.forecastReady()).isFalse();
    assertThat(in.outfit()).containsEntry("ai", null).containsEntry("score", 87);
  }

  @Test
  void 필수_값이_없으면_REQUIRED() {
    PlanDraft d =
        new PlanDraft(null, null, null, " ", null, null, null, null, null, null, null, null, null);

    assertThat(fields(validate(d)))
        .containsExactly(
            "date:REQUIRED",
            "timeOfDay:REQUIRED",
            "title:REQUIRED",
            "occasion:REQUIRED",
            "weather:REQUIRED");
    assertThat(fields(PlanRules.validate(d, null, TODAY, codes, "items[2].", true)))
        .contains("items[2].clientId:REQUIRED", "items[2].date:REQUIRED");
  }

  @Test
  void 날짜는_형식과_앞뒤_1년_범위를_본다() {
    assertThat(validate(withDate("2026-13-01")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(FieldErrorCode.INVALID_FORMAT);
              assertThat(e.params()).containsEntry("format", "YYYY-MM-DD");
            });
    assertThat(validate(withDate("2025-10-18"))).isEmpty();
    assertThat(validate(withDate("2027-10-18"))).isEmpty();
    FieldError past = validate(withDate("2025-10-17")).getFirst();
    assertThat(past.code()).isEqualTo(FieldErrorCode.OUT_OF_RANGE);
    assertThat(past.params()).containsEntry("min", "2025-10-18").containsEntry("max", "2027-10-18");
    assertThat(fields(validate(withDate("2027-10-19")))).containsExactly("date:OUT_OF_RANGE");

    // PATCH: 날짜를 바꾸지 않았으면 범위 밖이어도 둔다
    PlanDraft old = withDate("2024-01-01");
    assertThat(PlanRules.validate(old, old, TODAY, codes, "", false)).isEmpty();
  }

  @Test
  void 고정_목록과_길이를_본다() {
    PlanDraft d = draft();
    PlanDraft bad =
        new PlanDraft(
            "bad id!",
            d.date(),
            "NIGHT",
            "가".repeat(51),
            "PARTY",
            d.concepts(),
            "메".repeat(501),
            d.place(),
            new PlanDraft.Weather(61.0, -61.0, 10.0, 101.0, "SNOW", true),
            "GUESS",
            "어제",
            true,
            d.outfit());

    List<FieldError> errors = validate(bad);
    assertThat(fields(errors))
        .containsExactly(
            "clientId:INVALID_FORMAT",
            "timeOfDay:NOT_ALLOWED",
            "title:TOO_LONG",
            "occasion:NOT_ALLOWED",
            "memo:TOO_LONG",
            "weather.temp:OUT_OF_RANGE",
            "weather.min:OUT_OF_RANGE",
            "weather.rainProb:OUT_OF_RANGE",
            "weather.cond:NOT_ALLOWED",
            "weatherSource:NOT_ALLOWED",
            "weatherCheckedAt:INVALID_FORMAT");
    assertThat(errors.get(1).params()).containsEntry("allowed", List.of("AM", "PM", "EVENING"));
    assertThat(errors.get(2).params()).containsEntry("max", 50);
    assertThat(errors.get(5).params()).containsEntry("min", -60).containsEntry("max", 60);
  }

  @Test
  void 컨셉은_STYLE_코드이고_중복과_비활성을_거른다() {
    PlanDraft d = withConcepts(List.of("CLASSIC", "NOPE", "CLASSIC", "MINIMAL"));
    assertThat(fields(validate(d)))
        .containsExactly(
            "concepts[2]:DUPLICATE", "concepts[1]:NOT_ALLOWED", "concepts[3]:INACTIVE");
    // 저장된 값에 있던 비활성 코드는 둔다
    PlanDraft previous = withConcepts(List.of("MINIMAL"));
    assertThat(
            PlanRules.validate(
                withConcepts(List.of("MINIMAL", "NEAT")), previous, TODAY, codes, "", false))
        .isEmpty();
    List<String> many = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      many.add("C" + i);
    }
    assertThat(fields(validate(withConcepts(many)))).containsExactly("concepts:TOO_MANY");
  }

  @Test
  void 장소는_이름_좌표_REGION_코드를_본다() {
    assertThat(fields(validate(withPlace(new PlanDraft.Place(null, "도쿄", 35.7, 139.7))))).isEmpty();
    assertThat(fields(validate(withPlace(new PlanDraft.Place("NOWHERE", "", 91.0, null)))))
        .containsExactly(
            "place.label:REQUIRED",
            "place.lat:OUT_OF_RANGE",
            "place.lon:REQUIRED",
            "place.regionCode:NOT_ALLOWED");
    assertThat(fields(validate(withPlace(new PlanDraft.Place("GYEONGGI", "경기", 37.2, 127.0)))))
        .containsExactly("place.regionCode:INACTIVE");
    assertThat(validate(withPlace(null))).isEmpty();
  }

  @Test
  void 코디는_items의_칸과_아이템_ID를_본다() {
    Map<String, Object> items = new HashMap<>();
    items.put("TOP", "42");
    items.put("HAT", "1");
    items.put("SHOES", 7);
    items.put("BAG", "x".repeat(65));
    assertThat(fields(validate(withOutfit(outfit(items)))))
        .containsExactlyInAnyOrder(
            "outfit.items.HAT:NOT_ALLOWED",
            "outfit.items.SHOES:INVALID_TYPE",
            "outfit.items.BAG:TOO_LONG");
    assertThat(fields(validate(withOutfit(Map.of("score", 1)))))
        .containsExactly("outfit.items:REQUIRED");
    assertThat(fields(validate(withOutfit(Map.of("items", List.of())))))
        .containsExactly("outfit.items:INVALID_TYPE");
    Map<String, Object> huge = outfit(Map.of("TOP", "1"));
    huge.put("reasons", List.of("가".repeat(PlanOutfits.MAX_JSON_LENGTH)));
    assertThat(fields(validate(withOutfit(huge)))).containsExactly("outfit:TOO_LONG");
  }

  @Test
  void 코디_아이템_중_옷장에_없는_ID를_찾는다() {
    Map<String, Object> o = outfit(Map.of("BOTTOM", "43", "TOP", "42", "SHOES", "item-local"));

    assertThat(PlanOutfits.itemIds(o)).containsExactly("42", "43", "item-local");
    assertThat(PlanOutfits.missing(o, List.of(42L))).containsExactly("43", "item-local");
    assertThat(PlanOutfits.serverItemId("042")).isEqualTo(42L);
    assertThat(PlanOutfits.serverItemId("0")).isNull();
    assertThat(PlanOutfits.serverItemId("-1")).isNull();
    assertThat(PlanOutfits.serverItemId("99999999999999999999")).isNull();
    assertThat(PlanOutfits.itemIds(null)).isEmpty();
  }

  @Test
  void 저장된_예정을_입력_모양으로_되돌리면_같은_값이_된다() {
    PlanInput in = PlanRules.toInput(draft());
    Plan plan = new Plan(1L, 7L, "plan-abc", in, Instant.EPOCH, Instant.EPOCH);

    PlanDraft back = plan.toDraft();
    assertThat(validate(back)).isEmpty();
    assertThat(PlanRules.toInput(back)).isEqualTo(in);
  }

  private static PlanDraft withDate(String date) {
    PlanDraft d = draft();
    return new PlanDraft(
        d.clientId(),
        date,
        d.timeOfDay(),
        d.title(),
        d.occasion(),
        d.concepts(),
        d.memo(),
        d.place(),
        d.weather(),
        d.weatherSource(),
        d.weatherCheckedAt(),
        d.forecastReady(),
        d.outfit());
  }

  private static PlanDraft withConcepts(List<String> concepts) {
    PlanDraft d = draft();
    return new PlanDraft(
        d.clientId(),
        d.date(),
        d.timeOfDay(),
        d.title(),
        d.occasion(),
        concepts,
        d.memo(),
        d.place(),
        d.weather(),
        d.weatherSource(),
        d.weatherCheckedAt(),
        d.forecastReady(),
        d.outfit());
  }

  private static PlanDraft withPlace(PlanDraft.Place place) {
    PlanDraft d = draft();
    return new PlanDraft(
        d.clientId(),
        d.date(),
        d.timeOfDay(),
        d.title(),
        d.occasion(),
        d.concepts(),
        d.memo(),
        place,
        d.weather(),
        d.weatherSource(),
        d.weatherCheckedAt(),
        d.forecastReady(),
        d.outfit());
  }

  private static PlanDraft withOutfit(Map<String, Object> outfit) {
    PlanDraft d = draft();
    return new PlanDraft(
        d.clientId(),
        d.date(),
        d.timeOfDay(),
        d.title(),
        d.occasion(),
        d.concepts(),
        d.memo(),
        d.place(),
        d.weather(),
        d.weatherSource(),
        d.weatherCheckedAt(),
        d.forecastReady(),
        outfit);
  }
}
