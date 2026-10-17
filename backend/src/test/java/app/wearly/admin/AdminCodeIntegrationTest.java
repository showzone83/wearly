package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.code.CodeIntegrationTest;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** 07 문서 4장 "코드": 관리자 코드 CRUD·순서·삭제·pairs·캐시 갱신·변경 이력 */
class AdminCodeIntegrationTest extends IntegrationTestBase {

  @Autowired JdbcTemplate jdbc;

  @Test
  void 관리자가_아니면_403_토큰이_없으면_401() {
    Session user = signup();
    for (String path :
        List.of(
            "/api/v1/admin/codes",
            "/api/v1/admin/dashboard",
            "/api/v1/admin/ai/providers",
            "/api/v1/admin/users",
            "/api/v1/admin/audit-logs",
            "/api/v1/admin/ai/logs")) {
      api().get(path, user.accessToken()).expectError(403, "FORBIDDEN");
      api().get(path, null).expectError(401, "UNAUTHORIZED");
    }
    api()
        .post("/api/v1/admin/codes/PATTERN", user.accessToken(), Map.of("code", "X", "label", "x"))
        .expectError(403, "FORBIDDEN");
    api().get("/api/v1/admin/codes", admin().accessToken()).expect(200);
  }

  @Test
  void 코드_목록은_설명과_사용_수를_준다() {
    Session admin = admin();
    Session s = signup();
    String pattern = "ZU_" + CodeIntegrationTest.suffix();
    api()
        .post(
            "/api/v1/admin/codes/PATTERN",
            admin.accessToken(),
            Map.of("code", pattern, "label", "사용 수"))
        .expect(201);
    try {
      Map<String, Object> in = CodeIntegrationTest.item("a", "TOP", "TEE", "navy");
      in.put("pattern", pattern);
      api().post("/api/v1/wardrobe/items", s.accessToken(), in).expect(201);
      api().post("/api/v1/wardrobe/items", s.accessToken(), in).expect(201);

      JsonNode body = api().get("/api/v1/admin/codes", admin.accessToken()).expect(200).body();
      assertThat(body.get("version").asString())
          .isEqualTo(api().get("/api/v1/codes", null).body().get("version").asString());
      JsonNode group = body.get("groups").get(0);
      assertThat(group.get("description").asString()).contains("slot");
      JsonNode created = CodeIntegrationTest.find(body, "PATTERN", pattern);
      assertThat(created.get("usageCount").asLong()).isEqualTo(2);
      assertThat(created.get("sortOrder").asInt()).isGreaterThan(70); // 맨 뒤 + 10
      // 사용 중이면 삭제할 수 없다
      Resp del =
          api()
              .delete("/api/v1/admin/codes/PATTERN/" + pattern, admin.accessToken())
              .expectError(409, "CODE_IN_USE");
      assertThat(del.body().get("message").asString()).contains("아이템 2개");
      api()
          .delete("/api/v1/admin/codes/COLOR/navy", admin.accessToken())
          .expectError(409, "CODE_IN_USE");
      api()
          .delete("/api/v1/admin/codes/CATEGORY/BAG", admin.accessToken())
          .expectError(409, "CODE_IN_USE"); // 하위 세부 종류
    } finally {
      jdbc.update("delete from tb_wardrobe_item where user_id = ?", Long.parseLong(s.userId()));
      api().delete("/api/v1/admin/codes/PATTERN/" + pattern, admin.accessToken()).expect(204);
    }
  }

