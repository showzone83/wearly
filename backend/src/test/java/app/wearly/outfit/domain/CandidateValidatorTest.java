package app.wearly.outfit.domain;

import static app.wearly.outfit.domain.OutfitFixtures.candidate;
import static app.wearly.outfit.domain.OutfitFixtures.request;
import static app.wearly.outfit.domain.OutfitFixtures.response;
import static app.wearly.outfit.domain.OutfitFixtures.wardrobe;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 09 문서 3장 후보 검증 규칙 (Spring 없음) */
class CandidateValidatorTest {

  final OutfitPool pool = OutfitPool.of(request(), wardrobe());

  @Test
  void 정상_후보는_칸_순서로_받고_이유는_다듬고_점수는_0에서_100_정수로_맞춘다() {
    CandidateValidator.Result r =
        CandidateValidator.validate(
            response(
                candidate(
                    Map.of("TOP", "2", "BOTTOM", "3", "OUTER", "6", "SHOES", "7", "BAG", "9")),
                candidate(Map.of("DRESS", "5", "SHOES", "8"))),
            pool);
    assertThat(r.rejections()).isEmpty();
    assertThat(r.accepted()).hasSize(2);
    OutfitCandidate first = r.accepted().getFirst();
    assertThat(first.items())
        .containsExactly(
            Map.entry("TOP", "2"),
            Map.entry("BOTTOM", "3"),
            Map.entry("OUTER", "6"),
            Map.entry("SHOES", "7"),
            Map.entry("BAG", "9"));
    assertThat(first.aiScores()).isEqualTo(new AiScores(80, 75, 100, 0, 60));
    assertThat(r.accepted().get(1).items()).containsOnlyKeys("DRESS", "SHOES");

    Map<String, Object> longReason = candidate(Map.of("TOP", "1", "BOTTOM", "4", "SHOES", "7"));
    longReason.put("reason", "  가".repeat(100));
    String reason =
        CandidateValidator.validate(response(longReason), pool).accepted().getFirst().reason();
    assertThat(reason.codePointCount(0, reason.length())).isLessThanOrEqualTo(80);
    assertThat(reason).doesNotContain("  ");
  }

  @Test
  void 남의_아이템이나_없는_id는_버린다() {
    assertRejected(Map.of("TOP", "999", "BOTTOM", "3", "SHOES", "7"), "내 옷장에 없어요");
  }

  @Test
  void 칸이_아이템_종류의_칸과_다르면_버린다() {
    // 하의(3)를 상의 칸에, 액세서리(11)를 가방 칸에
    assertRejected(Map.of("TOP", "3", "BOTTOM", "4", "SHOES", "7"), "칸에 들어갈 수 없어요");
    assertRejected(Map.of("TOP", "1", "BOTTOM", "4", "SHOES", "7", "BAG", "11"), "칸에 들어갈 수 없어요");
  }

  @Test
  void 상의_하의와_원피스를_섞거나_몸판이_모자라면_버린다() {
    assertRejected(Map.of("TOP", "1", "BOTTOM", "3", "DRESS", "5", "SHOES", "7"), "원피스 하나");
    assertRejected(Map.of("TOP", "1", "DRESS", "5", "SHOES", "7"), "원피스 하나");
    assertRejected(Map.of("TOP", "1", "SHOES", "7"), "원피스 하나");
  }

  @Test
  void 신발이_없으면_버린다() {
    assertRejected(Map.of("TOP", "1", "BOTTOM", "3"), "신발이 없어요");
  }

