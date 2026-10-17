package app.wearly.appsetting;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/** 앱 설정·테마 (12 문서 3장): 공개 GET /app/config, ETag 304, 관리자 GET·PUT /admin/app/theme, 변경 이력 */
class AppConfigIntegrationTest extends IntegrationTestBase {

  static final String CONFIG = "/api/v1/app/config";
  static final String THEME = "/api/v1/admin/app/theme";

  @Test
  void 공개_설정과_관리자_테마_변경_흐름() {
    Session admin = admin();
    String a = admin.accessToken();

    // 처음 상태: nyang, 바꾼 적 없음 (이 클래스만 테마를 바꾼다)
    JsonNode initial = api().get(THEME, a).expect(200).body();
    assertThat(initial.get("themeId").asString()).isEqualTo("nyang");
    assertThat(initial.get("updatedAt").isNull()).isTrue();
    assertThat(initial.get("updatedBy").isNull()).isTrue();

    // 토큰 없이 200, 형식·헤더
    Resp config = api().get(CONFIG, null).expect(200);
    assertThat(config.body().size()).isEqualTo(2);
    assertThat(config.body().get("themeId").asString()).isEqualTo("nyang");
    String version = config.body().get("version").asString();
    assertThat(version).isEqualTo("initial");
    String etag = config.headers().getETag();
    assertThat(etag).isEqualTo("\"" + version + "\"");
    assertThat(config.headers().getCacheControl()).isEqualTo("no-cache");

    // 같은 ETag면 304
    HttpHeaders ifNone = new HttpHeaders();
    ifNone.setIfNoneMatch(etag);
    Resp notModified = api().get(CONFIG, null, ifNone);
    assertThat(notModified.status()).isEqualTo(304);
    assertThat(notModified.headers().getETag()).isEqualTo(etag);

    // 관리자가 바꾸면 version·ETag가 바뀐다
    JsonNode changed = api().put(THEME, a, Map.of("themeId", "closeta")).expect(200).body();
    assertThat(changed.get("themeId").asString()).isEqualTo("closeta");
    assertThat(changed.get("updatedAt").asString()).endsWith("Z");
    assertThat(changed.get("updatedBy").asString()).isEqualTo(ADMIN_EMAIL);
    assertThat(api().get(THEME, a).expect(200).body()).isEqualTo(changed);

    Resp after = api().get(CONFIG, null, ifNone).expect(200);
    assertThat(after.body().get("themeId").asString()).isEqualTo("closeta");
    assertThat(after.body().get("version").asString())
        .isEqualTo(changed.get("updatedAt").asString());
    assertThat(after.headers().getETag()).isNotEqualTo(etag);

    // 같은 테마로 다시 PUT하면 아무것도 바뀌지 않는다 (version 그대로, 이력 없음)
    JsonNode same = api().put(THEME, a, Map.of("themeId", "closeta")).expect(200).body();
    assertThat(same).isEqualTo(changed);
    assertThat(api().get(CONFIG, null).expect(200).body()).isEqualTo(after.body());

    // 변경 이력: APP_THEME_UPDATE 한 건
    JsonNode audits =
        api().get("/api/v1/admin/audit-logs?targetType=APP_SETTING", a).expect(200).body();
    assertThat(audits.get("total").asLong()).isEqualTo(1);
    JsonNode log = audits.get("items").get(0);
    assertThat(log.get("action").asString()).isEqualTo("APP_THEME_UPDATE");
    assertThat(log.get("targetType").asString()).isEqualTo("APP_SETTING");
    assertThat(log.get("targetId").asString()).isEqualTo("theme");
    assertThat(log.get("before").toString()).isEqualTo("{\"themeId\":\"nyang\"}");
    assertThat(log.get("after").toString()).isEqualTo("{\"themeId\":\"closeta\"}");
    assertThat(log.get("adminEmail").asString()).isEqualTo(ADMIN_EMAIL);

    // 되돌려 둔다
    assertThat(
            api()
                .put(THEME, a, Map.of("themeId", "nyang"))
                .expect(200)
                .body()
                .get("themeId")
                .asString())
        .isEqualTo("nyang");
  }

  @Test
  void 만료되거나_틀린_토큰이_같이_와도_공개_설정은_200이고_CORS에_ETag를_노출한다() {
    api().get(CONFIG, "not-a-jwt").expect(200);

    HttpHeaders cors = new HttpHeaders();
    cors.setOrigin("http://localhost:8081");
    Resp r = api().get(CONFIG, null, cors).expect(200);
    assertThat(r.headers().getAccessControlAllowOrigin()).isEqualTo("http://localhost:8081");
    assertThat(r.headers().getAccessControlExposeHeaders()).contains("ETag");
  }

  @Test
  void 테마_변경은_관리자만_허용_값만() {
    Session user = signup();
    api().get(THEME, user.accessToken()).expectError(403, "FORBIDDEN");
    api()
        .put(THEME, user.accessToken(), Map.of("themeId", "closeta"))
        .expectError(403, "FORBIDDEN");
    api().get(THEME, null).expectError(401, "UNAUTHORIZED");
    // 쓰기는 공개가 아니다
    api().put(CONFIG, null, Map.of("themeId", "closeta")).expectError(401, "UNAUTHORIZED");

    String a = admin().accessToken();
    for (Object body : new Object[] {Map.of("themeId", "dark"), Map.of(), Map.of("themeId", "")}) {
      Resp r = api().put(THEME, a, body).expectError(400, "VALIDATION_FAILED");
      assertThat(r.body().get("fieldErrors").findValuesAsString("field"))
          .containsExactly("themeId");
    }
    Resp none = api().put(THEME, a, null).expectError(400, "VALIDATION_FAILED");
    assertThat(none.body().get("fieldErrors").findValuesAsString("field"))
        .containsExactly("themeId");
    api().get("/api/v1/admin/audit-logs?targetType=NOPE", a).expectError(400, "VALIDATION_FAILED");
  }
}
