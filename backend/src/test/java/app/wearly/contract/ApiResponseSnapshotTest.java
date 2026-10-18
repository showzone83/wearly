package app.wearly.contract;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.FakeHttpServer;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import app.wearly.support.TestMinio;
import app.wearly.support.TestValkey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * API 응답 동일성 확인 (5차 리팩터링). 리팩터링 전(origin/dev)에 주요 엔드포인트의 상태 코드와 응답 JSON을 기록해 둔
 * src/test/resources/snapshots/api-responses.json과 지금 응답을 비교한다.
 *
 * <ul>
 *   <li>전용 PostgreSQL 컨테이너(빈 DB)로 띄워 ID·개수가 매번 같다. 시나리오는 한 메서드에서 순서대로 부른다.
 *   <li>매번 달라지는 값만 자리표시자로 바꾼다: 토큰, 시각(ISO-8601 UTC, 밀리초 3자리 또는 없음만 인정), 코드 버전·ETag의 시각, 소요
 *       시간(latencyMs·avgLatencyMs), 오늘 날짜, 가짜 공급자 서버 주소. 나머지(문자열 ID, null 포함 여부, 메시지)는 그대로 비교한다.
 *   <li>필드 순서는 비교하지 않는다 (JSON 객체 비교).
 *   <li>스냅샷 파일이 없으면 지금 응답으로 만든다 (리팩터링 전 브랜치에서 한 번). 다시 만들려면 파일을 지우고 돌린다.
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "wearly.ai.provider=mock",
      "wearly.ai.daily-limit=3",
      "wearly.ai.outfit-daily-limit=3",
      "wearly.ai.allow-base-url=true",
      "spring.config.import=",
      "wearly.admin.email=" + ApiResponseSnapshotTest.ADMIN_EMAIL,
      "wearly.admin.password=" + ApiResponseSnapshotTest.ADMIN_PASSWORD,
      "wearly.admin.name=스냅샷관리자",
      "ANTHROPIC_API_KEY=",
      "OPENAI_API_KEY=",
      "GEMINI_API_KEY=",
      "SNAPSHOT_FAKE_API_KEY=snapshot-fake-key-value-0001",
      // 웹 키 입력을 켠다 (고정 마스터 키, 테스트 전용)
      "wearly.secret-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
      // 날씨 주기 실행은 끈다 (실제 공급자를 부르지 않게)
      "wearly.weather.provider=mock",
      "wearly.weather.scheduler-enabled=false",
    })
class ApiResponseSnapshotTest {

  static final String ADMIN_EMAIL = "admin@snapshot.test";
  static final String ADMIN_PASSWORD = "admin1234";
  static final Path SNAPSHOT =
      Path.of("src", "test", "resources", "snapshots", "api-responses.json");
  static final JsonMapper JSON =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  static final Pattern INSTANT =
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z$");
  static final Pattern VERSION =
      Pattern.compile("^\"?\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z#(\\d+)\"?$");

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
              DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("wearly_snapshot")
          .withUsername("wearly")
          .withPassword("wearly");

  static final FakeHttpServer FAKE = new FakeHttpServer();

  /** 로그인 세션은 다른 통합 테스트와 같은 Valkey의 다른 DB 번호를 비워서 쓴다 (대시보드 activeSessions가 매번 같게) */
  static final int VALKEY_DB = 2;

