package app.wearly.wardrobe;

import static app.wearly.wardrobe.WardrobeIntegrationTest.item;
import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.code.CodeIntegrationTest;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** 사용하지 않는 옷 · 완전 삭제 (13 문서): 실제 PostgreSQL·HTTP로 확인 */
class WardrobeRetireIntegrationTest extends IntegrationTestBase {

  static final String ITEMS = "/api/v1/wardrobe/items";

  @Autowired JdbcTemplate jdbc;

  @Test
  void 사용_중지하면_목록_기본에서_빠지고_retired_true에_보이며_다시_사용하면_돌아온다() {
    Session s = signup();
    String t = s.accessToken();
    String knit = create(t, item("네이비 니트", "TOP", "KNIT", "navy"));
    String shirt = create(t, item("화이트 셔츠", "TOP", "SHIRT", "white"));

    // 처음 응답에는 세 필드가 null로 있다
    JsonNode fresh = api().get(ITEMS + "/" + knit, t).expect(200).body();
    assertThat(fresh.get("retiredAt").isNull()).isTrue();
    assertThat(fresh.get("retireReason").isNull()).isTrue();
    assertThat(fresh.get("retireMemo").isNull()).isTrue();

    JsonNode retired =
        api()
            .post(
                ITEMS + "/" + knit + "/retire", t, Map.of("reason", "DONATED", "memo", " 아름다운가게 "))
            .expect(200)
            .body();
    assertThat(retired.get("id").asString()).isEqualTo(knit);
    assertThat(retired.get("retireReason").asString()).isEqualTo("DONATED");
    assertThat(retired.get("retireMemo").asString()).isEqualTo("아름다운가게");
    assertThat(retired.get("status").asString()).isEqualTo("OWNED");
    String retiredAt = retired.get("retiredAt").asString();
    assertThat(retiredAt).endsWith("Z");
    assertThat(Instant.parse(retiredAt)).isBefore(Instant.now().plusSeconds(5));

    assertThat(ids(api().get(ITEMS, t).expect(200))).containsExactly(shirt);
    assertThat(ids(api().get(ITEMS + "?retired=false", t).expect(200))).containsExactly(shirt);
    assertThat(ids(api().get(ITEMS + "?retired=true", t).expect(200))).containsExactly(knit);
    assertThat(ids(api().get(ITEMS + "?retired=all", t).expect(200)))
        .containsExactlyInAnyOrder(knit, shirt);
    assertThat(ids(api().get(ITEMS + "?retired=true&category=TOP&q=니트", t).expect(200)))
        .containsExactly(knit);
    api().get(ITEMS + "?retired=maybe", t).expectError(400, "VALIDATION_FAILED");
    // 단건 조회는 사용 중지한 옷도 된다
    assertThat(api().get(ITEMS + "/" + knit, t).expect(200).body()).isEqualTo(retired);

    // 이미 사용 중지면 이유·메모만 바뀌고 시각은 그대로
    JsonNode again =
        api().post(ITEMS + "/" + knit + "/retire", t, Map.of("reason", "SOLD")).expect(200).body();
    assertThat(again.get("retiredAt").asString()).isEqualTo(retiredAt);
    assertThat(again.get("retireReason").asString()).isEqualTo("SOLD");
    assertThat(again.get("retireMemo").isNull()).isTrue();

    // 사용 중지한 옷도 PATCH로 고칠 수 있다
    JsonNode patched =
        api().patch(ITEMS + "/" + knit, t, Map.of("name", "기부한 니트")).expect(200).body();
    assertThat(patched.get("name").asString()).isEqualTo("기부한 니트");
    assertThat(patched.get("retireReason").asString()).isEqualTo("SOLD");

    JsonNode restored = api().post(ITEMS + "/" + knit + "/restore", t, null).expect(200).body();
    assertThat(restored.get("retiredAt").isNull()).isTrue();
    assertThat(restored.get("retireReason").isNull()).isTrue();
    assertThat(restored.get("retireMemo").isNull()).isTrue();
    assertThat(restored.get("status").asString()).isEqualTo("OWNED");
    // 사용 중이면 그대로 200
    assertThat(api().post(ITEMS + "/" + knit + "/restore", t, null).expect(200).body())
        .isEqualTo(restored);
    assertThat(ids(api().get(ITEMS, t).expect(200))).containsExactlyInAnyOrder(knit, shirt);
  }

