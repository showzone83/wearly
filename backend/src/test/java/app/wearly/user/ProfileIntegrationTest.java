package app.wearly.user;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import app.wearly.user.application.port.in.UserUseCase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

class ProfileIntegrationTest extends IntegrationTestBase {

  static final String ME = "/api/v1/users/me/profile";

  @Autowired JdbcTemplate jdbc;

  @Autowired UserUseCase users;

  @Test
  void 저장_전에는_앱_기본값과_onboarded_false() {
    Session s = signup();
    Resp r = api().get("/api/v1/users/me/profile", s.accessToken()).expect(200);
    assertThat(r.body().get("name").asString()).isEqualTo("테스터");
    assertThat(strings(r, "dressCodes")).containsExactly("BIZ_CASUAL");
    assertThat(strings(r, "moods")).containsExactly("NEAT", "MINIMAL");
    assertThat(strings(r, "colors")).containsExactly("navy", "beige", "white");
    assertThat(r.body().get("workType").asString()).isEqualTo("WEEKDAY");
    assertThat(r.body().get("workDays").toString()).isEqualTo("[1,2,3,4,5]");
    assertThat(r.body().get("workRule").asBoolean()).isTrue();
    assertThat(r.body().get("offRule").asBoolean()).isTrue();
    assertThat(r.body().get("notify").toString())
        .isEqualTo("{\"morning\":true,\"evening\":true,\"dMinus1\":true}");
    assertThat(r.body().get("onboarded").asBoolean()).isFalse();
    // 나의 정보는 선택이라 처음에는 모두 null
    for (String f : List.of("ageRange", "bodyType", "heightCm", "fitPreference")) {
      assertThat(r.body().get(f).isNull()).as(f).isTrue();
    }
  }

  @Test
  void 나의_정보를_저장하고_빼고_보내면_비운다() {
    Session s = signup();
    Map<String, Object> body = withInfo(profile());
    Resp put = api().put(ME, s.accessToken(), body).expect(200);
    assertThat(put.body().get("ageRange").asString()).isEqualTo("AGE_30_EARLY");
    assertThat(put.body().get("bodyType").asString()).isEqualTo("WAVE");
    assertThat(put.body().get("heightCm").asInt()).isEqualTo(162);
    assertThat(put.body().get("fitPreference").asString()).isEqualTo("SLIM");
    assertThat(api().get(ME, s.accessToken()).expect(200).body()).isEqualTo(put.body());

    // 하나만 비우기 (null)
    body.put("bodyType", null);
    Resp one = api().put(ME, s.accessToken(), body).expect(200);
    assertThat(one.body().get("bodyType").isNull()).isTrue();
    assertThat(one.body().get("heightCm").asInt()).isEqualTo(162);

    // 전체 교체: 빠진 필드는 비운다
    Resp cleared = api().put(ME, s.accessToken(), profile()).expect(200);
    for (String f : List.of("ageRange", "bodyType", "heightCm", "fitPreference")) {
      assertThat(cleared.body().get(f).isNull()).as(f).isTrue();
    }
    Map<String, Object> row =
        jdbc.queryForMap(
            "select age_range, body_type, height_cm, fit_preference from tb_user_profile"
                + " where user_id = ?",
            Long.parseLong(s.userId()));
    assertThat(row.values()).containsOnlyNulls();
  }