  @Test
  void 추가_수정_삭제와_캐시_갱신_변경_이력() {
    Session admin = admin();
    String t = admin.accessToken();
    String v0 = api().get("/api/v1/codes", null).body().get("version").asString();
    String code = "ZK_" + CodeIntegrationTest.suffix();

    Resp created =
        api()
            .post(
                "/api/v1/admin/codes/SUB_TYPE",
                t,
                Map.of(
                    "code",
                    code,
                    "label",
                    "시험 아이템",
                    "parent",
                    "TOP",
                    "sortOrder",
                    999,
                    "attrs",
                    Map.of("volume", -1, "warmth", 3, "shape", "NEW_SHAPE")))
            .expect(201);
    assertThat(created.body().get("active").asBoolean()).isTrue();
    assertThat(created.body().get("usageCount").asLong()).isZero();
    api()
        .post(
            "/api/v1/admin/codes/SUB_TYPE",
            t,
            Map.of(
                "code",
                code,
                "label",
                "중복",
                "parent",
                "TOP",
                "attrs",
                Map.of("volume", 0, "warmth", 0, "shape", "X")))
        .expectError(409, "CODE_EXISTS");

    // 캐시가 바로 갱신된다: /codes version이 바뀌고 새 코드로 아이템을 등록할 수 있다
    Resp codes = api().get("/api/v1/codes", null).expect(200);
    String v1 = codes.body().get("version").asString();
    assertThat(v1).isNotEqualTo(v0);
    assertThat(
            CodeIntegrationTest.find(codes.body(), "SUB_TYPE", code)
                .get("attrs")
                .get("shape")
                .asString())
        .isEqualTo("NEW_SHAPE");
    Session user = signup();
    String itemId =
        api()
            .post(
                "/api/v1/wardrobe/items",
                user.accessToken(),
                CodeIntegrationTest.item("새 옷", "TOP", code, "white"))
            .expect(201)
            .body()
            .get("id")
            .asString();

    // PATCH: 보낸 필드만, attrs는 통째로. 코드 문자열은 못 바꾼다
    Resp patched =
        api()
            .patch(
                "/api/v1/admin/codes/SUB_TYPE/" + code,
                t,
                Map.of("label", "바뀐 이름", "attrs", Map.of("volume", 1, "warmth", 0, "shape", "S2")))
            .expect(200);
    assertThat(patched.body().get("label").asString()).isEqualTo("바뀐 이름");
    assertThat(patched.body().get("parent").asString()).isEqualTo("TOP");
    assertThat(patched.body().get("usageCount").asLong()).isEqualTo(1);
    api()
        .patch("/api/v1/admin/codes/SUB_TYPE/" + code, t, Map.of("code", "OTHER"))
        .expectError(400, "VALIDATION_FAILED");
    api()
        .patch("/api/v1/admin/codes/SUB_TYPE/" + code, t, Map.of("parent", "NOPE"))
        .expectError(400, "VALIDATION_FAILED");
    api()
        .patch("/api/v1/admin/codes/SUB_TYPE/NOPE_" + code, t, Map.of("label", "x"))
        .expectError(404, "NOT_FOUND");
    api()
        .patch("/api/v1/admin/codes/NOPE/" + code, t, Map.of("label", "x"))
        .expectError(404, "NOT_FOUND");
    String v2 = api().get("/api/v1/codes", null).body().get("version").asString();
    assertThat(v2).isNotEqualTo(v1);

    // 사용 중 → 409, 아이템을 지우면 삭제
    api().delete("/api/v1/admin/codes/SUB_TYPE/" + code, t).expectError(409, "CODE_IN_USE");
    api().delete("/api/v1/wardrobe/items/" + itemId, user.accessToken()).expect(204);
    api().delete("/api/v1/admin/codes/SUB_TYPE/" + code, t).expect(204);
    api().delete("/api/v1/admin/codes/SUB_TYPE/" + code, t).expectError(404, "NOT_FOUND");
    assertThat(CodeIntegrationTest.find(api().get("/api/v1/codes", null).body(), "SUB_TYPE", code))
        .isNull();

    // 변경 이력: 만들기·수정·삭제, before/after
    JsonNode audits =
        api().get("/api/v1/admin/audit-logs?targetType=CODE&size=100", t).expect(200).body();
    assertThat(audits.get("page").asInt()).isZero();
    assertThat(audits.get("size").asInt()).isEqualTo(100);
    assertThat(audits.get("total").asLong()).isPositive();
    List<String> actions = new ArrayList<>();
    JsonNode update = null;
    for (JsonNode a : audits.get("items")) {
      if (a.get("targetId").asString().equals("SUB_TYPE:" + code)) {
        actions.add(a.get("action").asString());
        assertThat(a.get("adminEmail").asString()).isEqualTo(ADMIN_EMAIL);
        if (a.get("action").asString().equals("CODE_UPDATE")) {
          update = a;
        }
      }
    }
    assertThat(actions).containsExactly("CODE_DELETE", "CODE_UPDATE", "CODE_CREATE"); // 최신순
    assertThat(update.get("before").get("label").asString()).isEqualTo("시험 아이템");
    assertThat(update.get("after").get("label").asString()).isEqualTo("바뀐 이름");
  }

  @Test
  void 입력_검증() {
    String t = admin().accessToken();
    Resp r =
        api()
            .post(
                "/api/v1/admin/codes/COLOR",
                t,
                Map.of(
                    "code",
                    "Bad-Color",
                    "label",
                    "",
                    "attrs",
                    Map.of(
                        "hex",
                        "red",
                        "neutral",
                        "yes",
                        "light",
                        2,
                        "pairs",
                        List.of("nope"),
                        "glow",
                        true)))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(r.body().get("fieldErrors").findValuesAsString("field"))
        .contains(
            "code",
            "label",
            "attrs.hex",
            "attrs.neutral",
            "attrs.light",
            "attrs.pairs",
            "attrs.glow");

    Resp sub =
        api()
            .post(
                "/api/v1/admin/codes/SUB_TYPE",
                t,
                Map.of("code", "ZZ", "label", "x", "attrs", Map.of("volume", 5, "warmth", 1.5)))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(sub.body().get("fieldErrors").findValuesAsString("field"))
        .contains("parent", "attrs.volume", "attrs.warmth", "attrs.shape");

    Resp pattern =
        api()
            .post(
                "/api/v1/admin/codes/PATTERN",
                t,
                Map.of("code", "lower", "label", "x", "parent", "TOP", "attrs", Map.of("x", 1)))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(pattern.body().get("fieldErrors").findValuesAsString("field"))
        .contains("code", "parent", "attrs.x");

    api()
        .post(
            "/api/v1/admin/codes/CATEGORY",
            t,
            Map.of("code", "ZZ", "label", "x", "attrs", Map.of("slot", "HAT")))
        .expectError(400, "VALIDATION_FAILED");
    api()
        .post("/api/v1/admin/codes/NOPE", t, Map.of("code", "ZZ", "label", "x"))
        .expectError(404, "NOT_FOUND");
  }