  @Test
  void 통계는_사용_중인_옷만_세고_사용_중지는_따로_센다() {
    Session s = signup();
    String t = s.accessToken();
    Map<String, Object> worn = item("자주 입는 셔츠", "TOP", "SHIRT", "white");
    worn.put("lastWornAt", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString());
    create(t, worn);
    String b = create(t, item("블랙 슬랙스", "BOTTOM", "SLACKS", "black"));
    String c = create(t, item("베이지 코트", "OUTER", "COAT", "beige"));
    String d = create(t, item("브라운 로퍼", "SHOES", "LOAFER", "brown"));
    retire(t, b, "DONATED");
    retire(t, c, "DONATED");
    retire(t, d, "SOLD");

    JsonNode st = api().get("/api/v1/wardrobe/stats", t).expect(200).body();
    assertThat(st.get("total").asLong()).isEqualTo(1);
    assertThat(st.get("byCategory").get("TOP").asLong()).isEqualTo(1);
    assertThat(st.get("byCategory").get("BOTTOM").asLong()).isZero();
    assertThat(st.get("byStatus").get("OWNED").asLong()).isEqualTo(1);
    assertThat(st.get("byColor")).hasSize(1);
    assertThat(st.get("wornLast30Days").asLong()).isEqualTo(1);
    assertThat(st.get("utilization30d").asDouble()).isEqualTo(1.0);
    assertThat(st.get("sleeping")).isEmpty();
    assertThat(st.get("retired").asLong()).isEqualTo(3);
    assertThat(st.get("retiredByReason").toString())
        .isEqualTo("[{\"reason\":\"DONATED\",\"count\":2},{\"reason\":\"SOLD\",\"count\":1}]");

    api().post(ITEMS + "/" + d + "/restore", t, null).expect(200);
    JsonNode after = api().get("/api/v1/wardrobe/stats", t).expect(200).body();
    assertThat(after.get("total").asLong()).isEqualTo(2);
    assertThat(after.get("sleeping").toString()).isEqualTo("[\"" + d + "\"]");
    assertThat(after.get("retired").asLong()).isEqualTo(2);
    assertThat(after.get("retiredByReason")).hasSize(1);
  }

  @Test
  void 완전_삭제는_사용_중지와_달리_데이터가_사라진다() {
    Session s = signup();
    String t = s.accessToken();
    String kept = create(t, item("기부한 셔츠", "TOP", "SHIRT", "white"));
    String gone = create(t, item("버린 셔츠", "TOP", "SHIRT", "navy"));
    retire(t, kept, "DONATED");

    api().delete(ITEMS + "/" + gone, t).expect(204);
    api().get(ITEMS + "/" + gone, t).expectError(404, "NOT_FOUND");
    api()
        .post(ITEMS + "/" + gone + "/retire", t, Map.of("reason", "ETC"))
        .expectError(404, "NOT_FOUND");
    assertThat(ids(api().get(ITEMS + "?retired=all", t).expect(200))).containsExactly(kept);

    // 사용 중지한 옷도 완전 삭제할 수 있다
    api().delete(ITEMS + "/" + kept, t).expect(204);
    assertThat(ids(api().get(ITEMS + "?retired=all", t).expect(200))).isEmpty();
  }

  @Test
  void 남의_옷은_404이고_입력이_틀리면_400() {
    Session owner = signup();
    Session other = signup();
    String id = create(owner.accessToken(), item("셔츠", "TOP", "SHIRT", "white"));

    api()
        .post(ITEMS + "/" + id + "/retire", other.accessToken(), Map.of("reason", "ETC"))
        .expectError(404, "NOT_FOUND");
    api()
        .post(ITEMS + "/" + id + "/restore", other.accessToken(), null)
        .expectError(404, "NOT_FOUND");
    // 남의 옷이면 입력이 틀려도 404가 먼저
    api()
        .post(ITEMS + "/" + id + "/retire", other.accessToken(), Map.of("reason", "LOST"))
        .expectError(404, "NOT_FOUND");
    api().post(ITEMS + "/abc/retire", owner.accessToken(), Map.of("reason", "ETC")).expect(404);
    api()
        .post(ITEMS + "/" + id + "/retire", null, Map.of("reason", "ETC"))
        .expectError(401, "UNAUTHORIZED");

    Map<String, Object> bad = new HashMap<>();
    bad.put("reason", "LOST");
    bad.put("memo", "가".repeat(201));
    Resp r =
        api()
            .post(ITEMS + "/" + id + "/retire", owner.accessToken(), bad)
            .expectError(400, "VALIDATION_FAILED");
    assertThat(fields(r)).containsExactlyInAnyOrder("reason", "memo");
    assertThat(
            fields(
                api()
                    .post(ITEMS + "/" + id + "/retire", owner.accessToken(), Map.of())
                    .expectError(400, "VALIDATION_FAILED")))
        .containsExactly("reason");
    assertThat(
            fields(
                api()
                    .post(ITEMS + "/" + id + "/retire", owner.accessToken(), null)
                    .expectError(400, "VALIDATION_FAILED")))
        .containsExactly("reason");
    // 200자는 된다
    api()
        .post(
            ITEMS + "/" + id + "/retire",
            owner.accessToken(),
            Map.of("reason", "ETC", "memo", "가".repeat(200)))
        .expect(200);
  }

