package app.wearly.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Session;
import app.wearly.support.TestImages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 옷장 도우미 대리 작업 (doc/26 2단계, 실제 HTTP·DB·Valkey·MinIO): 주인 옷장에 등록·수정·사용 중지·다시 사용, 작업 기록, 사진 올리기와 AI
 * 분석(돕는 사람 한도), 권한별 허용·거부, 해제 뒤 접근 거부와 옷 유지
 */
class OwnerClosetIntegrationTest extends IntegrationTestBase {

  static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Autowired JdbcTemplate jdbc;

  /** 주인 코드 → 돕는 사람 입력 → 주인 승인 (#18부터 코드는 주인만 만든다). 연결 ID */
  String pair(Session owner, Session helper, String permission) {
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
    api()
        .post(
            "/api/v1/pairings/" + id + "/approve",
            owner.accessToken(),
            Map.of("permission", permission))
        .expect(200);
    return id;
  }

  static String items(Session owner) {
    return "/api/v1/owners/" + owner.userId() + "/wardrobe/items";
  }

  static Map<String, Object> item(String name, String imageId) {
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
    if (imageId != null) {
      m.put("imageId", imageId);
    }
    return m;
  }

  JsonNode actions(Session s, String pairingId) {
    return api()
        .get("/api/v1/pairings/" + pairingId + "/actions", s.accessToken())
        .expect(200)
        .body();
  }

  @Test
  void 돕는_사람이_주인_옷장에_등록_수정_사용_중지_다시_사용하고_기록이_남는다() {
    Session owner = signup();
    Session helper = signup();
    String pairingId = pair(owner, helper, "EDIT");
    String t = helper.accessToken();

    String itemId =
        api().post(items(owner), t, item("엄마 셔츠", null)).expect(201).body().get("id").asString();
    // 주인 옷장에 있고 돕는 사람 옷장에는 없다
    JsonNode ownerItems =
        api().get("/api/v1/wardrobe/items", owner.accessToken()).expect(200).body().get("items");
    assertThat(ownerItems.findValuesAsString("id")).containsExactly(itemId);
    assertThat(api().get("/api/v1/wardrobe/items", t).body().get("items")).isEmpty();
    assertThat(api().get(items(owner), t).expect(200).body().get("items")).hasSize(1);
    assertThat(api().get(items(owner) + "/" + itemId, t).expect(200).body().get("name").asString())
        .isEqualTo("엄마 셔츠");

    api().patch(items(owner) + "/" + itemId, t, Map.of("name", "엄마 흰 셔츠")).expect(200);
    api().post(items(owner) + "/" + itemId + "/retire", t, Map.of("reason", "DONATED")).expect(200);
    JsonNode restored =
        api().post(items(owner) + "/" + itemId + "/restore", t, null).expect(200).body();
    assertThat(restored.get("retiredAt").isNull()).isTrue();
    // 이미 사용 중이면 다시 사용하기는 기록하지 않는다
    api().post(items(owner) + "/" + itemId + "/restore", t, null).expect(200);

    JsonNode log = actions(owner, pairingId);
    assertThat(log.findValuesAsString("action"))
        .containsExactly("ITEM_RESTORE", "ITEM_RETIRE", "ITEM_UPDATE", "ITEM_CREATE");
    assertThat(log.get(0).get("itemId").asString()).isEqualTo(itemId);
    assertThat(log.get(0).get("itemName").asString()).isEqualTo("엄마 흰 셔츠");
    assertThat(log.get(3).get("itemName").asString()).isEqualTo("엄마 셔츠");
    assertThat(actions(helper, pairingId)).hasSize(4);
    api()
        .get("/api/v1/pairings/" + pairingId + "/actions", signup().accessToken())
        .expectError(404, "NOT_FOUND");

    // 완전 삭제 경로는 없다 (주인만 /wardrobe에서)
    api().delete(items(owner) + "/" + itemId, t).expectError(405, "METHOD_NOT_ALLOWED");
    // 주인이 지운 옷도 기록은 이름으로 남는다
    api().delete("/api/v1/wardrobe/items/" + itemId, owner.accessToken()).expect(204);
    assertThat(actions(owner, pairingId)).hasSize(4);
  }