  @Test
  void 순서_바꾸기() {
    String t = admin().accessToken();
    List<String> original = codes(api().get("/api/v1/admin/codes", t).body(), "SEASON");
    List<String> reversed = new ArrayList<>(original);
    Collections.reverse(reversed);
    try {
      Resp r =
          api().put("/api/v1/admin/codes/SEASON/order", t, Map.of("codes", reversed)).expect(200);
      assertThat(codes(r.body(), "SEASON")).containsExactlyElementsOf(reversed);
      JsonNode season = r.body().get("groups").get(4);
      assertThat(season.get("codes").get(0).get("sortOrder").asInt()).isEqualTo(10);
      assertThat(season.get("codes").get(1).get("sortOrder").asInt()).isEqualTo(20);
      assertThat(codes(api().get("/api/v1/codes", null).body(), "SEASON"))
          .containsExactlyElementsOf(reversed);

      api()
          .put("/api/v1/admin/codes/SEASON/order", t, Map.of("codes", reversed.subList(0, 2)))
          .expectError(400, "VALIDATION_FAILED");
      api()
          .put(
              "/api/v1/admin/codes/SEASON/order",
              t,
              Map.of("codes", List.of("SPRING", "SPRING", "FALL", "WINTER")))
          .expectError(400, "VALIDATION_FAILED");
    } finally {
      api().put("/api/v1/admin/codes/SEASON/order", t, Map.of("codes", original)).expect(200);
    }
  }

  @Test
  void 색_pairs는_양방향으로_반영된다() {
    String t = admin().accessToken();
    String color = "zp_" + CodeIntegrationTest.suffix().toLowerCase();
    api()
        .post(
            "/api/v1/admin/codes/COLOR",
            t,
            Map.of(
                "code",
                color,
                "label",
                "시험 색",
                "attrs",
                Map.of(
                    "hex",
                    "#abcdef",
                    "neutral",
                    false,
                    "light",
                    0.7,
                    "pairs",
                    List.of("navy", "navy"))))
        .expect(201);
    try {
      JsonNode codes = api().get("/api/v1/codes", null).body();
      assertThat(CodeIntegrationTest.find(codes, "COLOR", color).get("attrs").get("hex").asString())
          .isEqualTo("#ABCDEF");
      assertThat(pairs(codes, color)).containsExactly("navy");
      assertThat(pairs(codes, "navy")).contains(color);

      api()
          .patch(
              "/api/v1/admin/codes/COLOR/" + color,
              t,
              Map.of(
                  "attrs",
                  Map.of(
                      "hex",
                      "#ABCDEF",
                      "neutral",
                      false,
                      "light",
                      0.7,
                      "pairs",
                      List.of("white", "black"))))
          .expect(200);
      codes = api().get("/api/v1/codes", null).body();
      assertThat(pairs(codes, color)).containsExactly("black", "white");
      assertThat(pairs(codes, "navy")).doesNotContain(color);
      assertThat(pairs(codes, "white")).contains(color);
      assertThat(pairs(codes, "black")).contains(color);

      api()
          .patch(
              "/api/v1/admin/codes/COLOR/" + color,
              t,
              Map.of(
                  "attrs",
                  Map.of(
                      "hex", "#ABCDEF", "neutral", false, "light", 0.7, "pairs", List.of(color))))
          .expectError(400, "VALIDATION_FAILED");
    } finally {
      api().delete("/api/v1/admin/codes/COLOR/" + color, t).expect(204);
    }
    JsonNode codes = api().get("/api/v1/codes", null).body();
    assertThat(pairs(codes, "white")).doesNotContain(color);
    assertThat(pairs(codes, "black")).doesNotContain(color);
  }

  static List<String> codes(JsonNode body, String group) {
    List<String> out = new ArrayList<>();
    for (JsonNode g : body.get("groups")) {
      if (g.get("group").asString().equals(group)) {
        g.get("codes").forEach(c -> out.add(c.get("code").asString()));
      }
    }
    return out;
  }

  static List<String> pairs(JsonNode body, String color) {
    List<String> out = new ArrayList<>();
    CodeIntegrationTest.find(body, "COLOR", color)
        .get("attrs")
        .get("pairs")
        .forEach(p -> out.add(p.asString()));
    return out;
  }
}
