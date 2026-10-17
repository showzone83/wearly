package app.wearly.user;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileIntegrationTest extends IntegrationTestBase {

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

  private static List<String> strings(Resp r, String field) {
    List<String> values = new java.util.ArrayList<>();
    r.body().get(field).forEach(n -> values.add(n.asString()));
    return values;
  }
}