  @Test
  void 나의_정보_검증_모르는_코드와_키_범위() {
    Session s = signup();
    Map<String, Object> bad = withInfo(profile());
    bad.put("ageRange", "AGE_99");
    bad.put("bodyType", "");
    bad.put("fitPreference", "slim");
    bad.put("heightCm", 129);
    Resp r = api().put(ME, s.accessToken(), bad).expectError(400, "VALIDATION_FAILED");
    // 키는 Bean Validation이 먼저 본다
    JsonNode height = r.body().get("fieldErrors").get(0);
    assertThat(height.get("field").asString()).isEqualTo("heightCm");
    assertThat(height.get("code").asString()).isEqualTo("OUT_OF_RANGE");
    assertThat(height.get("params").toString()).isEqualTo("{\"min\":130,\"max\":210}");

    bad.put("heightCm", 210);
    Resp codes = api().put(ME, s.accessToken(), bad).expectError(400, "VALIDATION_FAILED");
    assertThat(codes.body().get("fieldErrors").findValuesAsString("field"))
        .containsExactly("ageRange", "bodyType", "fitPreference");
    assertThat(codes.body().get("fieldErrors").findValuesAsString("code"))
        .containsOnly("NOT_ALLOWED");

    bad.put("heightCm", 211);
    api().put(ME, s.accessToken(), bad).expectError(400, "VALIDATION_FAILED");
    // 경계값은 통과
    Map<String, Object> edge = withInfo(profile());
    edge.put("heightCm", 130);
    assertThat(api().put(ME, s.accessToken(), edge).expect(200).body().get("heightCm").asInt())
        .isEqualTo(130);
  }

  @Test
  void 비활성_핏_코드는_새로_고를_수_없지만_저장된_값은_둔다_사용_중이면_삭제_거부() {
    Session admin = admin();
    Session s = signup();
    String fit = "ZF_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    api()
        .post("/api/v1/admin/codes/FIT", admin.accessToken(), Map.of("code", fit, "label", "시험 핏"))
        .expect(201);
    try {
      Map<String, Object> body = withInfo(profile());
      body.put("fitPreference", fit);
      api().put(ME, s.accessToken(), body).expect(200);
      api()
          .patch("/api/v1/admin/codes/FIT/" + fit, admin.accessToken(), Map.of("active", false))
          .expect(200);

      // 저장돼 있던 값은 그대로 다시 보내도 된다
      assertThat(
              api()
                  .put(ME, s.accessToken(), body)
                  .expect(200)
                  .body()
                  .get("fitPreference")
                  .asString())
          .isEqualTo(fit);
      // 다른 사람이 새로 고르면 INACTIVE
      Resp other =
          api().put(ME, signup().accessToken(), body).expectError(400, "VALIDATION_FAILED");
      JsonNode e = other.body().get("fieldErrors").get(0);
      assertThat(e.get("field").asString()).isEqualTo("fitPreference");
      assertThat(e.get("code").asString()).isEqualTo("INACTIVE");

      // 프로필이 쓰는 코드는 지울 수 없다 (사용처 PROFILE)
      Resp del =
          api()
              .delete("/api/v1/admin/codes/FIT/" + fit, admin.accessToken())
              .expectError(409, "CODE_IN_USE");
      assertThat(del.body().get("params").get("usages").toString())
          .isEqualTo("[{\"kind\":\"PROFILE\",\"count\":1}]");

      // 관리자 회원 목록에 나의 정보가 보인다 (조회만)
      String email = api().get("/api/v1/users/me", s.accessToken()).body().get("email").asString();
      JsonNode row =
          api()
              .get("/api/v1/admin/users?q=" + email, admin.accessToken())
              .expect(200)
              .body()
              .get("items")
              .get(0);
      assertThat(row.get("ageRange").asString()).isEqualTo("AGE_30_EARLY");
      assertThat(row.get("bodyType").asString()).isEqualTo("WAVE");
      assertThat(row.get("heightCm").asInt()).isEqualTo(162);
      assertThat(row.get("fitPreference").asString()).isEqualTo(fit);
    } finally {
      jdbc.update("delete from tb_user_profile where user_id = ?", Long.parseLong(s.userId()));
      api().delete("/api/v1/admin/codes/FIT/" + fit, admin.accessToken()).expect(204);
    }
  }

  @Test
  void 탈퇴하면_나의_정보도_함께_지운다() {
    Session s = signup();
    api().put(ME, s.accessToken(), withInfo(profile())).expect(200);
    long id = Long.parseLong(s.userId());
    assertThat(count(id)).isEqualTo(1);
    // 탈퇴 유예 기간이 지나 회원을 지운다 (doc/27, 예약 작업이 부르는 것과 같다)
    users.delete(id);
    assertThat(count(id)).isZero();
  }

