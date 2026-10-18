package app.wearly.outfit.domain;

import static app.wearly.outfit.domain.OutfitFixtures.CODES;
import static app.wearly.outfit.domain.OutfitFixtures.item;
import static app.wearly.outfit.domain.OutfitFixtures.request;
import static app.wearly.outfit.domain.OutfitFixtures.wardrobe;
import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.FieldErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 프롬프트·스키마 (공급자와 무관, Spring 없음) */
class OutfitPromptTest {

  final OutfitTaste taste =
      new OutfitTaste(List.of("BIZ_CASUAL"), List.of("NEAT", "MINIMAL"), List.of("navy"));

  @Test
  void 칸별_enum은_그_칸에_쓸_수_있는_내_아이템만이고_세탁_중인_옷은_빠진다() {
    Map<String, Object> schema = OutfitPrompt.schema(OutfitPool.of(request(), wardrobe()));
    Map<String, Object> slots = slots(schema);
    assertThat(slots).containsOnlyKeys("TOP", "BOTTOM", "DRESS", "OUTER", "SHOES", "BAG");
    // 상의: 1·2 (세탁 중 10 제외), 원피스가 가능하므로 null도 허용
    assertThat(enumOf(slots.get("TOP"))).containsExactly("1", "2");
    assertThat(nullable(slots.get("TOP"))).isTrue();
    assertThat(enumOf(slots.get("BOTTOM"))).containsExactly("3", "4");
    assertThat(enumOf(slots.get("DRESS"))).containsExactly("5");
    assertThat(enumOf(slots.get("OUTER"))).containsExactly("6");
    // 신발은 필수 (null 불가)
    assertThat(enumOf(slots.get("SHOES"))).containsExactly("7", "8");
    assertThat(nullable(slots.get("SHOES"))).isFalse();
    assertThat(enumOf(slots.get("BAG"))).containsExactly("9");
    // 액세서리(11)는 어느 칸에도 없다
    assertThat(schema.toString()).doesNotContain("\"11\"").doesNotContain("enum=[11");

    // 구조화 출력 공통 규칙: 모든 객체 additionalProperties=false, 모든 필드 required
    assertThat(schema).containsEntry("additionalProperties", false);
    assertThat(schema.get("required")).isEqualTo(List.of("candidates"));
    Map<String, Object> candidate = candidateSchema(schema);
    assertThat(candidate.get("required")).isEqualTo(List.of("items", "reason", "aiScores"));
    Map<String, Object> items = props(candidate).get("items");
    assertThat(items.get("required"))
        .isEqualTo(List.of("TOP", "BOTTOM", "DRESS", "OUTER", "SHOES", "BAG"));
    Map<String, Object> scores = props(candidate).get("aiScores");
    assertThat(props(scores))
        .containsOnlyKeys("color", "occasion", "weather", "taste", "diversity");
    assertThat(scores).containsEntry("additionalProperties", false);
  }

  @Test
  void 다른_회원의_옷은_스키마와_프롬프트에_없다() {
    // 회원의 옷장만 넘기므로 다른 회원 옷 id(500)는 어디에도 나오지 않는다
    OutfitPool pool = OutfitPool.of(request(), wardrobe());
    assertThat(OutfitPrompt.schema(pool).toString()).doesNotContain("500");
    assertThat(OutfitPrompt.prompt(pool, taste, CODES)).doesNotContain("id 500");
  }

  @Test
  void 고정하면_그_칸은_그_아이템_하나이고_원피스_고정이면_상의_하의는_null만() {
    Map<String, Object> slots =
        slots(
            OutfitPrompt.schema(
                OutfitPool.of(
                    request(Map.of("TOP", "2"), null, Set.of(), List.of(), 5), wardrobe())));
    assertThat(enumOf(slots.get("TOP"))).containsExactly("2");
    assertThat(nullable(slots.get("TOP"))).isFalse();
    assertThat(nullable(slots.get("BOTTOM"))).isFalse();
    assertThat(slots.get("DRESS")).isEqualTo(Map.of("type", "null"));

    Map<String, Object> dress =
        slots(
            OutfitPrompt.schema(
                OutfitPool.of(request(Map.of(), "5", Set.of(), List.of(), 5), wardrobe())));
    assertThat(dress.get("TOP")).isEqualTo(Map.of("type", "null"));
    assertThat(dress.get("BOTTOM")).isEqualTo(Map.of("type", "null"));
    assertThat(enumOf(dress.get("DRESS"))).containsExactly("5");
  }