  static {
    POSTGRES.start();
    TestValkey.flush(VALKEY_DB);
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    TestValkey.register(registry, VALKEY_DB);
    TestMinio.register(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @AfterAll
  static void stop() {
    FAKE.close();
  }

  @Value("${local.server.port}")
  int port;

  final Map<String, JsonNode> recorded = new LinkedHashMap<>();
  final String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
  TestApi api;

  @Test
  void 주요_API_응답이_리팩터링_전과_같다() throws IOException {
    api = new TestApi("http://localhost:" + port);
    scenario();
    ObjectNode actual = JSON.createObjectNode();
    recorded.forEach(actual::set);
    if (!Files.exists(SNAPSHOT)) {
      Files.createDirectories(SNAPSHOT.getParent());
      Files.writeString(SNAPSHOT, JSON.writeValueAsString(actual) + "\n", StandardCharsets.UTF_8);
      System.out.println("응답 스냅샷을 새로 만들었어요: " + SNAPSHOT.toAbsolutePath());
      return;
    }
    JsonNode expected = JSON.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
    List<String> diffs = new ArrayList<>();
    diff("", expected, actual, diffs);
    assertThat(diffs).as("스냅샷과 다른 곳 (기대 → 실제)").isEmpty();
  }

  private void scenario() {
    auth();
    Session a = login("snap-a@wearly.test", "pass1234");
    final Session b = login("snap-b@wearly.test", "pass1234");
    users(a);
    codes();
    Map<String, String> ids = wardrobe(a);
    analyze(a);
    outfit(a, b, ids);
    Session admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    // 정지하면 b의 세션이 끊기므로(16 문서) 해제 뒤 다시 로그인한 세션을 쓴다
    final Session reloggedB = adminUsers(admin, a, b);
    adminCodes(admin);
    adminAi(admin);
    adminLogs(admin);
    errors(a);
    record("users.delete", api.delete("/api/v1/users/me", reloggedB.accessToken()));
    record("users.delete.after", api.get("/api/v1/users/me", reloggedB.accessToken()));
  }

  private void auth() {
    record(
        "auth.signup",
        api.post(
            "/api/v1/auth/signup",
            null,
            Map.of("email", "Snap-A@Wearly.test", "password", "pass1234", "name", " 스냅A ")));
    record(
        "auth.signup.b",
        api.post(
            "/api/v1/auth/signup",
            null,
            Map.of("email", "snap-b@wearly.test", "password", "pass1234", "name", "스냅B")));
    record(
        "auth.signup.duplicate",
        api.post(
            "/api/v1/auth/signup",
            null,
            Map.of("email", "snap-a@wearly.test", "password", "pass1234", "name", "중복")));
    record(
        "auth.signup.invalid",
        api.post(
            "/api/v1/auth/signup",
            null,
            Map.of("email", "not-email", "password", "short", "name", "가".repeat(21))));
    record("auth.signup.empty", api.post("/api/v1/auth/signup", null, Map.of()));
    record("auth.signup.badJson", api.post("/api/v1/auth/signup", null, "{\"email\":"));
    Resp login =
        api.post(
            "/api/v1/auth/login",
            null,
            Map.of("email", "snap-a@wearly.test", "password", "pass1234"));
    record("auth.login", login);
    record(
        "auth.login.wrong",
        api.post(
            "/api/v1/auth/login",
            null,
            Map.of("email", "snap-a@wearly.test", "password", "nope1234")));
    record(
        "auth.login.unknown",
        api.post("/api/v1/auth/login", null, Map.of("email", "x@wearly.test", "password", "x")));
    String refresh = login.body().get("refreshToken").asString();
    Resp rotated = api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", refresh));
    record("auth.refresh", rotated);
    record(
        "auth.refresh.reused",
        api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", refresh)));
    record("auth.refresh.missing", api.post("/api/v1/auth/refresh", null, Map.of()));
    record(
        "auth.logout",
        api.post("/api/v1/auth/logout", null, Map.of("refreshToken", "whatever-token")));
  }

  private Session login(String email, String password) {
    return TestApi.session(
        api.post("/api/v1/auth/login", null, Map.of("email", email, "password", password))
            .expect(200));
  }

  private void users(Session a) {
    String t = a.accessToken();
    record("users.me", api.get("/api/v1/users/me", t));
    record("profile.get.default", api.get("/api/v1/users/me/profile", t));
    Map<String, Object> profile = profile();
    record("profile.put", api.put("/api/v1/users/me/profile", t, profile));
    record("profile.get", api.get("/api/v1/users/me/profile", t));
    Map<String, Object> badEnum = new HashMap<>(profile);
    badEnum.put("dressCodes", List.of("CASUAL", "XYZ"));
    record("profile.put.badEnum", api.put("/api/v1/users/me/profile", t, badEnum));
    record(
        "profile.put.invalid",
        api.put("/api/v1/users/me/profile", t, Map.of("name", " ", "workDays", List.of(7))));
    Map<String, Object> badColor = new HashMap<>(profile);
    badColor.put("colors", List.of("navy", "fuchsia"));
    badColor.put("moods", List.of("NOPE"));
    record("profile.put.badCode", api.put("/api/v1/users/me/profile", t, badColor));
    Map<String, Object> keepOnboarded = new HashMap<>(profile);
    keepOnboarded.put("onboarded", false);
    record("profile.put.notOnboarded", api.put("/api/v1/users/me/profile", t, keepOnboarded));
    record("profile.put.restore", api.put("/api/v1/users/me/profile", t, profile));
  }

  private static Map<String, Object> profile() {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("name", "  스냅에이  ");
    p.put("dressCodes", List.of("FORMAL", "CASUAL", "FORMAL"));
    p.put("moods", List.of("CLASSIC", "NEAT"));
    p.put("colors", List.of("navy", "white", "navy"));
    p.put("workType", "CUSTOM");
    p.put("workDays", List.of(5, 1, 3, 1));
    p.put("workRule", false);
    p.put("offRule", true);
    p.put("notify", Map.of("morning", true, "evening", false, "dMinus1", true));
    return p;
  }

  private void codes() {
    Resp codes = api.get("/api/v1/codes", null);
    record("codes", codes);
    HttpHeaders h = new HttpHeaders();
    h.setIfNoneMatch(codes.headers().getETag());
    Resp notModified = api.get("/api/v1/codes", null, h);
    ObjectNode n = JSON.createObjectNode();
    n.put("status", notModified.status());
    n.put("etag", normalizeText(notModified.headers().getETag()));
    n.put("cacheControl", notModified.headers().getCacheControl());
    recorded.put("codes.notModified", n);
  }

  private Map<String, String> wardrobe(Session a) {
    String t = a.accessToken();
    Map<String, Object> item = item("  네이비 니트 ", "TOP", "KNIT", "navy");
    item.put("brand", "  브랜드  ");
    item.put("wornCount", 4);
    item.put("lastWornAt", today);
    item.put("seasons", List.of("FALL", "WINTER", "FALL"));
    Resp created = api.post("/api/v1/wardrobe/items", t, item);
    record("wardrobe.create", created);
    final String id = created.body().get("id").asString();
    Map<String, Object> invalid = new HashMap<>();
    invalid.put("name", " ");
    invalid.put("formality", 4);
    invalid.put("wornCount", -1);
    invalid.put("seasons", java.util.Arrays.asList("SPRING", null));
    record("wardrobe.create.invalid", api.post("/api/v1/wardrobe/items", t, invalid));
    Map<String, Object> badCodes = item("코드 틀림", "TOP", "JEANS", "fuchsia");
    badCodes.put("styles", List.of("NEAT", "NOPE"));
    badCodes.put("status", "LOST");
    record("wardrobe.create.badCodes", api.post("/api/v1/wardrobe/items", t, badCodes));
    record(
        "wardrobe.create.badType",
        api.post("/api/v1/wardrobe/items", t, "{\"name\":\"x\",\"formality\":\"abc\"}"));

    List<Map<String, Object>> bulk = new ArrayList<>();
    bulk.add(item("화이트 셔츠", "TOP", "SHIRT", "white"));
    bulk.add(item("차콜 슬랙스", "BOTTOM", "SLACKS", "charcoal"));
    bulk.add(item("베이지 와이드", "BOTTOM", "WIDE", "beige"));
    bulk.add(item("블랙 셔츠 원피스", "DRESS", "SHIRT_DRESS", "black"));
    bulk.add(item("네이비 블레이저", "OUTER", "BLAZER", "navy"));
    bulk.add(item("블랙 로퍼", "SHOES", "LOAFER", "black"));
    bulk.add(item("화이트 스니커즈", "SHOES", "SNEAKER", "white"));
    bulk.add(item("브라운 토트", "BAG", "TOTE", "brown"));
    Map<String, Object> laundry = item("핑크 블라우스", "TOP", "BLOUSE", "pink");
    laundry.put("status", "LAUNDRY");
    laundry.put("lastWornAt", "2020-01-01");
    bulk.add(laundry);
    Resp bulkResp = api.post("/api/v1/wardrobe/items/bulk", t, Map.of("items", bulk));
    record("wardrobe.bulk", bulkResp);
    Map<String, String> ids = new LinkedHashMap<>();
    ids.put("네이비 니트", id);
    for (JsonNode i : bulkResp.body().get("items")) {
      ids.put(i.get("name").asString(), i.get("id").asString());
    }
    List<Map<String, Object>> badBulk = new ArrayList<>();
    badBulk.add(item("괜찮음", "TOP", "SHIRT", "white"));
    Map<String, Object> broken = item("", "SHOES", "KNIT", "white");
    badBulk.add(broken);
    record(
        "wardrobe.bulk.invalid",
        api.post("/api/v1/wardrobe/items/bulk", t, Map.of("items", badBulk)));
    record("wardrobe.bulk.empty", api.post("/api/v1/wardrobe/items/bulk", t, Map.of()));

    record("wardrobe.list", api.get("/api/v1/wardrobe/items", t));
    record(
        "wardrobe.list.filtered",
        api.get("/api/v1/wardrobe/items?category=TOP&status=OWNED&sort=name", t));
    record("wardrobe.list.worn", api.get("/api/v1/wardrobe/items?sort=worn&color=navy", t));
    record("wardrobe.list.q", api.get("/api/v1/wardrobe/items?q={q}", t, Map.of("q", "브랜드")));
    record(
        "wardrobe.list.likeEscape", api.get("/api/v1/wardrobe/items?q={q}", t, Map.of("q", "%_")));
    record(
        "wardrobe.list.invalid",
        api.get("/api/v1/wardrobe/items?category=HAT&status=GONE&color=x&sort=price", t));
    record("wardrobe.get", api.get("/api/v1/wardrobe/items/" + id, t));
    record("wardrobe.get.notFound", api.get("/api/v1/wardrobe/items/999999", t));
    record("wardrobe.get.badId", api.get("/api/v1/wardrobe/items/abc", t));

    Map<String, Object> patch = new HashMap<>();
    patch.put("name", " 네이비 울 니트 ");
    patch.put("seasons", List.of("WINTER"));
    patch.put("brand", null);
    patch.put("unknownField", "무시");
    record("wardrobe.patch", api.patch("/api/v1/wardrobe/items/" + id, t, patch));
    record(
        "wardrobe.patch.typeError",
        api.patch("/api/v1/wardrobe/items/" + id, t, "{\"formality\":\"x\",\"wornCount\":\"y\"}"));
    record(
        "wardrobe.patch.listTypeError",
        api.patch("/api/v1/wardrobe/items/" + id, t, "{\"seasons\":\"SPRING\"}"));
    Map<String, Object> nullName = new HashMap<>();
    nullName.put("name", null);
    nullName.put("category", null);
    record("wardrobe.patch.nulls", api.patch("/api/v1/wardrobe/items/" + id, t, nullName));
    record(
        "wardrobe.patch.invalid",
        api.patch(
            "/api/v1/wardrobe/items/" + id,
            t,
            Map.of("formality", 9, "brand", "b".repeat(41), "wornCount", -3)));
    record(
        "wardrobe.patch.badCode",
        api.patch("/api/v1/wardrobe/items/" + id, t, Map.of("subType", "JEANS")));
    record("wardrobe.patch.notObject", api.patch("/api/v1/wardrobe/items/" + id, t, "[1]"));
    record(
        "wardrobe.patch.notFound",
        api.patch("/api/v1/wardrobe/items/999999", t, Map.of("name", "x")));
    record("wardrobe.patch.notFoundBadBody", api.patch("/api/v1/wardrobe/items/999999", t, "[1]"));
    record("wardrobe.stats", api.get("/api/v1/wardrobe/stats", t));
    Map<String, Object> temp = item("지울 옷", "ACC", "WATCH", "black");
    String tempId = api.post("/api/v1/wardrobe/items", t, temp).body().get("id").asString();
    record("wardrobe.delete", api.delete("/api/v1/wardrobe/items/" + tempId, t));
    record("wardrobe.delete.again", api.delete("/api/v1/wardrobe/items/" + tempId, t));
    return ids;
  }

  private static Map<String, Object> item(
      String name, String category, String subType, String color) {
    Map<String, Object> m = new HashMap<>();
    m.put("name", name);
    m.put("category", category);
    m.put("subType", subType);
    m.put("color", color);
    m.put("pattern", "SOLID");
    m.put("seasons", List.of("SPRING", "FALL"));
    m.put("styles", List.of("NEAT"));
    m.put("formality", 2);
    m.put("status", "OWNED");
    return m;
  }

  private void analyze(Session a) {
    String t = a.accessToken();
    String path = "/api/v1/wardrobe/items/analyze";
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5};
    record("ai.analyze", api.upload(path, t, "image", jpeg, "a.jpg", "image/jpeg"));
    record(
        "ai.analyze.sniffed",
        api.upload(
            path, t, "image", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4}, "b", null));
    record("ai.analyze.missing", api.upload(path, t, "other", jpeg, "a.jpg", "image/jpeg"));
    record("ai.analyze.empty", api.upload(path, t, "image", new byte[0], "a.jpg", "image/jpeg"));
    record(
        "ai.analyze.badType",
        api.upload(
            path, t, "image", "hello".getBytes(StandardCharsets.UTF_8), "a.txt", "text/plain"));
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 9, 9, 9, 9};
    record("ai.analyze.third", api.upload(path, t, "image", png, "c.png", "image/png"));
    record("ai.analyze.limit", api.upload(path, t, "image", png, "c.png", "image/png"));
  }

