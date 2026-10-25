package app.wearly.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.pairing.application.port.in.ProxyDigestUseCase;
import app.wearly.pairing.application.port.in.dto.ProxyDigestRunDto;
import app.wearly.support.FakeHttpServer;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Session;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * 옷장 도우미 알림 (doc/26 3단계, 실제 DB·HTTP, Expo는 가짜 서버): 연결 요청·승인·해제는 바로, 대리 작업은 묶어서 한 번. 서버 푸시를 켜고 예약
 * 작업은 끈 채 유스케이스를 직접 부른다
 */
class PairingNotifyIntegrationTest extends IntegrationTestBase {

  static final FakeHttpServer EXPO = new FakeHttpServer();
  static final String OK_TICKET = "{\"data\":[{\"status\":\"ok\",\"id\":\"receipt-1\"}]}";

  @DynamicPropertySource
  static void push(DynamicPropertyRegistry registry) {
    registry.add("wearly.notify.push.enabled", () -> "true");
    registry.add("wearly.notify.push.provider", () -> "expo");
    registry.add("wearly.notify.push.expo.base-url", EXPO::url);
    registry.add("wearly.notify.day-before.scheduler-enabled", () -> "false");
    registry.add("wearly.pairing.notify.scheduler-enabled", () -> "false");
  }

  @AfterAll
  static void stopExpo() {
    EXPO.close();
  }

  @Autowired ProxyDigestUseCase digest;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void reset() {
    EXPO.clear();
    EXPO.respond(200, OK_TICKET);
    // DB는 다른 테스트와 함께 쓴다: 앞선 테스트가 남긴 알리지 않은 작업은 이 테스트의 대상이 아니다
    jdbc.update("UPDATE tb_closet_proxy_action SET notified_at = now() WHERE notified_at IS NULL");
  }

  /** 회원 + 기기 토큰 하나. 토큰 */
  String withToken(Session s) {
    String token = "ExponentPushToken[" + UUID.randomUUID().toString().replace("-", "") + "]";
    api()
        .put(
            "/api/v1/me/push-token",
            s.accessToken(),
            Map.of("token", token, "platform", "ios", "deviceId", "dev-" + s.userId()))
        .expect(200);
    return token;
  }

  /** 마지막으로 Expo가 받은 푸시 한 건 */
  JsonNode lastPush() throws Exception {
    JsonNode messages = TestApi.JSON.readTree(EXPO.last().body());
    assertThat(messages).hasSize(1);
    return messages.get(0);
  }

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

  @Test
  void 연결_요청_승인_해제는_바로_알린다() throws Exception {
    Session owner = signup();
    Session helper = signup();
    String ownerToken = withToken(owner);
    String helperToken = withToken(helper);

    // 돕는 사람이 주인 코드를 넣으면 주인에게 요청 알림
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
    assertThat(EXPO.requests()).hasSize(1);
    JsonNode request = lastPush();
    assertThat(request.get("to").asString()).isEqualTo(ownerToken);
    assertThat(request.get("channelId").asString()).isEqualTo("closet-helper");
    assertThat(request.get("ttl").asInt()).isEqualTo(24 * 60 * 60);
    assertThat(request.get("title").asString()).isEqualTo("옷장 도우미");
    assertThat(request.get("body").asString()).endsWith("님이 옷장 도우미가 되고 싶어 해요");
    assertThat(request.get("data").get("type").asString()).isEqualTo("closet-pairing-request");
    assertThat(request.get("data").get("pairingId").asString()).isEqualTo(id);

    // 승인하면 돕는 사람에게
    api()
        .post(
            "/api/v1/pairings/" + id + "/approve",
            owner.accessToken(),
            Map.of("permission", "EDIT"))
        .expect(200);
    assertThat(EXPO.requests()).hasSize(2);
    assertThat(lastPush().get("to").asString()).isEqualTo(helperToken);
    assertThat(lastPush().get("data").get("type").asString()).isEqualTo("closet-pairing-accepted");

    // 해제하면 상대(주인)에게
    api().delete("/api/v1/pairings/" + id, helper.accessToken()).expect(204);
    assertThat(EXPO.requests()).hasSize(3);
    assertThat(lastPush().get("to").asString()).isEqualTo(ownerToken);
    assertThat(lastPush().get("data").get("type").asString()).isEqualTo("closet-pairing-ended");
  }

