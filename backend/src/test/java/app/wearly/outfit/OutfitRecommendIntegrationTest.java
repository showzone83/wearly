package app.wearly.outfit;

import static app.wearly.outfit.OutfitTestData.PATH;
import static app.wearly.outfit.OutfitTestData.context;
import static app.wearly.outfit.OutfitTestData.item;
import static app.wearly.outfit.OutfitTestData.request;
import static app.wearly.outfit.OutfitTestData.wardrobe;
import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 09 문서 3장 POST /outfits/recommendations (목업 실행). 목업 공급자는 규칙에 맞는 후보를 결정적으로 만들고, 서버 검증을 그대로 거친다. 한도는
 * IntegrationTestBase의 wearly.ai.outfit-daily-limit=3.
 */
class OutfitRecommendIntegrationTest extends IntegrationTestBase {

  static final Set<String> SLOTS = Set.of("TOP", "BOTTOM", "DRESS", "OUTER", "SHOES", "BAG");

  @Autowired JdbcTemplate jdbc;

  @Test
  void 목업_응답은_계약_형식이고_규칙에_맞으며_결정적이다() {
    Session s = signup();
    final Map<String, String> ids = wardrobe(api(), s.accessToken());
    Resp r = api().post(PATH, s.accessToken(), request(context())).expect(200);
    JsonNode body = r.body();
    assertThat(body.get("provider").asString()).isEqualTo("mock");
    assertThat(body.get("model").isNull()).isTrue();
    assertThat(body.get("remainingToday").asInt()).isEqualTo(2);
    JsonNode candidates = body.get("candidates");
    assertThat(candidates).hasSize(5);
    Set<String> combos = new HashSet<>();
    for (JsonNode c : candidates) {
      JsonNode items = c.get("items");
      List<String> slots = new ArrayList<>();
      items.propertyNames().forEach(slots::add);
      assertThat(SLOTS).containsAll(slots);
      // 빈 칸은 키가 없다 (null을 주지 않는다)
      items.forEach(v -> assertThat(v.isString()).isTrue());
      boolean separates = items.has("TOP") && items.has("BOTTOM") && !items.has("DRESS");
      boolean dress = items.has("DRESS") && !items.has("TOP") && !items.has("BOTTOM");
      assertThat(separates || dress).isTrue();
      assertThat(items.has("SHOES")).isTrue();
      // 세탁 중인 옷은 들어가지 않는다
      assertThat(items.toString()).doesNotContain("\"" + ids.get("핑크 블라우스") + "\"");
      // 날씨(14°C)가 아우터를 원한다
      assertThat(items.get("OUTER").asString()).isEqualTo(ids.get("네이비 블레이저"));
      assertThat(c.get("reason").asString()).isNotBlank().hasSizeLessThanOrEqualTo(80);
      JsonNode scores = c.get("aiScores");
      for (String f : List.of("color", "occasion", "weather", "taste", "diversity")) {
        assertThat(scores.get(f).isInt()).isTrue();
        assertThat(scores.get(f).asInt()).isBetween(0, 100);
      }
      assertThat(combos.add(items.toString())).isTrue();
    }
    assertThat(ids.values()).containsAll(itemIds(candidates));

    // 같은 옷장·같은 요청 = 같은 후보
    JsonNode again = api().post(PATH, s.accessToken(), request(context())).expect(200).body();
    assertThat(again.get("candidates")).isEqualTo(candidates);
    assertThat(again.get("remainingToday").asInt()).isEqualTo(1);

    // 기록: feature=OUTFIT_RECOMMEND, 이미지 칸은 NULL, 결과 JSON에 후보
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select feature, provider, status, image_sha256, image_bytes, result::text as result"
                + " from tb_ai_analysis_log where user_id = ? order by ai_analysis_log_id",
            Long.parseLong(s.userId()));
    assertThat(rows).hasSize(2);
    assertThat(rows.getFirst())
        .containsEntry("feature", "OUTFIT_RECOMMEND")
        .containsEntry("provider", "mock")
        .containsEntry("status", "SUCCESS")
        .containsEntry("image_sha256", null)
        .containsEntry("image_bytes", null);
    assertThat((String) rows.getFirst().get("result"))
        .contains("\"candidates\"")
        .contains("\"reason\"")
        .contains("\"aiScores\"");