  private void outfit(Session a, Session b, Map<String, String> ids) {
    String t = a.accessToken();
    String path = "/api/v1/outfits/recommendations";
    record("outfit.recommend", api.post(path, t, Map.of("context", context())));
    Map<String, Object> pinned = context();
    pinned.put("fixed", Map.of("TOP", ids.get("화이트 셔츠")));
    pinned.put("anchorItemId", ids.get("블랙 로퍼"));
    pinned.put("avoidColors", List.of("brown"));
    pinned.put("concepts", List.of("CLASSIC"));
    pinned.put("timeOfDay", "PM");
    pinned.put("memo", "  발표가 있는 날  ");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("context", pinned);
    body.put("excludeItemIds", List.of(ids.get("차콜 슬랙스"), "999"));
    body.put(
        "recent", List.of(Map.of("date", "2026-10-16", "items", Map.of("TOP", ids.get("네이비 니트")))));
    body.put("likedItemIds", List.of(ids.get("네이비 블레이저")));
    body.put("dislikedItemIds", List.of(ids.get("화이트 스니커즈")));
    body.put("count", 2);
    record("outfit.recommend.pinned", api.post(path, t, body));
    Map<String, Object> badContext = new LinkedHashMap<>();
    badContext.put("date", "2026-13-01");
    badContext.put("occasion", "PARTY");
    badContext.put("mood", null);
    badContext.put("formality", "BIZ_CASUAL");
    badContext.put("weather", Map.of("temp", 99, "min", 1, "max", 2, "rainProb", 120, "cond", "X"));
    badContext.put("fixed", Map.of("HAT", "1"));
    badContext.put("avoidColors", List.of("fuchsia"));
    badContext.put("memo", "가".repeat(201));
    Map<String, Object> badBody = new LinkedHashMap<>();
    badBody.put("context", badContext);
    badBody.put("count", 9);
    badBody.put("recent", List.of(Map.of("date", "x")));
    record("outfit.recommend.invalid", api.post(path, t, badBody));
    record("outfit.recommend.noContext", api.post(path, t, Map.of()));
    Map<String, Object> badPin = context();
    badPin.put("fixed", Map.of("BOTTOM", ids.get("화이트 셔츠"), "DRESS", ids.get("블랙 셔츠 원피스")));
    badPin.put("anchorItemId", "999999");
    record("outfit.recommend.badPin", api.post(path, t, Map.of("context", badPin)));
    record(
        "outfit.recommend.insufficient",
        api.post(path, b.accessToken(), Map.of("context", context())));
    record("outfit.recommend.third", api.post(path, t, Map.of("context", context())));
    record("outfit.recommend.limit", api.post(path, t, Map.of("context", context())));
  }

