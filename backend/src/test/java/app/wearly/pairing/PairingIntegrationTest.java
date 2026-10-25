package app.wearly.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 옷장 도우미 연결 (doc/26 1단계, 실제 HTTP·DB·Valkey): 코드 두 흐름, 1회용, 인원, 권한 변경, 해제 */
class PairingIntegrationTest extends IntegrationTestBase {

  static final String PATH = "/api/v1/pairings";

  String issue(Session s, String role) {
    JsonNode body =
        api().post(PATH + "/codes", s.accessToken(), Map.of("role", role)).expect(201).body();
    return body.get("code").asString();
  }

  JsonNode join(Session s, String code, String permission) {
    Map<String, Object> body =
        permission == null ? Map.of("code", code) : Map.of("code", code, "permission", permission);
    return api().post(PATH + "/join", s.accessToken(), body).expect(201).body();
  }

  @Test
  void 주인_코드로_요청하고_주인이_승인한다() {
    Session owner = signup();
    Session helper = signup();

    JsonNode issued =
        api()
            .post(PATH + "/codes", owner.accessToken(), Map.of("role", "owner"))
            .expect(201)
            .body();
    String code = issued.get("code").asString();
    assertThat(code).matches("[A-HJKMNP-Z2-9]{8}");
    assertThat(issued.get("role").asString()).isEqualTo("OWNER");
    Instant expiresAt = Instant.parse(issued.get("expiresAt").asString());
    assertThat(Duration.between(Instant.now(), expiresAt))
        .isBetween(Duration.ofHours(23), Duration.ofHours(25));

    String typed = (code.substring(0, 4) + "-" + code.substring(4)).toLowerCase();
    JsonNode pending = join(helper, typed, null);
    assertThat(pending.get("status").asString()).isEqualTo("PENDING");
    assertThat(pending.get("role").asString()).isEqualTo("HELPER");
    assertThat(pending.get("partnerId").asString()).isEqualTo(owner.userId());
    assertThat(pending.get("permission").isNull()).isTrue();
    assertThat(pending.get("approvedAt").isNull()).isTrue();
    String id = pending.get("id").asString();

    // 1회용: 같은 코드를 다시 넣으면 없는 코드
    api()
        .post(PATH + "/join", signup().accessToken(), Map.of("code", code))
        .expectError(404, "PAIRING_CODE_INVALID");

    JsonNode ownerList = api().get(PATH, owner.accessToken()).expect(200).body();
    assertThat(ownerList.get("asOwner")).hasSize(1);
    assertThat(ownerList.get("asHelper")).isEmpty();
    assertThat(ownerList.get("asOwner").get(0).get("partnerName").asString()).isNotBlank();

    // 돕는 사람은 승인할 수 없다
    api()
        .post(PATH + "/" + id + "/approve", helper.accessToken(), Map.of("permission", "EDIT"))
        .expectError(404, "NOT_FOUND");
    JsonNode approved =
        api()
            .post(PATH + "/" + id + "/approve", owner.accessToken(), Map.of("permission", "edit"))
            .expect(200)
            .body();
    assertThat(approved.get("status").asString()).isEqualTo("ACTIVE");
    assertThat(approved.get("permission").asString()).isEqualTo("EDIT");
    assertThat(approved.get("role").asString()).isEqualTo("OWNER");
    assertThat(approved.get("approvedAt").asString()).endsWith("Z");

    JsonNode helperList = api().get(PATH, helper.accessToken()).body();
    assertThat(helperList.get("asHelper").get(0).get("status").asString()).isEqualTo("ACTIVE");
  }

  @Test
  void 돕는_사람_코드를_주인이_넣으면_바로_연결되고_권한을_바꾸고_해제한다() {
    Session owner = signup();
    Session helper = signup();

    String code = issue(helper, "HELPER");
    api()
        .post(PATH + "/join", owner.accessToken(), Map.of("code", code))
        .expectError(400, "VALIDATION_FAILED");
    JsonNode active = join(owner, code, "VIEW");
    assertThat(active.get("status").asString()).isEqualTo("ACTIVE");
    assertThat(active.get("issuedBy").asString()).isEqualTo("HELPER");
    String id = active.get("id").asString();

    JsonNode changed =
        api()
            .patch(
                PATH + "/" + id,
                owner.accessToken(),
                Map.of("permission", "EDIT", "notifyOwner", false))
            .expect(200)
            .body();
    assertThat(changed.get("permission").asString()).isEqualTo("EDIT");
    assertThat(changed.get("notifyOwner").asBoolean()).isFalse();
    api()
        .patch(PATH + "/" + id, helper.accessToken(), Map.of("permission", "VIEW"))
        .expectError(404, "NOT_FOUND");

    // 돕는 사람도 끊을 수 있고, 끊은 뒤에는 목록에 없다
    api().delete(PATH + "/" + id, helper.accessToken()).expect(204);
    assertThat(api().get(PATH, owner.accessToken()).body().get("asOwner")).isEmpty();
    api().delete(PATH + "/" + id, owner.accessToken()).expectError(404, "NOT_FOUND");

    // 끊은 뒤 다시 연결할 수 있다 (부분 고유 인덱스)
    join(owner, issue(helper, "HELPER"), "EDIT");
  }

  @Test
  void 거절_자기_코드_중복_인원() {
    Session owner = signup();
    Session helper = signup();

    String own = issue(owner, "OWNER");
    api()
        .post(PATH + "/join", owner.accessToken(), Map.of("code", own))
        .expectError(400, "PAIRING_SELF");

    final JsonNode pending = join(helper, own, null);
    api()
        .post(PATH + "/join", helper.accessToken(), Map.of("code", issue(owner, "OWNER")))
        .expectError(409, "PAIRING_EXISTS");

    // 주인은 돕는 사람 1명까지 (대기 포함)
    Resp limit =
        api().post(PATH + "/join", signup().accessToken(), Map.of("code", issue(owner, "OWNER")));
    limit.expectError(409, "PAIRING_LIMIT");
    assertThat(limit.body().get("params").get("side").asString()).isEqualTo("OWNER");
    assertThat(limit.body().get("params").get("max").asInt()).isEqualTo(1);

    String id = pending.get("id").asString();
    api().post(PATH + "/" + id + "/reject", owner.accessToken(), null).expect(204);
    assertThat(api().get(PATH, helper.accessToken()).body().get("asHelper")).isEmpty();
    api()
        .post(PATH + "/" + id + "/approve", owner.accessToken(), Map.of("permission", "VIEW"))
        .expectError(404, "NOT_FOUND");
  }

  @Test
  void 자리_값_검사와_로그인_필요() {
    Session s = signup();
    api()
        .post(PATH + "/codes", s.accessToken(), Map.of("role", "FRIEND"))
        .expectError(400, "VALIDATION_FAILED");
    api().get(PATH, null).expectError(401, "UNAUTHORIZED");
  }
}