    // 관리자 AI 기록: feature 검색과 요약
    String admin = admin().accessToken();
    JsonNode logs =
        api()
            .get(
                "/api/v1/admin/ai/logs?feature=OUTFIT_RECOMMEND&userEmail="
                    + s.auth().get("user").get("email").asString(),
                admin)
            .expect(200)
            .body();
    assertThat(logs.get("total").asLong()).isEqualTo(2);
    assertThat(logs.get("items").get(0).get("feature").asString()).isEqualTo("OUTFIT_RECOMMEND");
    assertThat(logs.get("items").get(0).get("resultSummary").asString()).isEqualTo("코디 후보 5개");
    JsonNode detail =
        api()
            .get("/api/v1/admin/ai/logs/" + logs.get("items").get(0).get("id").asString(), admin)
            .expect(200)
            .body();
    assertThat(detail.get("result").get("candidates")).hasSize(5);
    api().get("/api/v1/admin/ai/logs?feature=NOPE", admin).expectError(400, "VALIDATION_FAILED");
    // 대시보드 오늘 AI 호출에 코디 추천도 들어간다
    assertThat(
            api()
                .get("/api/v1/admin/dashboard", admin)
                .body()
                .get("aiToday")
                .get("success")
                .asLong())
        .isGreaterThanOrEqualTo(2);
  }

  @Test
  void 고정_중심_제외_피하고_싶은_색_후보_수를_지킨다() {
    Session s = signup();
    Map<String, String> ids = wardrobe(api(), s.accessToken());
    Map<String, Object> ctx = context();
    ctx.put("fixed", Map.of("SHOES", ids.get("화이트 스니커즈")));
    ctx.put("anchorItemId", ids.get("네이비 니트"));
    ctx.put("avoidColors", List.of("beige"));
    Map<String, Object> req = request(ctx);
    req.put("excludeItemIds", List.of(ids.get("네이비 블레이저")));
    req.put("count", 2);
    req.put(
        "recent", List.of(Map.of("date", "2026-10-16", "items", Map.of("TOP", ids.get("화이트 셔츠")))));
    req.put("likedItemIds", List.of(ids.get("차콜 슬랙스"), "999999"));
    JsonNode candidates =
        api().post(PATH, s.accessToken(), req).expect(200).body().get("candidates");
    assertThat(candidates).hasSize(1); // 니트 × 슬랙스 하나뿐 (와이드는 베이지라 빠짐, 원피스는 상의 중심이라 빠짐)
    JsonNode items = candidates.get(0).get("items");
    assertThat(items.get("TOP").asString()).isEqualTo(ids.get("네이비 니트"));
    assertThat(items.get("BOTTOM").asString()).isEqualTo(ids.get("차콜 슬랙스"));
    assertThat(items.get("SHOES").asString()).isEqualTo(ids.get("화이트 스니커즈"));
    assertThat(items.has("OUTER")).isFalse();
  }

  @Test
  void 옷이_모자라면_AI를_부르지_않고_400_WARDROBE_INSUFFICIENT() {
    Session s = signup();
    OutfitTestData.create(
        api(),
        s.accessToken(),
        List.of(
            item("화이트 셔츠", "TOP", "SHIRT", "white"),
            item("세탁 중 슬랙스", "BOTTOM", "SLACKS", "black", "LAUNDRY")));
    Resp r =
        api()
            .post(PATH, s.accessToken(), request(context()))
            .expectError(400, "WARDROBE_INSUFFICIENT");
    assertThat(r.body().get("message").asString()).contains("BOTTOM 또는 DRESS").contains("SHOES");
    Integer logs =
        jdbc.queryForObject(
            "select count(*) from tb_ai_analysis_log where user_id = ?",
            Integer.class,
            Long.parseLong(s.userId()));
    assertThat(logs).isZero();
  }

  @Test
  void 코디_추천_한도는_사진_분석과_따로_세고_넘으면_429() {
    Session s = signup();
    wardrobe(api(), s.accessToken());
    for (int i = 2; i >= 0; i--) {
      assertThat(
              api()
                  .post(PATH, s.accessToken(), request(context()))
                  .expect(200)
                  .body()
                  .get("remainingToday")
                  .asInt())
          .isEqualTo(i);
    }
    Resp r =
        api().post(PATH, s.accessToken(), request(context())).expectError(429, "AI_LIMIT_EXCEEDED");
    assertThat(r.body().get("message").asString()).contains("코디 추천");
    // 사진 분석 한도(3)는 그대로
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 7, 7, 7};
    assertThat(
            api()
                .upload(
                    "/api/v1/wardrobe/items/analyze",
                    s.accessToken(),
                    "image",
                    jpeg,
                    "a.jpg",
                    "image/jpeg")
                .expect(200)
                .body()
                .get("remainingToday")
                .asInt())
        .isEqualTo(2);
    // 429는 기록하지 않는다 (코디 추천 성공 3건만)
    Integer outfitLogs =
        jdbc.queryForObject(
            "select count(*) from tb_ai_analysis_log"
                + " where user_id = ? and feature = 'OUTFIT_RECOMMEND'",
            Integer.class,
            Long.parseLong(s.userId()));
    assertThat(outfitLogs).isEqualTo(3);
  }

  @Test
  void 요청_검증과_인증() {
    api().post(PATH, null, request(context())).expectError(401, "UNAUTHORIZED");
    Session s = signup();
    final Map<String, String> ids = wardrobe(api(), s.accessToken());
    Session other = signup();
    final Map<String, String> otherIds = wardrobe(api(), other.accessToken());

    Map<String, Object> bad = context();
    bad.put("occasion", "PARTY");
    bad.put("date", "2026/10/17");
    bad.put("weather", Map.of("temp", 14, "min", 8, "max", 19, "rainProb", 120, "cond", "SNOW"));
    bad.put("avoidColors", List.of("fuchsia"));
    bad.put("memo", "가".repeat(201));
    Map<String, Object> req = request(bad);
    req.put("count", 7);
    Resp r = api().post(PATH, s.accessToken(), req).expectError(400, "VALIDATION_FAILED");
    assertThat(fields(r))
        .contains(
            "context.occasion",
            "context.date",
            "context.weather.rainProb",
            "context.weather.cond",
            "context.avoidColors",
            "context.memo",
            "count");

    api().post(PATH, s.accessToken(), Map.of()).expectError(400, "VALIDATION_FAILED");

    // 남의 옷을 고정·중심으로 쓸 수 없다
    Map<String, Object> foreign = context();
    foreign.put("fixed", Map.of("TOP", otherIds.get("화이트 셔츠")));
    foreign.put("anchorItemId", otherIds.get("블랙 로퍼"));
    Resp f =
        api().post(PATH, s.accessToken(), request(foreign)).expectError(400, "VALIDATION_FAILED");
    assertThat(fields(f)).containsExactlyInAnyOrder("context.fixed.TOP", "context.anchorItemId");

    // 모르는 칸은 요청 검증에서, 칸이 맞지 않는 고정은 옷장을 읽은 뒤 막는다
    Map<String, Object> unknownSlot = context();
    unknownSlot.put("fixed", Map.of("HAT", ids.get("브라운 토트")));
    Resp u =
        api()
            .post(PATH, s.accessToken(), request(unknownSlot))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(fields(u)).containsExactly("context.fixed.HAT");
    Map<String, Object> wrongSlot = context();
    wrongSlot.put("fixed", Map.of("BOTTOM", ids.get("화이트 셔츠")));
    Resp w =
        api().post(PATH, s.accessToken(), request(wrongSlot)).expectError(400, "VALIDATION_FAILED");
    assertThat(fields(w)).containsExactly("context.fixed.BOTTOM");
  }

  private static List<String> fields(Resp r) {
    List<String> out = new ArrayList<>();
    r.body().get("fieldErrors").forEach(e -> out.add(e.get("field").asString()));
    return out;
  }

  private static Set<String> itemIds(JsonNode candidates) {
    Set<String> out = new HashSet<>();
    candidates.forEach(c -> c.get("items").forEach(v -> out.add(v.asString())));
    return out;
  }
}