  private static Map<String, Object> context() {
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("date", "2026-10-17");
    c.put("occasion", "COMMUTE");
    c.put("mood", "NEAT");
    c.put("formality", "BIZ_CASUAL");
    c.put("weather", Map.of("temp", 14, "min", 8, "max", 19, "rainProb", 20, "cond", "CLOUDY"));
    return c;
  }

  private Session adminUsers(Session admin, Session a, Session b) {
    String t = admin.accessToken();
    record("admin.dashboard", api.get("/api/v1/admin/dashboard", t));
    record("admin.users", api.get("/api/v1/admin/users", t));
    record("admin.users.q", api.get("/api/v1/admin/users?q=SNAP-&size=1&page=1", t));
    record("admin.users.filter", api.get("/api/v1/admin/users?status=ACTIVE&role=ADMIN", t));
    record("admin.users.invalid", api.get("/api/v1/admin/users?status=GONE", t));
    record("admin.users.badPage", api.get("/api/v1/admin/users?page=-1&size=500", t));
    String userPath = "/api/v1/admin/users/" + b.userId();
    record("admin.users.patch", api.patch(userPath, t, Map.of("status", "SUSPENDED")));
    record("admin.users.patch.suspendedLogin", api.get("/api/v1/users/me", b.accessToken()));
    record(
        "admin.users.patch.suspendedSignin",
        api.post(
            "/api/v1/auth/login",
            null,
            Map.of("email", "snap-b@wearly.test", "password", "pass1234")));
    record("admin.users.patch.restore", api.patch(userPath, t, Map.of("status", "ACTIVE")));
    // 16 문서: 정지 때 세션을 끊었으므로 해제해도 예전 Access 토큰은 401 TOKEN_INVALID (다시 로그인해야 한다)
    record("admin.users.patch.restoredOldToken", api.get("/api/v1/users/me", b.accessToken()));
    final Session relogged = login("snap-b@wearly.test", "pass1234");
    record(
        "admin.users.patch.self",
        api.patch(
            "/api/v1/admin/users/" + admin.userId(),
            t,
            Map.of("status", "SUSPENDED", "role", "USER")));
    record(
        "admin.users.patch.badValues", api.patch(userPath, t, "{\"status\":1,\"role\":\"OWNER\"}"));
    record("admin.users.patch.notObject", api.patch(userPath, t, "\"x\""));
    record(
        "admin.users.patch.notFound",
        api.patch("/api/v1/admin/users/999999", t, Map.of("role", "ADMIN")));
    record("admin.users.patch.promote", api.patch(userPath, t, Map.of("role", "ADMIN")));
    record("admin.users.patch.demote", api.patch(userPath, t, Map.of("role", "USER")));
    record("admin.forbidden", api.get("/api/v1/admin/dashboard", a.accessToken()));
    return relogged;
  }