  private long count(long userId) {
    return jdbc.queryForObject(
        "select count(*) from tb_user_profile where user_id = ?", Long.class, userId);
  }

  @Test
  void PUT으로_저장하면_그대로_돌려주고_회원_이름도_바뀐다() {
    Session s = signup();
    Map<String, Object> body = profile();
    Resp put = api().put("/api/v1/users/me/profile", s.accessToken(), body).expect(200);
    assertThat(put.body().get("name").asString()).isEqualTo("새이름");
    assertThat(strings(put, "dressCodes")).containsExactly("CASUAL", "FORMAL");
    assertThat(strings(put, "colors")).containsExactly("black", "lightblue");
    assertThat(put.body().get("workDays").toString()).isEqualTo("[0,3,6]");
    assertThat(put.body().get("notify").toString())
        .isEqualTo("{\"morning\":false,\"evening\":true,\"dMinus1\":false}");
    assertThat(put.body().get("onboarded").asBoolean()).isTrue();

    Resp get = api().get("/api/v1/users/me/profile", s.accessToken()).expect(200);
    assertThat(get.body()).isEqualTo(put.body());
    assertThat(api().get("/api/v1/users/me", s.accessToken()).body().get("name").asString())
        .isEqualTo("새이름");

    // 다시 저장 (갱신)
    body.put("workType", "CUSTOM");
    body.put("onboarded", false);
    Resp again = api().put("/api/v1/users/me/profile", s.accessToken(), body).expect(200);
    assertThat(again.body().get("workType").asString()).isEqualTo("CUSTOM");
    assertThat(again.body().get("onboarded").asBoolean()).isFalse();
  }

  @Test
  void PUT_검증_실패() {
    Session s = signup();
    Map<String, Object> bad = profile();
    bad.put("workDays", List.of(1, 7));
    bad.put("name", "");
    Resp r =
        api()
            .put("/api/v1/users/me/profile", s.accessToken(), bad)
            .expectError(400, "VALIDATION_FAILED");
    assertThat(r.body().get("fieldErrors").findValuesAsString("field"))
        .anyMatch(f -> f.startsWith("workDays"))
        .contains("name");

    Map<String, Object> badEnum = profile();
    badEnum.put("colors", List.of("fuchsia"));
    Resp e =
        api()
            .put("/api/v1/users/me/profile", s.accessToken(), badEnum)
            .expectError(400, "VALIDATION_FAILED");
    assertThat(e.body().get("fieldErrors").get(0).get("field").asString()).startsWith("colors");

    Map<String, Object> noNotify = profile();
    noNotify.remove("notify");
    api()
        .put("/api/v1/users/me/profile", s.accessToken(), noNotify)
        .expectError(400, "VALIDATION_FAILED");
  }

  private static Map<String, Object> profile() {
    Map<String, Object> body = new HashMap<>();
    body.put("name", "새이름");
    body.put("dressCodes", List.of("CASUAL", "FORMAL", "CASUAL"));
    body.put("moods", List.of("STREET"));
    body.put("colors", List.of("black", "lightblue"));
    body.put("workType", "WEEKEND");
    body.put("workDays", List.of(6, 0, 3));
    body.put("workRule", false);
    body.put("offRule", true);
    body.put("notify", Map.of("morning", false, "evening", true, "dMinus1", false));
    return body;
  }

  private static Map<String, Object> withInfo(Map<String, Object> body) {
    body.put("ageRange", "AGE_30_EARLY");
    body.put("bodyType", "WAVE");
    body.put("heightCm", 162);
    body.put("fitPreference", "SLIM");
    return body;
  }

  private static List<String> strings(Resp r, String field) {
    List<String> values = new java.util.ArrayList<>();
    r.body().get(field).forEach(n -> values.add(n.asString()));
    return values;
  }
}
