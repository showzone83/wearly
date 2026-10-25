package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Session;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** 관리자 옷장 도우미 (doc/26 6단계, 실제 HTTP): 연결 목록·검색·상태 필터, 작업 기록, 강제 해제와 변경 이력, 권한 */
class AdminPairingIntegrationTest extends IntegrationTestBase {

  static final String PATH = "/api/v1/admin/pairings";

  @Autowired JdbcTemplate jdbc;

  /** 돕는 사람 코드를 주인이 넣어 바로 연결. 연결 ID */
  String pair(Session owner, Session helper, String permission) {
    String code =
        api()
            .post("/api/v1/pairings/codes", helper.accessToken(), Map.of("role", "HELPER"))
            .expect(201)
            .body()
            .get("code")
            .asString();
    return api()
        .post(
            "/api/v1/pairings/join",
            owner.accessToken(),
            Map.of("code", code, "permission", permission))
        .expect(201)
        .body()
        .get("id")
        .asString();
  }

  static Map<String, Object> item(String name) {
    Map<String, Object> m = new HashMap<>();
    m.put("name", name);
    m.put("category", "TOP");
    m.put("subType", "SHIRT");
    m.put("color", "white");
    m.put("pattern", "SOLID");
    m.put("seasons", List.of("SPRING"));
    m.put("styles", List.of("NEAT"));
    m.put("formality", 2);
    m.put("status", "OWNED");
    return m;
  }

  JsonNode find(String adminToken, String id) {
    JsonNode items = api().get(PATH + "?size=100", adminToken).expect(200).body().get("items");
    for (JsonNode p : items) {
      if (p.get("id").asString().equals(id)) {
        return p;
      }
    }
    throw new AssertionError("연결 목록에 없음: " + id);
  }

  @Test
  void 관리자만_쓸_수_있다() {
    Session user = signup();
    for (String path : List.of(PATH, PATH + "/1/actions")) {
      api().get(path, user.accessToken()).expectError(403, "FORBIDDEN");
      api().get(path, null).expectError(401, "UNAUTHORIZED");
    }
    api().post(PATH + "/1/end", user.accessToken(), null).expectError(403, "FORBIDDEN");
    api().post(PATH + "/1/end", null, null).expectError(401, "UNAUTHORIZED");
  }

  @Test
  void 연결_목록은_양쪽_회원과_작업_수를_보여_주고_검색과_상태로_거른다() {
    Session owner = signup();
    Session helper = signup();
    String id = pair(owner, helper, "EDIT");
    api()
        .post(
            "/api/v1/owners/" + owner.userId() + "/wardrobe/items",
            helper.accessToken(),
            item("셔츠"))
        .expect(201);
    api()
        .post(
            "/api/v1/owners/" + owner.userId() + "/wardrobe/items",
            helper.accessToken(),
            item("니트"))
        .expect(201);
    String t = admin().accessToken();

    JsonNode p = find(t, id);
    assertThat(p.get("status").asString()).isEqualTo("ACTIVE");
    assertThat(p.get("permission").asString()).isEqualTo("EDIT");
    assertThat(p.get("issuedBy").asString()).isEqualTo("HELPER");
    assertThat(p.get("notifyOwner").asBoolean()).isTrue();
    assertThat(p.get("ownerId").asString()).isEqualTo(owner.userId());
    assertThat(p.get("helperId").asString()).isEqualTo(helper.userId());
    assertThat(p.get("ownerEmail").asString()).contains("@");
    assertThat(p.get("actionCount").asLong()).isEqualTo(2);
    assertThat(p.get("lastActionAt").asString()).endsWith("Z");
    assertThat(p.get("endedAt").isNull()).isTrue();
    assertThat(p.get("endedBy").isNull()).isTrue();

    // 이메일로 검색 (주인·돕는 사람 어느 쪽이든)
    String ownerEmail = p.get("ownerEmail").asString();
    JsonNode byOwner =
        api().get(PATH + "?q=" + ownerEmail + "&status=ACTIVE&size=100", t).expect(200).body();
    assertThat(byOwner.get("items").findValuesAsString("id")).containsExactly(id);
    assertThat(byOwner.get("total").asLong()).isEqualTo(1);
    String helperEmail = p.get("helperEmail").asString();
    assertThat(
            api()
                .get(PATH + "?q=" + helperEmail, t)
                .expect(200)
                .body()
                .get("items")
                .findValuesAsString("id"))
        .containsExactly(id);
    assertThat(api().get(PATH + "?q=" + ownerEmail + "&status=ENDED", t).body().get("items"))
        .isEmpty();
    api().get(PATH + "?status=UNKNOWN", t).expectError(400, "VALIDATION_FAILED");
  }