  private void adminCodes(Session admin) {
    String t = admin.accessToken();
    record("admin.codes", api.get("/api/v1/admin/codes", t));
    Map<String, Object> teal = new LinkedHashMap<>();
    teal.put("code", " teal ");
    teal.put("label", " 틸 ");
    teal.put(
        "attrs",
        Map.of("hex", "#00aaaa", "neutral", false, "light", 0.4, "pairs", List.of("navy")));
    record("admin.codes.create", api.post("/api/v1/admin/codes/COLOR", t, teal));
    record("admin.codes.create.duplicate", api.post("/api/v1/admin/codes/COLOR", t, teal));
    Map<String, Object> badCode = new LinkedHashMap<>();
    badCode.put("code", "Bad Code");
    badCode.put("label", "");
    badCode.put("parent", "TOP");
    badCode.put("attrs", Map.of("hex", "red", "extra", 1, "pairs", List.of("teal", "nope")));
    record("admin.codes.create.invalid", api.post("/api/v1/admin/codes/COLOR", t, badCode));
    record(
        "admin.codes.create.subType",
        api.post(
            "/api/v1/admin/codes/SUB_TYPE",
            t,
            Map.of(
                "code",
                "RUGBY",
                "label",
                "럭비 셔츠",
                "parent",
                "TOP",
                "sortOrder",
                5,
                "active",
                false,
                "attrs",
                Map.of("volume", 0, "warmth", 1, "shape", " 셔츠 "))));
    record(
        "admin.codes.create.unknownGroup",
        api.post("/api/v1/admin/codes/NOPE", t, Map.of("code", "X", "label", "x")));
    record(
        "admin.codes.create.noBody",
        api.exchange(HttpMethod.POST, "/api/v1/admin/codes/STYLE", t, null));
    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("label", "청록");
    patch.put(
        "attrs",
        Map.of("hex", "#00AAAA", "neutral", true, "light", 0.5, "pairs", List.of("white")));
    record("admin.codes.patch", api.patch("/api/v1/admin/codes/COLOR/teal", t, patch));
    record(
        "admin.codes.patch.invalid",
        api.patch(
            "/api/v1/admin/codes/COLOR/teal",
            t,
            "{\"code\":\"other\",\"label\":5,\"parent\":\"TOP\",\"sortOrder\":\"x\","
                + "\"active\":\"y\",\"attrs\":[]}"));
    record(
        "admin.codes.patch.parentNull",
        api.patch("/api/v1/admin/codes/SUB_TYPE/RUGBY", t, "{\"parent\":null,\"active\":true}"));
    record(
        "admin.codes.patch.notFound",
        api.patch("/api/v1/admin/codes/COLOR/nope", t, Map.of("label", "x")));
    record("admin.codes.patch.notObject", api.patch("/api/v1/admin/codes/COLOR/teal", t, "[]"));
    Resp patterns = api.get("/api/v1/admin/codes", t);
    List<String> order = new ArrayList<>();
    for (JsonNode g : patterns.body().get("groups")) {
      if (g.get("group").asString().equals("PATTERN")) {
        g.get("codes").forEach(c -> order.add(0, c.get("code").asString()));
      }
    }
    record(
        "admin.codes.reorder",
        api.put("/api/v1/admin/codes/PATTERN/order", t, Map.of("codes", order)));
    record(
        "admin.codes.reorder.invalid",
        api.put("/api/v1/admin/codes/PATTERN/order", t, Map.of("codes", List.of("SOLID"))));
    record("admin.codes.delete.inUse", api.delete("/api/v1/admin/codes/COLOR/navy", t));
    record("admin.codes.delete.category", api.delete("/api/v1/admin/codes/CATEGORY/BAG", t));
    record("admin.codes.delete", api.delete("/api/v1/admin/codes/COLOR/teal", t));
    record("admin.codes.delete.notFound", api.delete("/api/v1/admin/codes/COLOR/teal", t));
    record("codes.afterAdmin", api.get("/api/v1/codes", null));
  }