  @Test
  void 비활성_이유_코드는_400이고_사용_중지한_옷이_쓰는_코드는_지울_수_없다() {
    Session admin = admin();
    String a = admin.accessToken();
    Session s = signup();
    String t = s.accessToken();
    String reason = "ZR_" + CodeIntegrationTest.suffix();
    api()
        .post("/api/v1/admin/codes/RETIRE_REASON", a, Map.of("code", reason, "label", "잃어버렸어요"))
        .expect(201);
    String id = create(t, item("셔츠", "TOP", "SHIRT", "white"));
    retire(t, id, reason);

    // 사용 중지한 옷이 쓰는 이유 코드는 사용 수에 들어가고 삭제가 막힌다
    JsonNode codes = api().get("/api/v1/admin/codes", a).expect(200).body();
    JsonNode group = null;
    for (JsonNode g : codes.get("groups")) {
      if (g.get("group").asString().equals("RETIRE_REASON")) {
        group = g;
      }
    }
    assertThat(group).isNotNull();
    long usage = -1;
    for (JsonNode c : group.get("codes")) {
      if (c.get("code").asString().equals(reason)) {
        usage = c.get("usageCount").asLong();
      }
    }
    assertThat(usage).isEqualTo(1);
    api().delete("/api/v1/admin/codes/RETIRE_REASON/" + reason, a).expectError(409, "CODE_IN_USE");

    // 다른 그룹의 코드(색)도 사용 중지한 옷이 쓰면 사용 수에 들어간다
    String color = "zr_" + CodeIntegrationTest.suffix().toLowerCase();
    api()
        .post(
            "/api/v1/admin/codes/COLOR",
            a,
            Map.of(
                "code",
                color,
                "label",
                "사용 중지 색",
                "attrs",
                Map.of("hex", "#123456", "neutral", false, "light", 0.5)))
        .expect(201);
    Map<String, Object> in = item("색 셔츠", "TOP", "SHIRT", color);
    String colored = create(t, in);
    retire(t, colored, "ETC");
    api().delete("/api/v1/admin/codes/COLOR/" + color, a).expectError(409, "CODE_IN_USE");

    // 비활성 이유는 새로 고를 수 없다
    api()
        .patch("/api/v1/admin/codes/RETIRE_REASON/" + reason, a, Map.of("active", false))
        .expect(200);
    String other = create(t, item("다른 셔츠", "TOP", "SHIRT", "white"));
    Resp r =
        api()
            .post(ITEMS + "/" + other + "/retire", t, Map.of("reason", reason))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(fields(r)).containsExactly("reason");

    // 정리: 아이템을 지우면 코드를 지울 수 있다
    api().delete(ITEMS + "/" + id, t).expect(204);
    api().delete(ITEMS + "/" + colored, t).expect(204);
    api().delete("/api/v1/admin/codes/RETIRE_REASON/" + reason, a).expect(204);
    api().delete("/api/v1/admin/codes/COLOR/" + color, a).expect(204);
  }

  @Test
  void 관리자_회원_목록은_사용_중인_옷과_사용_중지한_옷을_따로_센다() {
    Session s = signup();
    String t = s.accessToken();
    create(t, item("셔츠", "TOP", "SHIRT", "white"));
    create(t, item("니트", "TOP", "KNIT", "navy"));
    retire(t, create(t, item("코트", "OUTER", "COAT", "beige")), "NOT_WORN");

    JsonNode me = api().get("/api/v1/users/me", t).expect(200).body();
    JsonNode users =
        api()
            .get("/api/v1/admin/users?q=" + me.get("email").asString(), admin().accessToken())
            .expect(200)
            .body();
    JsonNode row = users.get("items").get(0);
    assertThat(row.get("itemCount").asLong()).isEqualTo(2);
    assertThat(row.get("retiredItemCount").asLong()).isEqualTo(1);
  }

  @Test
  void DB_제약_사용_중지면_시각과_이유가_함께_있다() {
    Session s = signup();
    String id = create(s.accessToken(), item("셔츠", "TOP", "SHIRT", "white"));
    List<String> errors = new ArrayList<>();
    try {
      jdbc.update(
          "update tb_wardrobe_item set retired_at = now() where item_id = ?", Long.parseLong(id));
    } catch (RuntimeException e) {
      errors.add(e.getClass().getSimpleName());
    }
    assertThat(errors).hasSize(1);
  }

  private String create(String token, Map<String, Object> input) {
    return api().post(ITEMS, token, input).expect(201).body().get("id").asString();
  }

  private void retire(String token, String id, String reason) {
    api().post(ITEMS + "/" + id + "/retire", token, Map.of("reason", reason)).expect(200);
  }

  private static List<String> ids(Resp r) {
    List<String> out = new ArrayList<>();
    r.body().get("items").forEach(i -> out.add(i.get("id").asString()));
    return out;
  }

  private static List<String> fields(Resp r) {
    return r.body().get("fieldErrors").findValuesAsString("field");
  }
}