  @Test
  void 고정이나_중심_아이템이_빠지면_버린다() {
    OutfitPool fixed =
        OutfitPool.of(request(Map.of("TOP", "2"), "6", Set.of(), List.of(), 5), wardrobe());
    CandidateValidator.Result r =
        CandidateValidator.validate(
            response(
                candidate(Map.of("TOP", "1", "BOTTOM", "3", "OUTER", "6", "SHOES", "7")),
                candidate(Map.of("TOP", "2", "BOTTOM", "3", "SHOES", "7")),
                candidate(Map.of("TOP", "2", "BOTTOM", "3", "OUTER", "6", "SHOES", "7"))),
            fixed);
    assertThat(r.accepted()).hasSize(1);
    assertThat(r.accepted().getFirst().items())
        .containsEntry("TOP", "2")
        .containsEntry("OUTER", "6");
    assertThat(r.rejections()).hasSize(2);
    assertThat(r.rejections().get(0)).contains("고정한 아이템이 아니에요");
    assertThat(r.rejections().get(1)).contains("중심 아이템이 빠졌어요");
  }

  @Test
  void 제외_목록_피하고_싶은_색_세탁_중인_옷이_들어가면_버린다() {
    OutfitPool p =
        OutfitPool.of(request(Map.of(), null, Set.of("2"), List.of("beige"), 5), wardrobe());
    assertThat(rejection(Map.of("TOP", "2", "BOTTOM", "3", "SHOES", "7"), p)).contains("제외한 아이템");
    assertThat(rejection(Map.of("TOP", "1", "BOTTOM", "4", "SHOES", "7"), p)).contains("피하고 싶은 색");
    assertThat(rejection(Map.of("TOP", "10", "BOTTOM", "3", "SHOES", "7"), p))
        .contains("추천할 수 없는 상태");

    // 고정한 아이템은 제외·색·상태와 관계없이 쓸 수 있다 (앱 규칙 엔진과 같다)
    OutfitPool pinned =
        OutfitPool.of(
            request(Map.of("TOP", "10"), null, Set.of("10"), List.of("pink"), 5), wardrobe());
    assertThat(
            CandidateValidator.validate(
                    response(candidate(Map.of("TOP", "10", "BOTTOM", "3", "SHOES", "7"))), pinned)
                .accepted())
        .hasSize(1);
  }

  @Test
  void 같은_조합은_한_번만_받고_요청_수까지만_받는다() {
    Map<String, String> a = Map.of("TOP", "1", "BOTTOM", "3", "SHOES", "7");
    CandidateValidator.Result r =
        CandidateValidator.validate(
            response(
                candidate(a),
                candidate(a),
                candidate(Map.of("TOP", "2", "BOTTOM", "3", "SHOES", "7")),
                candidate(Map.of("TOP", "2", "BOTTOM", "4", "SHOES", "7"))),
            OutfitPool.of(request(Map.of(), null, Set.of(), List.of(), 2), wardrobe()));
    assertThat(r.accepted()).hasSize(2);
    assertThat(r.rejections()).singleElement().asString().contains("같은 조합");
  }

  @Test
  @SuppressWarnings("unchecked")
  void 형식이_틀린_후보와_응답은_버린다() {
    Map<String, Object> noReason = candidate(Map.of("TOP", "1", "BOTTOM", "3", "SHOES", "7"));
    noReason.put("reason", " ");
    Map<String, Object> badScores = candidate(Map.of("TOP", "1", "BOTTOM", "3", "SHOES", "7"));
    badScores.put("aiScores", Map.of("color", 1));
    Map<String, Object> unknownSlot = candidate(Map.of("TOP", "1", "BOTTOM", "3", "SHOES", "7"));
    ((Map<String, Object>) unknownSlot.get("items")).put("HAT", "11");
    CandidateValidator.Result r =
        CandidateValidator.validate(response(noReason, badScores, unknownSlot, "문자열"), pool);
    assertThat(r.accepted()).isEmpty();
    assertThat(r.rejections()).hasSize(4);
    assertThat(CandidateValidator.validate(Map.of("candidates", "x"), pool).rejections())
        .containsExactly("candidates가 배열이 아니에요.");
  }

  private void assertRejected(Map<String, String> items, String why) {
    assertThat(rejection(items, pool)).contains(why);
  }

  private static String rejection(Map<String, String> items, OutfitPool p) {
    CandidateValidator.Result r = CandidateValidator.validate(response(candidate(items)), p);
    assertThat(r.accepted()).isEmpty();
    return r.rejections().getFirst();
  }
}