  private void adminAi(Session admin) {
    String t = admin.accessToken();
    String base = "/api/v1/admin/ai/providers";
    record("admin.ai.providers", api.get(base, t));
    record("admin.ai.types", api.get("/api/v1/admin/ai/types", t));
    Map<String, Object> fake = new LinkedHashMap<>();
    fake.put("providerId", " fake-openai ");
    fake.put("type", "OPENAI");
    fake.put("displayName", " 가짜 OpenAI ");
    fake.put("model", "gpt-test");
    fake.put("apiKeyEnv", "SNAPSHOT_FAKE_API_KEY");
    fake.put("enabled", true);
    fake.put("priority", 7);
    Map<String, Object> options = new LinkedHashMap<>();
    options.put("baseUrl", FAKE.url() + "/v1");
    options.put("temperature", null);
    options.put("maxTokens", 1000);
    fake.put("options", options);
    record("admin.ai.providers.create", api.post(base, t, fake));
    record("admin.ai.providers.create.duplicate", api.post(base, t, fake));
    Map<String, Object> bad = new LinkedHashMap<>();
    bad.put("providerId", "mock");
    bad.put("type", "MISTRAL");
    bad.put("displayName", "");
    bad.put("model", "bad model!");
    bad.put("apiKeyEnv", "DB_PASSWORD");
    bad.put("enabled", true);
    bad.put("priority", -1);
    record("admin.ai.providers.create.invalid", api.post(base, t, bad));
    record(
        "admin.ai.providers.create.badOptions",
        api.post(
            base,
            t,
            Map.of(
                "providerId",
                "bad-opts",
                "type",
                "CLAUDE",
                "displayName",
                "x",
                "apiKeyEnv",
                "X_API_KEY",
                "options",
                Map.of("effort", "huge", "maxTokens", 0, "nope", 1))));
    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("displayName", "가짜 오픈AI");
    patch.put("priority", 8);
    patch.put("options", Map.of("baseUrl", FAKE.url() + "/v1", "detail", "low"));
    record("admin.ai.providers.patch", api.patch(base + "/fake-openai", t, patch));
    record(
        "admin.ai.providers.patch.invalid",
        api.patch(
            base + "/fake-openai",
            t,
            "{\"providerId\":\"x\",\"type\":7,\"enabled\":\"y\",\"priority\":99999,"
                + "\"options\":[],\"model\":\"\"}"));
    record(
        "admin.ai.providers.patch.enableNoModel",
        api.patch(base + "/openai", t, Map.of("enabled", true)));
    record(
        "admin.ai.providers.patch.notFound", api.patch(base + "/nope", t, Map.of("enabled", true)));
    record("admin.ai.providers.patch.notObject", api.patch(base + "/fake-openai", t, "1"));

    // 모델 목록 (07 문서 4장): 키가 있으면 제공사 목록(가짜 서버), 없으면 내장 목록
    FAKE.respond(
        200,
        "{\"object\":\"list\",\"data\":["
            + "{\"id\":\"gpt-test-vision\",\"object\":\"model\",\"created\":1760000000},"
            + "{\"id\":\"text-embedding-3-small\",\"object\":\"model\",\"created\":1705948997}]}");
    record("admin.ai.models.live", api.get(base + "/fake-openai/models", t));
    record("admin.ai.models.noKey", api.get(base + "/claude/models", t));
    record("admin.ai.models.emptyCatalog", api.get(base + "/openai/models?refresh=true", t));
    record("admin.ai.models.notFound", api.get(base + "/nope/models", t));

    FAKE.respond(200, openAiResponse());
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 7, 7, 7};
    record(
        "admin.ai.test",
        api.upload(base + "/fake-openai/test", t, "image", jpeg, "t.jpg", "image/jpeg"));
    FAKE.respond(500, "{\"error\":{\"message\":\"boom\"}}");
    record(
        "admin.ai.test.fail",
        api.upload(base + "/fake-openai/test", t, "image", jpeg, "t.jpg", "image/jpeg"));
    record(
        "admin.ai.test.noModel",
        api.upload(base + "/openai/test", t, "image", jpeg, "t.jpg", "image/jpeg"));
    record(
        "admin.ai.test.notFound",
        api.upload(base + "/nope/test", t, "image", jpeg, "t.jpg", "image/jpeg"));
    record(
        "admin.ai.test.noImage",
        api.upload(base + "/fake-openai/test", t, "file", jpeg, "t.jpg", "image/jpeg"));