  @Test
  void 오류로_끝난_요청은_알리지_않고_토큰이_없는_회원은_조용히_넘어간다() {
    Session owner = signup();
    Session helper = signup(); // 둘 다 토큰 없음
    String code =
        api()
            .post("/api/v1/pairings/codes", owner.accessToken(), Map.of("role", "OWNER"))
            .expect(201)
            .body()
            .get("code")
            .asString();
    api().post("/api/v1/pairings/join", helper.accessToken(), Map.of("code", code)).expect(201);
    assertThat(EXPO.requests()).isEmpty();

    // 자기 코드 입력은 실패하므로 알림이 없다
    Session lone = signup();
    withToken(lone);
    String own =
        api()
            .post("/api/v1/pairings/codes", lone.accessToken(), Map.of("role", "OWNER"))
            .expect(201)
            .body()
            .get("code")
            .asString();
    api()
        .post("/api/v1/pairings/join", lone.accessToken(), Map.of("code", own))
        .expectError(400, "PAIRING_SELF");
    assertThat(EXPO.requests()).isEmpty();
  }

  @Test
  void 대리_작업은_조용해지면_묶어서_한_번_알린다() throws Exception {
    Session owner = signup();
    Session helper = signup();
    final String ownerToken = withToken(owner);
    final String pairingId = pair(owner, helper, "EDIT");
    EXPO.clear();
    String base = "/api/v1/owners/" + owner.userId() + "/wardrobe/items";
    String t = helper.accessToken();

    api().post(base, t, item("셔츠 1")).expect(201);
    api().post(base, t, item("셔츠 2")).expect(201);
    String third = api().post(base, t, item("셔츠 3")).expect(201).body().get("id").asString();
    api().patch(base + "/" + third, t, Map.of("name", "셔츠 셋")).expect(200);

    // 방금 한 작업은 아직 보내지 않는다 (15분 조용해야 한다)
    ProxyDigestRunDto early = digest.sendDue(Instant.now());
    assertThat(early.due()).isZero();
    assertThat(EXPO.requests()).isEmpty();

    // 20분 뒤: 한 번 보낸다. 셔츠 셋은 등록하고 수정했으므로 두 줄에 모두 센다
    ProxyDigestRunDto run = digest.sendDue(Instant.now().plusSeconds(20 * 60));
    assertThat(run.due()).isEqualTo(1);
    assertThat(run.notified()).isEqualTo(1);
    assertThat(EXPO.requests()).hasSize(1);
    JsonNode push = lastPush();
    assertThat(push.get("to").asString()).isEqualTo(ownerToken);
    assertThat(push.get("body").asString()).endsWith("님이 옷 3벌을 등록하고 1벌을 수정했어요");
    assertThat(push.get("data").get("type").asString()).isEqualTo("closet-helper-digest");
    assertThat(push.get("data").get("pairingId").asString()).isEqualTo(pairingId);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tb_closet_proxy_action WHERE notified_at IS NULL",
                Long.class))
        .isZero();

    // 다시 불러도 보내지 않는다
    assertThat(digest.sendDue(Instant.now().plusSeconds(40 * 60)).due()).isZero();
    assertThat(EXPO.requests()).hasSize(1);
  }

  @Test
  void 알림을_끄면_보내지_않고_표시만_하고_계속_작업하면_최대_대기_뒤_보낸다() throws Exception {
    Session owner = signup();
    Session helper = signup();
    withToken(owner);
    String pairingId = pair(owner, helper, "EDIT");
    EXPO.clear();
    String base = "/api/v1/owners/" + owner.userId() + "/wardrobe/items";
    String t = helper.accessToken();

    api()
        .patch("/api/v1/pairings/" + pairingId, owner.accessToken(), Map.of("notifyOwner", false))
        .expect(200);
    api().post(base, t, item("끈 뒤")).expect(201);
    ProxyDigestRunDto off = digest.sendDue(Instant.now().plusSeconds(20 * 60));
    assertThat(off.due()).isEqualTo(1);
    assertThat(off.notified()).isZero();
    assertThat(off.skipped()).isEqualTo(1);
    assertThat(EXPO.requests()).isEmpty();

    // 다시 켜면 그다음 작업부터 알린다
    api()
        .patch("/api/v1/pairings/" + pairingId, owner.accessToken(), Map.of("notifyOwner", true))
        .expect(200);
    api().post(base, t, item("켠 뒤")).expect(201);
    // 방금 한 작업은 조용해지기 전(15분)이라 아직이지만, 첫 작업 뒤 1시간이 지나면 보낸다
    assertThat(digest.sendDue(Instant.now().plusSeconds(10 * 60)).due()).isZero();
    ProxyDigestRunDto waited = digest.sendDue(Instant.now().plusSeconds(61 * 60));
    assertThat(waited.notified()).isEqualTo(1);
    assertThat(lastPush().get("body").asString()).endsWith("님이 옷 1벌을 등록했어요");
  }
}