  @Test
  void 제외_목록과_피하고_싶은_색은_목록에서_빠지고_없는_칸은_null만() {
    List<OutfitItem> items =
        List.of(
            item("1", "TOP", "SHIRT", "white"),
            item("2", "TOP", "KNIT", "red"),
            item("3", "BOTTOM", "SLACKS", "charcoal"),
            item("7", "SHOES", "LOAFER", "black"),
            item("8", "SHOES", "SNEAKER", "white"));
    Map<String, Object> slots =
        slots(
            OutfitPrompt.schema(
                OutfitPool.of(request(Map.of(), null, Set.of("8"), List.of("red"), 5), items)));
    assertThat(enumOf(slots.get("TOP"))).containsExactly("1");
    // 원피스가 없으니 상의·하의는 필수
    assertThat(nullable(slots.get("TOP"))).isFalse();
    assertThat(enumOf(slots.get("SHOES"))).containsExactly("7");
    assertThat(slots.get("DRESS")).isEqualTo(Map.of("type", "null"));
    assertThat(slots.get("OUTER")).isEqualTo(Map.of("type", "null"));
    assertThat(slots.get("BAG")).isEqualTo(Map.of("type", "null"));
  }

  @Test
  void 프롬프트에는_조건_취향_점수_기준_색_짝_칸별_옷_목록이_들어간다() {
    OutfitPool pool =
        OutfitPool.of(request(Map.of("TOP", "2"), null, Set.of(), List.of(), 3), wardrobe());
    String prompt = OutfitPrompt.prompt(pool, taste, CODES);
    assertThat(prompt)
        .contains("출근(COMMUTE)")
        .contains("비즈니스 캐주얼(BIZ_CASUAL)")
        .contains("목표 아이템 격식 2")
        .contains("단정(NEAT)")
        .contains("14°C (최저 8 / 최고 19)")
        .contains("아우터 필요")
        .contains("고정: TOP=2")
        .contains("후보 수: 3")
        .contains("선호 색: 네이비(navy)")
        .contains("- id 2 | \"옷2\" | 니트(KNIT) | 색 navy(네이비, 뉴트럴")
        .contains("[고정]")
        .contains("[TOP]")
        .contains("[SHOES]")
        // 색 짝은 내 옷에 있는 색끼리만
        .contains("- navy: beige");
    // 세탁 중(10)·액세서리(11)는 목록에 없다
    assertThat(prompt).doesNotContain("id 10 ").doesNotContain("id 11 ");
    assertThat(OutfitPrompt.instructions())
        .contains("색상 조화 30%")
        .contains("상황 적합 25%")
        .contains("날씨 적합 20%")
        .contains("개인 취향 15%")
        .contains("다양성 10%")
        .contains("신발(SHOES)은 반드시");
  }

  @Test
  void 옷이_모자란_칸을_칸_코드로_알려_준다() {
    assertThat(OutfitPool.of(request(), wardrobe()).missing()).isEmpty();
    List<OutfitItem> topsOnly =
        List.of(
            item("1", "TOP", "SHIRT", "white"), item("10", "BOTTOM", "SLACKS", "black", "LAUNDRY"));
    assertThat(OutfitPool.of(request(), topsOnly).missing())
        .containsExactly("BOTTOM 또는 DRESS", "SHOES");
    assertThat(OutfitPool.of(request(), List.of()).missing())
        .containsExactly("TOP·BOTTOM 또는 DRESS", "SHOES");
    // 상의를 고정했으면 원피스로 대신할 수 없다
    List<OutfitItem> withDress =
        List.of(
            item("1", "TOP", "SHIRT", "white"),
            item("5", "DRESS", "DRESS", "black"),
            item("7", "SHOES", "LOAFER", "black"));
    assertThat(
            OutfitPool.of(request(Map.of("TOP", "1"), null, Set.of(), List.of(), 5), withDress)
                .missing())
        .containsExactly("BOTTOM");
    assertThat(OutfitPool.of(request(), withDress).missing()).isEmpty();
  }