    FAKE.respond(200, "{\"data\":[]}");
    String key = "sk-snapshot-key-abcdefghijklmnop-WXYZ";
    record(
        "admin.ai.key.put",
        api.put(
            base + "/fake-openai/api-key",
            t,
            Map.of("apiKey", " " + key + " ", "currentPassword", ADMIN_PASSWORD)));
    record(
        "admin.ai.key.put.badPassword",
        api.put(
            base + "/fake-openai/api-key", t, Map.of("apiKey", key, "currentPassword", "wrong")));
    record(
        "admin.ai.key.put.badKey",
        api.put(
            base + "/fake-openai/api-key",
            t,
            Map.of("apiKey", "short", "currentPassword", ADMIN_PASSWORD)));
    FAKE.respond(401, "{\"error\":{\"message\":\"bad key " + key + "\"}}");
    record(
        "admin.ai.key.put.rejected",
        api.put(
            base + "/fake-openai/api-key",
            t,
            Map.of("apiKey", key + "2", "currentPassword", ADMIN_PASSWORD)));
    record(
        "admin.ai.key.put.noVerify",
        api.put(
            base + "/fake-openai/api-key",
            t,
            Map.of("apiKey", key + "3", "currentPassword", ADMIN_PASSWORD, "verify", false)));
    record(
        "admin.ai.key.put.notFound",
        api.put(base + "/nope/api-key", t, Map.of("apiKey", key, "currentPassword", "x")));
    record("admin.ai.providers.afterKey", api.get(base, t));
    record(
        "admin.ai.key.delete",
        api.exchange(
            HttpMethod.DELETE,
            base + "/fake-openai/api-key",
            t,
            Map.of("currentPassword", ADMIN_PASSWORD)));
    record(
        "admin.ai.key.delete.again",
        api.exchange(
            HttpMethod.DELETE,
            base + "/fake-openai/api-key",
            t,
            Map.of("currentPassword", ADMIN_PASSWORD)));
    record(
        "admin.ai.key.delete.badPassword",
        api.exchange(HttpMethod.DELETE, base + "/fake-openai/api-key", t, null));
    record("admin.ai.providers.delete", api.delete(base + "/fake-openai", t));
    record("admin.ai.providers.delete.again", api.delete(base + "/fake-openai", t));
  }

  private static String openAiResponse() {
    ObjectNode s = JSON.createObjectNode();
    s.put("name", "스냅샷 니트");
    s.put("category", "TOP");
    s.put("subType", "KNIT");
    s.put("color", "navy");
    s.put("pattern", "SOLID");
    s.putArray("seasons").add("FALL").add("FALL").add("WINTER");
    s.putArray("styles").add("NEAT");
    s.put("formality", 2);
    s.put("confidence", 0.876);
    ObjectNode r = JSON.createObjectNode();
    r.put("id", "resp_test");
    r.put("object", "response");
    r.put("status", "completed");
    r.put("model", "gpt-test-2026-01-01");
    r.putNull("error");
    r.putObject("usage").put("input_tokens", 1200).put("output_tokens", 345);
    ArrayNode output = r.putArray("output");
    output
        .addObject()
        .put("type", "message")
        .put("role", "assistant")
        .putArray("content")
        .addObject()
        .put("type", "output_text")
        .put("text", JSON.writeValueAsString(s));
    return JSON.writeValueAsString(r);
  }

  private void adminLogs(Session admin) {
    String t = admin.accessToken();
    Resp logs = api.get("/api/v1/admin/ai/logs", t);
    record("admin.ai.logs", logs);
    record(
        "admin.ai.logs.filtered",
        api.get(
            "/api/v1/admin/ai/logs?feature=OUTFIT_RECOMMEND&status=SUCCESS&from="
                + today
                + "&to="
                + today
                + "&userEmail={e}&size=2",
            t,
            Map.of("e", "SNAP-A")));
    record(
        "admin.ai.logs.invalid",
        api.get("/api/v1/admin/ai/logs?feature=CHAT&status=OK&from=yesterday", t));
    record("admin.ai.logs.badTo", api.get("/api/v1/admin/ai/logs?to=2026-99-01", t));
    for (JsonNode row : logs.body().get("items")) {
      String id = row.get("id").asString();
      record("admin.ai.log." + id, api.get("/api/v1/admin/ai/logs/" + id, t));
    }
    record("admin.ai.log.notFound", api.get("/api/v1/admin/ai/logs/999999", t));
    record("admin.audit", api.get("/api/v1/admin/audit-logs?size=100", t));
    record("admin.audit.code", api.get("/api/v1/admin/audit-logs?targetType=CODE&size=3", t));
    record("admin.audit.invalid", api.get("/api/v1/admin/audit-logs?targetType=ITEM", t));
    record("admin.dashboard.after", api.get("/api/v1/admin/dashboard", t));
  }

  private void errors(Session a) {
    record("error.unauthorized", api.get("/api/v1/users/me", null));
    record("error.badToken", api.get("/api/v1/users/me", "not-a-jwt"));
    record(
        "error.methodNotAllowed",
        api.exchange(HttpMethod.PUT, "/api/v1/codes", a.accessToken(), null));
    record("error.noResource", api.get("/api/v1/nothing-here", a.accessToken()));
    record(
        "error.unsupportedMedia",
        api.upload(
            "/api/v1/wardrobe/items", a.accessToken(), "image", new byte[] {1}, "x", "image/png"));
  }

  private void record(String name, Resp r) {
    if (recorded.containsKey(name)) {
      throw new IllegalStateException("같은 이름: " + name);
    }
    ObjectNode n = JSON.createObjectNode();
    n.put("status", r.status());
    n.set("body", r.body() == null ? null : normalize(r.body()));
    recorded.put(name, n);
  }

  private JsonNode normalize(JsonNode node) {
    if (node.isObject()) {
      ObjectNode out = JSON.createObjectNode();
      for (Map.Entry<String, JsonNode> e : node.properties()) {
        String k = e.getKey();
        JsonNode v = e.getValue();
        if ((k.equals("accessToken") || k.equals("refreshToken")) && v.isString()) {
          out.put(k, v.asString().isBlank() ? "<blank-token>" : "<token>");
        } else if ((k.equals("latencyMs") || k.equals("avgLatencyMs")) && v.isNumber()) {
          out.put(k, "<ms>");
        } else if (k.equals("fieldErrors") && v.isArray()) {
          out.set(k, sortedErrors(normalize(v)));
        } else {
          out.set(k, normalize(v));
        }
      }
      return out;
    }
    if (node.isArray()) {
      ArrayNode out = JSON.createArrayNode();
      node.forEach(v -> out.add(normalize(v)));
      return out;
    }
    if (node.isString()) {
      return JSON.getNodeFactory().stringNode(normalizeText(node.asString()));
    }
    return node;
  }

  /**
   * Bean Validation이 내는 필드 오류 순서는 검증 대상 객체의 해시에 따라 실행마다 달라질 수 있어(일반 클래스 요청 DTO) 필드·메시지 순으로 정렬해
   * 비교한다. 항목 수·필드·메시지는 그대로 비교한다.
   */
  private static JsonNode sortedErrors(JsonNode errors) {
    List<JsonNode> list = new ArrayList<>();
    errors.forEach(list::add);
    list.sort(
        java.util.Comparator.comparing((JsonNode e) -> e.path("field").asString(""))
            .thenComparing(e -> e.path("message").asString("")));
    ArrayNode out = JSON.createArrayNode();
    list.forEach(out::add);
    return out;
  }

  private String normalizeText(String s) {
    if (s == null) {
      return null;
    }
    if (INSTANT.matcher(s).matches()) {
      return "<instant>";
    }
    var version = VERSION.matcher(s);
    if (version.matches()) {
      return "<version>#" + version.group(1);
    }
    if (s.equals(today)) {
      return "<today>";
    }
    return s.replace(FAKE.url(), "<fake>");
  }

  private static void diff(String path, JsonNode expected, JsonNode actual, List<String> out) {
    if (out.size() > 50) {
      return;
    }
    boolean expectedNull = expected == null || expected.isNull();
    boolean actualNull = actual == null || actual.isNull();
    if (expectedNull || actualNull) {
      if (expectedNull != actualNull) {
        out.add(path + ": " + expected + " → " + actual);
      }
      return;
    }
    if (expected.isObject() && actual.isObject()) {
      for (Map.Entry<String, JsonNode> e : expected.properties()) {
        if (!actual.has(e.getKey())) {
          out.add(path + "." + e.getKey() + ": " + e.getValue() + " → (없음)");
        } else {
          diff(path + "." + e.getKey(), e.getValue(), actual.get(e.getKey()), out);
        }
      }
      for (Map.Entry<String, JsonNode> e : actual.properties()) {
        if (!expected.has(e.getKey())) {
          out.add(path + "." + e.getKey() + ": (없음) → " + e.getValue());
        }
      }
      return;
    }
    if (expected.isArray() && actual.isArray()) {
      if (expected.size() != actual.size()) {
        out.add(path + ": 배열 길이 " + expected.size() + " → " + actual.size());
      }
      for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
        diff(path + "[" + i + "]", expected.get(i), actual.get(i), out);
      }
      return;
    }
    if (!expected.equals(actual)) {
      out.add(path + ": " + expected + " → " + actual);
    }
  }
}