  @Test
  void 보기_권한은_조회만_연결이_없으면_404() {
    Session owner = signup();
    Session helper = signup();
    String pairingId = pair(owner, helper, "VIEW");
    String t = helper.accessToken();
    String itemId =
        api()
            .post("/api/v1/wardrobe/items", owner.accessToken(), item("주인 니트", null))
            .expect(201)
            .body()
            .get("id")
            .asString();

    assertThat(api().get(items(owner), t).expect(200).body().get("items")).hasSize(1);
    api().post(items(owner), t, item("몰래", null)).expectError(403, "FORBIDDEN");
    api().patch(items(owner) + "/" + itemId, t, Map.of("name", "x")).expectError(403, "FORBIDDEN");
    api()
        .post(items(owner) + "/" + itemId + "/retire", t, Map.of("reason", "DONATED"))
        .expectError(403, "FORBIDDEN");
    api()
        .post(
            "/api/v1/owners/" + owner.userId() + "/images/upload-url",
            t,
            Map.of("purpose", "ITEM", "contentType", "image/jpeg", "size", 10))
        .expectError(403, "FORBIDDEN");

    // 연결이 없는 회원·자기 자신·대기 중인 연결은 404
    Session stranger = signup();
    api().get(items(owner), stranger.accessToken()).expectError(404, "NOT_FOUND");
    api().get(items(owner), owner.accessToken()).expectError(404, "NOT_FOUND");

    // 권한을 올리면 바로 쓸 수 있다 (매 요청 DB 확인)
    api()
        .patch("/api/v1/pairings/" + pairingId, owner.accessToken(), Map.of("permission", "EDIT"))
        .expect(200);
    api().post(items(owner), t, item("이제 등록", null)).expect(201);

    // 해제하면 막히고, 등록한 옷은 주인 옷장에 남는다
    api().delete("/api/v1/pairings/" + pairingId, owner.accessToken()).expect(204);
    api().get(items(owner), t).expectError(404, "NOT_FOUND");
    assertThat(
            api()
                .get("/api/v1/wardrobe/items", owner.accessToken())
                .body()
                .get("items")
                .findValuesAsString("name"))
        .containsExactlyInAnyOrder("주인 니트", "이제 등록");
  }

  @Test
  void 주인_경로로_사진을_올리고_AI_분석은_돕는_사람_한도를_쓴다() throws Exception {
    Session owner = signup();
    Session helper = signup();
    pair(owner, helper, "EDIT");
    String t = helper.accessToken();
    String images = "/api/v1/owners/" + owner.userId() + "/images";

    byte[] photo = TestImages.jpeg(200, 300);
    JsonNode url =
        api()
            .post(
                images + "/upload-url",
                t,
                Map.of("purpose", "ITEM", "contentType", "image/jpeg", "size", photo.length))
            .expect(200)
            .body();
    assertThat(put(url, photo)).isEqualTo(200);
    String imageId = url.get("imageId").asString();
    api().post(images + "/" + imageId + "/complete", t, null).expect(200);

    // 사진은 주인 것: 주인 경로·주인 회원으로 저장된다
    Long imageOwner =
        jdbc.queryForObject(
            "SELECT user_id FROM tb_item_image WHERE image_id = ?::uuid", Long.class, imageId);
    assertThat(imageOwner).isEqualTo(Long.parseLong(owner.userId()));
    // 돕는 사람 자기 경로로는 이 사진을 쓸 수 없다
    api()
        .post("/api/v1/wardrobe/items/analyze", t, Map.of("imageId", imageId))
        .expectError(404, "NOT_FOUND");

    JsonNode analyzed =
        api()
            .post(
                "/api/v1/owners/" + owner.userId() + "/wardrobe/items/analyze",
                t,
                Map.of("imageId", imageId))
            .expect(200)
            .body();
    assertThat(analyzed.get("provider").asString()).isEqualTo("mock");
    // 한도 3 (테스트 설정): 돕는 사람이 한 번 썼다
    assertThat(analyzed.get("remainingToday").asInt()).isEqualTo(2);
    Long analyzedBy =
        jdbc.queryForObject(
            "SELECT user_id FROM tb_ai_analysis_log ORDER BY created_at DESC, 1 DESC LIMIT 1",
            Long.class);
    assertThat(analyzedBy).isEqualTo(Long.parseLong(helper.userId()));

    // 주인 사진을 붙여 주인 옷장에 등록
    JsonNode created = api().post(items(owner), t, item("사진 셔츠", imageId)).expect(201).body();
    assertThat(created.get("image").get("imageId").asString()).isEqualTo(imageId);
    JsonNode ownerItem =
        api()
            .get("/api/v1/wardrobe/items/" + created.get("id").asString(), owner.accessToken())
            .expect(200)
            .body();
    assertThat(ownerItem.get("image").get("imageId").asString()).isEqualTo(imageId);
  }

  /** 서명 URL로 실제 PUT (받은 headers를 그대로 붙인다) */
  private static int put(JsonNode uploadUrl, byte[] bytes) throws Exception {
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create(uploadUrl.get("uploadUrl").asString()))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
    uploadUrl
        .get("headers")
        .properties()
        .forEach(e -> b.header(e.getKey(), e.getValue().asString()));
    return HTTP.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
  }
}