  @Test
  void 작업_기록은_최신순이고_없는_연결은_404() {
    Session owner = signup();
    Session helper = signup();
    String id = pair(owner, helper, "EDIT");
    String base = "/api/v1/owners/" + owner.userId() + "/wardrobe/items";
    String itemId =
        api().post(base, helper.accessToken(), item("셔츠")).expect(201).body().get("id").asString();
    api().patch(base + "/" + itemId, helper.accessToken(), Map.of("name", "흰 셔츠")).expect(200);
    String t = admin().accessToken();

    JsonNode actions = api().get(PATH + "/" + id + "/actions", t).expect(200).body();
    assertThat(actions.findValuesAsString("action")).containsExactly("ITEM_UPDATE", "ITEM_CREATE");
    assertThat(actions.get(0).get("itemId").asString()).isEqualTo(itemId);
    assertThat(actions.get(0).get("itemName").asString()).isEqualTo("흰 셔츠");
    api().get(PATH + "/999999999/actions", t).expectError(404, "NOT_FOUND");
  }

  @Test
  void 강제_해제는_연결을_끝내고_변경_이력을_남기고_옷은_주인에게_남는다() {
    Session owner = signup();
    Session helper = signup();
    String id = pair(owner, helper, "EDIT");
    api()
        .post(
            "/api/v1/owners/" + owner.userId() + "/wardrobe/items",
            helper.accessToken(),
            item("셔츠"))
        .expect(201);
    String t = admin().accessToken();

    api().post(PATH + "/" + id + "/end", t, null).expect(204);

    JsonNode p = find(t, id);
    assertThat(p.get("status").asString()).isEqualTo("ENDED");
    assertThat(p.get("endedBy").asString()).isEqualTo("ADMIN");
    assertThat(p.get("endedAt").asString()).endsWith("Z");
    // 앱 쪽: 목록에서 사라지고 대리 접근은 막히며 옷은 주인에게 남는다
    assertThat(api().get("/api/v1/pairings", owner.accessToken()).body().get("asOwner")).isEmpty();
    api()
        .get("/api/v1/owners/" + owner.userId() + "/wardrobe/items", helper.accessToken())
        .expectError(404, "NOT_FOUND");
    assertThat(api().get("/api/v1/wardrobe/items", owner.accessToken()).body().get("items"))
        .hasSize(1);
    // 작업 기록은 끝난 뒤에도 남는다
    assertThat(api().get(PATH + "/" + id + "/actions", t).body()).hasSize(1);

    // 이미 끝난 연결·없는 연결은 404
    api().post(PATH + "/" + id + "/end", t, null).expectError(404, "NOT_FOUND");
    api().post(PATH + "/999999999/end", t, null).expectError(404, "NOT_FOUND");

    // 변경 이력
    JsonNode audits =
        api().get("/api/v1/admin/audit-logs?targetType=PAIRING&size=100", t).expect(200).body();
    JsonNode row = null;
    for (JsonNode a : audits.get("items")) {
      if (a.get("targetId").asString().equals(id)) {
        row = a;
      }
    }
    assertThat(row).isNotNull();
    assertThat(row.get("action").asString()).isEqualTo("PAIRING_FORCE_END");
    assertThat(row.get("before").get("status").asString()).isEqualTo("ACTIVE");
    assertThat(row.get("before").get("ownerId").asString()).isEqualTo(owner.userId());
    assertThat(row.get("after").get("endedBy").asString()).isEqualTo("ADMIN");

    // 끝난 뒤 같은 두 사람이 다시 연결할 수 있다
    pair(owner, helper, "VIEW");
  }

  @Test
  void 승인_대기_연결도_강제로_끝낼_수_있다() {
    Session owner = signup();
    Session helper = signup();
    String code =
        api()
            .post("/api/v1/pairings/codes", owner.accessToken(), Map.of("role", "OWNER"))
            .expect(201)
            .body()
            .get("code")
            .asString();
    String id =
        api()
            .post("/api/v1/pairings/join", helper.accessToken(), Map.of("code", code))
            .expect(201)
            .body()
            .get("id")
            .asString();
    String t = admin().accessToken();
    assertThat(find(t, id).get("status").asString()).isEqualTo("PENDING");

    api().post(PATH + "/" + id + "/end", t, null).expect(204);
    assertThat(find(t, id).get("endedBy").asString()).isEqualTo("ADMIN");
    assertThat(api().get("/api/v1/pairings", helper.accessToken()).body().get("asHelper"))
        .isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT ended_by FROM tb_closet_pairing WHERE pairing_id = ?",
                String.class,
                Long.parseLong(id)))
        .isEqualTo("ADMIN");
  }
}
