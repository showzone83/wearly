package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** 로그인 세션(Valkey)·강제 로그아웃·관리자 세션 API (16 문서 3·4장) */
class AdminSessionIntegrationTest extends IntegrationTestBase {

  @Autowired StringRedisTemplate redis;

  @Autowired JdbcTemplate jdbc;

  static final String PASSWORD = "abcd1234";

  private Session login(String email, String client, String userAgent, String forwardedFor) {
    HttpHeaders h = new HttpHeaders();
    if (client != null) {
      h.set("X-Wearly-Client", client);
    }
    if (userAgent != null) {
      h.set(HttpHeaders.USER_AGENT, userAgent);
    }
    if (forwardedFor != null) {
      h.set("X-Forwarded-For", forwardedFor);
    }
    return TestApi.session(
        api()
            .exchange(
                HttpMethod.POST,
                "/api/v1/auth/login",
                null,
                Map.of("email", email, "password", PASSWORD),
                h)
            .expect(200));
  }

  /** Access 토큰의 sid 클레임 */
  private static String sid(String accessToken) {
    String payload =
        new String(
            Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]), StandardCharsets.UTF_8);
    return TestApi.JSON.readTree(payload).get("sid").asString();
  }

  private Resp refresh(String refreshToken) {
    return api().post("/api/v1/auth/refresh", null, Map.of("refreshToken", refreshToken));
  }

  private List<String> events(String userId) {
    return jdbc.queryForList(
        "select type from tb_auth_event where user_id = ? order by auth_event_id",
        String.class,
        Long.parseLong(userId));
  }

  @Test
  void 로그인하면_세션에_기기_종류_User_Agent_IP가_남고_관리자가_본다() {
    String email = uniqueEmail();
    Session signup = api().signup(email, PASSWORD, "기기");
    Session web =
        login(email, "web", "Mozilla/5.0 (Macintosh) Chrome/130", "203.0.113.5, 10.0.0.1");
    Session guessed = login(email, null, "Mozilla/5.0 (iPhone) Safari", null);
    String admin = admin().accessToken();

    JsonNode r = api().get("/api/v1/admin/users/" + signup.userId() + "/sessions", admin).body();
    assertThat(r.get("sessions")).hasSize(3);
    JsonNode first = r.get("sessions").get(0);
    // lastSeenAt 최신순: 마지막 로그인이 먼저
    assertThat(first.get("sessionId").asString()).isEqualTo(sid(guessed.accessToken()));
    assertThat(first.get("client").asString()).isEqualTo("WEB"); // 헤더 없음 + 브라우저 UA
    assertThat(first.get("current").asBoolean()).isFalse();
    JsonNode webRow = r.get("sessions").get(1);
    assertThat(webRow.get("sessionId").asString()).isEqualTo(sid(web.accessToken()));
    assertThat(webRow.get("client").asString()).isEqualTo("WEB");
    assertThat(webRow.get("ip").asString()).isEqualTo("203.0.113.5");
    assertThat(webRow.get("userAgent").asString()).contains("Chrome/130");
    assertThat(webRow.get("email").asString()).isEqualTo(email);
    assertThat(webRow.get("role").asString()).isEqualTo("USER");
    assertThat(webRow.get("userId").asString()).isEqualTo(signup.userId());
    assertThat(webRow.get("createdAt").asString()).endsWith("Z");
    assertThat(webRow.get("lastRefreshAt").isNull()).isTrue();
    // Java HttpClient(헤더 없음, 브라우저가 아님) → APP
    assertThat(r.get("sessions").get(2).get("client").asString()).isEqualTo("APP");

    assertThat(r.get("events")).hasSize(3);
    JsonNode ev = r.get("events").get(0);
    assertThat(ev.get("type").asString()).isEqualTo("LOGIN");
    assertThat(ev.get("sessionId").asString()).isEqualTo(sid(guessed.accessToken()));
    assertThat(ev.has("client") && ev.has("ip") && ev.has("userAgent")).isTrue();
    assertThat(ev.get("createdAt").asString()).endsWith("Z");

    api().get("/api/v1/admin/users/999999999/sessions", admin).expectError(404, "NOT_FOUND");
  }

  @Test
  void 목록은_lastSeenAt_최신순이고_검색_기기_필터_현재_세션_표시() {
    String email = uniqueEmail();
    Session a = api().signup(email, PASSWORD, "목록");
    Session b = login(email, "web", null, null);
    Session admin = admin();

    JsonNode page =
        api()
            .get("/api/v1/admin/sessions?q={q}&size=10", admin.accessToken(), Map.of("q", email))
            .expect(200)
            .body();
    assertThat(page.get("total").asLong()).isEqualTo(2);
    assertThat(page.get("page").asInt()).isZero();
    assertThat(page.get("size").asInt()).isEqualTo(10);
    assertThat(page.get("items").findValuesAsString("sessionId"))
        .containsExactly(sid(b.accessToken()), sid(a.accessToken()));

    // 갱신하면 lastSeenAt이 바뀌어 맨 앞으로
    refresh(a.refreshToken()).expect(200);
    JsonNode after =
        api()
            .get(
                "/api/v1/admin/sessions?q={q}",
                admin.accessToken(),
                Map.of("q", email.toUpperCase()))
            .body();
    assertThat(after.get("items").findValuesAsString("sessionId"))
        .containsExactly(sid(a.accessToken()), sid(b.accessToken()));
    assertThat(after.get("items").get(0).get("lastRefreshAt").isString()).isTrue();

    JsonNode web =
        api()
            .get("/api/v1/admin/sessions?q={q}&client=WEB", admin.accessToken(), Map.of("q", email))
            .body();
    assertThat(web.get("items").findValuesAsString("sessionId"))
        .containsExactly(sid(b.accessToken()));
    api()
        .get("/api/v1/admin/sessions?client=TV", admin.accessToken())
        .expectError(400, "VALIDATION_FAILED");

    // 필터 없는 목록: 페이지 모양, 내 현재 세션은 current
    JsonNode all = api().get("/api/v1/admin/sessions?size=100", admin.accessToken()).body();
    assertThat(all.get("total").asLong()).isGreaterThanOrEqualTo(3);
    List<String> mine = new ArrayList<>();
    for (JsonNode item : all.get("items")) {
      if (item.get("current").asBoolean()) {
        mine.add(item.get("sessionId").asString());
      }
    }
    assertThat(mine).containsExactly(sid(admin.accessToken()));

    JsonNode dash = api().get("/api/v1/admin/dashboard", admin.accessToken()).body();
    assertThat(dash.get("activeSessions").asLong()).isGreaterThanOrEqualTo(3);
    assertThat(dash.get("activeUsers24h").asLong()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void 세션_하나를_끊으면_그_기기의_다음_요청이_바로_401이고_다른_기기는_그대로() {
    String email = uniqueEmail();
    Session phone = api().signup(email, PASSWORD, "강제");
    Session laptop = login(email, "web", null, null);
    Session admin = admin();
    api().get("/api/v1/users/me", phone.accessToken()).expect(200);

    api()
        .delete("/api/v1/admin/sessions/" + sid(phone.accessToken()), admin.accessToken())
        .expect(204);

    api().get("/api/v1/users/me", phone.accessToken()).expectError(401, "TOKEN_INVALID");
    refresh(phone.refreshToken()).expectError(401, "REFRESH_INVALID");
    api().get("/api/v1/users/me", laptop.accessToken()).expect(200);
    refresh(laptop.refreshToken()).expect(200);
    assertThat(events(phone.userId())).containsExactly("LOGIN", "LOGIN", "FORCED_LOGOUT");

    JsonNode audit =
        api().get("/api/v1/admin/audit-logs?targetType=SESSION", admin.accessToken()).body();
    JsonNode row = audit.get("items").get(0);
    assertThat(row.get("action").asString()).isEqualTo("SESSION_REVOKE");
    assertThat(row.get("targetId").asString()).isEqualTo(sid(phone.accessToken()));
    assertThat(row.get("before").get("email").asString()).isEqualTo(email);

    // 이미 끊긴 세션·없는 세션은 404, 내 현재 세션은 400
    api()
        .delete("/api/v1/admin/sessions/" + sid(phone.accessToken()), admin.accessToken())
        .expectError(404, "NOT_FOUND");
    Resp self =
        api()
            .delete("/api/v1/admin/sessions/" + sid(admin.accessToken()), admin.accessToken())
            .expectError(400, "VALIDATION_FAILED");
    assertThat(self.body().get("fieldErrors").findValuesAsString("field"))
        .containsExactly("sessionId");
    api().get("/api/v1/admin/dashboard", admin.accessToken()).expect(200);
  }

  @Test
  void 회원_전체_로그아웃은_모든_기기를_끊고_자기_자신은_400() {
    String email = uniqueEmail();
    Session one = api().signup(email, PASSWORD, "전체");
    Session two = login(email, "web", null, null);
    Session admin = admin();

    api()
        .delete("/api/v1/admin/users/" + one.userId() + "/sessions", admin.accessToken())
        .expect(204);
    for (Session s : List.of(one, two)) {
      api().get("/api/v1/users/me", s.accessToken()).expectError(401, "TOKEN_INVALID");
      refresh(s.refreshToken()).expectError(401, "REFRESH_INVALID");
    }
    assertThat(redis.opsForSet().size("wearly:user:" + one.userId() + ":sessions")).isZero();
    JsonNode audit =
        api().get("/api/v1/admin/audit-logs?targetType=USER", admin.accessToken()).body();
    JsonNode row = audit.get("items").get(0);
    assertThat(row.get("action").asString()).isEqualTo("USER_SESSIONS_REVOKE");
    assertThat(row.get("targetId").asString()).isEqualTo(one.userId());
    assertThat(row.get("before").get("sessionCount").asInt()).isEqualTo(2);

    // 다시 로그인하면 된다
    login(email, null, null, null);

    api()
        .delete("/api/v1/admin/users/" + admin.userId() + "/sessions", admin.accessToken())
        .expectError(400, "VALIDATION_FAILED");
    api()
        .delete("/api/v1/admin/users/999999999/sessions", admin.accessToken())
        .expectError(404, "NOT_FOUND");
    api().get("/api/v1/users/me", admin.accessToken()).expect(200);
  }

  @Test
  void 관리자_세션_API는_ADMIN만() {
    Session user = signup();
    api().get("/api/v1/admin/sessions", null).expectError(401, "UNAUTHORIZED");
    api().get("/api/v1/admin/sessions", user.accessToken()).expectError(403, "FORBIDDEN");
    api()
        .get("/api/v1/admin/users/" + user.userId() + "/sessions", user.accessToken())
        .expectError(403, "FORBIDDEN");
    api()
        .delete("/api/v1/admin/sessions/" + sid(user.accessToken()), user.accessToken())
        .expectError(403, "FORBIDDEN");
    api()
        .delete("/api/v1/admin/users/" + user.userId() + "/sessions", user.accessToken())
        .expectError(403, "FORBIDDEN");
    api().get("/api/v1/users/me", user.accessToken()).expect(200);
  }

  @Test
  void 회원을_정지하면_세션을_모두_끊고_FORCED_LOGOUT을_남긴다() {
    Session u = signup();
    Session admin = admin();
    api()
        .patch(
            "/api/v1/admin/users/" + u.userId(), admin.accessToken(), Map.of("status", "SUSPENDED"))
        .expect(200);

    assertThat(redis.hasKey("wearly:session:" + sid(u.accessToken()))).isFalse();
    assertThat(events(u.userId())).containsExactly("LOGIN", "FORCED_LOGOUT");
    refresh(u.refreshToken()).expectError(401, "REFRESH_INVALID");
    api().get("/api/v1/users/me", u.accessToken()).expectError(401, "TOKEN_INVALID");
  }

  @Test
  void 같은_refresh_토큰으로_동시에_갱신하면_한쪽만_성공한다() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 5; round++) {
        Session s = signup();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Resp>> calls = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
          calls.add(
              pool.submit(
                  () -> {
                    start.await();
                    return refresh(s.refreshToken());
                  }));
        }
        start.countDown();
        List<Resp> results = new ArrayList<>();
        for (Future<Resp> f : calls) {
          results.add(f.get());
        }
        assertThat(results.stream().filter(r -> r.status() == 200)).hasSize(1);
        Resp loser = results.stream().filter(r -> r.status() != 200).findFirst().orElseThrow();
        // 늦은 쪽은 이미 회전된 토큰을 쓴 것 → 재사용으로 보고 세션을 끊는다 (이전 DB 구현과 같은 동작)
        loser.expectError(401, "REFRESH_REUSED");
        assertThat(redis.hasKey("wearly:session:" + sid(s.accessToken()))).isFalse();
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void lastSeenAt은_같은_세션에서_1분에_한_번만_쓴다() {
    Session s = signup();
    String key = "wearly:session:" + sid(s.accessToken());
    String created = (String) redis.opsForHash().get(key, "lastSeenAt");
    api().get("/api/v1/users/me", s.accessToken()).expect(200);
    assertThat(redis.opsForHash().get(key, "lastSeenAt")).isEqualTo(created);

    String old = Long.toString(Instant.now().minusSeconds(120).toEpochMilli());
    redis.opsForHash().put(key, "lastSeenAt", old);
    api().get("/api/v1/users/me", s.accessToken()).expect(200);
    long seen = Long.parseLong((String) redis.opsForHash().get(key, "lastSeenAt"));
    assertThat(seen).isGreaterThan(Long.parseLong(old) + 60_000);
    assertThat(redis.opsForZSet().score("wearly:sessions", sid(s.accessToken())))
        .isEqualTo((double) seen);
  }

  @Test
  void CORS_사전_요청에서_X_Wearly_Client_헤더를_허용한다() {
    for (String origin : List.of("http://localhost:5173", "http://localhost:8081")) {
      Resp r =
          api()
              .options(
                  "/api/v1/admin/sessions",
                  origin,
                  "GET",
                  "authorization,content-type,x-wearly-client");
      assertThat(r.status()).isEqualTo(200);
      assertThat(r.headers().getAccessControlAllowOrigin()).isEqualTo(origin);
      assertThat(r.headers().getAccessControlAllowHeaders())
          .map(String::toLowerCase)
          .contains("x-wearly-client");
    }
  }
}