  @Test
  void 고정_중심_아이템_확인() {
    assertThat(
            OutfitPool.checkPins(
                request(Map.of("TOP", "999"), null, Set.of(), List.of(), 5), wardrobe()))
        .singleElement()
        .satisfies(e -> assertThat(e.field()).isEqualTo("context.fixed.TOP"));
    assertThat(
            OutfitPool.checkPins(
                request(Map.of("TOP", "3"), null, Set.of(), List.of(), 5), wardrobe()))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.code()).isEqualTo(FieldErrorCode.SLOT_MISMATCH);
              assertThat(e.params()).containsEntry("slot", "TOP");
            });
    assertThat(OutfitPool.checkPins(request(Map.of(), "11", Set.of(), List.of(), 5), wardrobe()))
        .singleElement()
        .satisfies(e -> assertThat(e.field()).isEqualTo("context.anchorItemId"));
    assertThat(
            OutfitPool.checkPins(
                request(Map.of("TOP", "1"), "5", Set.of(), List.of(), 5), wardrobe()))
        .singleElement()
        .satisfies(e -> assertThat(e.field()).isEqualTo("context.fixed"));
    assertThat(
            OutfitPool.checkPins(
                request(Map.of("TOP", "1"), "6", Set.of(), List.of(), 5), wardrobe()))
        .isEmpty();
  }

  @Test
  void 사용_중지한_옷을_고정_중심으로_주면_사용하지_않는_옷이라고_알려_준다() {
    // 사용 중지한 옷(99, 98)은 후보 목록(wardrobe)에 없고 retiredIds로만 온다 (13 문서 3장)
    assertThat(
            OutfitPool.checkPins(
                request(Map.of("TOP", "99"), "98", Set.of(), List.of(), 5),
                wardrobe(),
                Set.of("99", "98")))
        .extracting(e -> e.field() + "=" + e.code())
        .containsExactly("context.fixed.TOP=ITEM_RETIRED", "context.anchorItemId=ITEM_RETIRED");
    // 내 옷이 아닌 id는 그대로 "없는 아이템"
    assertThat(
            OutfitPool.checkPins(
                request(Map.of("TOP", "999"), null, Set.of(), List.of(), 5),
                wardrobe(),
                Set.of("99")))
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo(FieldErrorCode.NOT_OWNED));
  }

  @Test
  void 목업은_규칙에_맞는_후보를_결정적으로_만든다() {
    OutfitPool pool = OutfitPool.of(request(Map.of(), "6", Set.of(), List.of(), 5), wardrobe());
    Map<String, Object> a = MockOutfitPlanner.plan(pool);
    assertThat(a)
        .isEqualTo(
            MockOutfitPlanner.plan(
                OutfitPool.of(request(Map.of(), "6", Set.of(), List.of(), 5), wardrobe())));
    CandidateValidator.Result r = CandidateValidator.validate(a, pool);
    assertThat(r.rejections()).isEmpty();
    assertThat(r.accepted()).hasSize(5);
    assertThat(r.accepted()).allSatisfy(c -> assertThat(c.items()).containsEntry("OUTER", "6"));
    assertThat(r.accepted().stream().map(OutfitCandidate::items).distinct()).hasSize(5);
    // 원피스 몸판도 후보에 들어간다 (상의×하의 4개 다음)
    assertThat(r.accepted().get(4).items()).containsKey("DRESS");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> props(Map<String, Object> object) {
    return (Map<String, Map<String, Object>>) object.get("properties");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> candidateSchema(Map<String, Object> schema) {
    return (Map<String, Object>) props(schema).get("candidates").get("items");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> slots(Map<String, Object> schema) {
    return (Map<String, Object>) props(candidateSchema(schema)).get("items").get("properties");
  }

  /** {type:string, enum} 또는 {anyOf:[{type:string, enum}, {type:null}]}의 id 목록 */
  @SuppressWarnings("unchecked")
  private static List<String> enumOf(Object slot) {
    Map<String, Object> m = (Map<String, Object>) slot;
    if (m.containsKey("anyOf")) {
      m = (Map<String, Object>) ((List<Object>) m.get("anyOf")).getFirst();
    }
    return (List<String>) m.get("enum");
  }

  @SuppressWarnings("unchecked")
  private static boolean nullable(Object slot) {
    return ((Map<String, Object>) slot).containsKey("anyOf");
  }
}
