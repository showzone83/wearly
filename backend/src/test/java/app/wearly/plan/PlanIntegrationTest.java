package app.wearly.plan;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.code.CodeIntegrationTest;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import app.wearly.user.application.port.in.UserUseCase;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 예정 코디 서버 저장 (실제 HTTP): 등록·조회·수정·삭제, clientId로 다시 올리기, bulk, 남의 예정 404, 입력 검증, 회원당 상한, 지워진 아이템 표시,
 * 코드 사용처, 탈퇴 시 삭제.
 */
class PlanIntegrationTest extends IntegrationTestBase {

  static final String BASE = "/api/v1/plans";

  @Autowired JdbcTemplate jdbc;

  @Autowired UserUseCase users;

  static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Seoul"));
  }

  static Map<String, Object> weather(double temp) {
    Map<String, Object> w = new LinkedHashMap<>();
    w.put("temp", temp);
    w.put("min", temp - 5);
    w.put("max", temp + 4);
    w.put("rainProb", 20);
    w.put("cond", "CLOUDY");
    return w;
  }

  static Map<String, Object> plan(String clientId, LocalDate date) {
    Map<String, Object> m = new LinkedHashMap<>();
    if (clientId != null) {
      m.put("clientId", clientId);
    }
    m.put("date", date.toString());
    m.put("timeOfDay", "PM");
    m.put("title", "결혼식 하객");
    m.put("occasion", "WEDDING");
    m.put("concepts", List.of("MINIMAL", "CLASSIC"));
    m.put("weather", weather(18.5));
    return m;
  }

  static Map<String, Object> outfit(Map<String, Object> items) {
    Map<String, Object> o = new LinkedHashMap<>();
    o.put("id", "outfit-1");
    o.put("items", items);
    o.put("score", 87);
    o.put("reasons", List.of("색 조합이 좋아요"));
    o.put("context", Map.of("occasion", "WEDDING", "mood", "CHIC", "date", "2026-11-01"));
    return o;
  }

  @Test
  void 등록_조회_수정_삭제() {
    Session s = signup();
    String t = s.accessToken();
    LocalDate date = today().plusDays(7);
    Map<String, Object> body = plan(null, date);
    body.put("memo", "  신부 측  ");
    body.put("place", Map.of("regionCode", "BUSAN", "label", "부산", "lat", 35.18, "lon", 129.08));
    body.put("weatherSource", "CLIMATE");
    body.put("weatherCheckedAt", "2026-10-18T09:00:00+09:00");
    body.put("outfit", outfit(Map.of("TOP", "item-local")));

    JsonNode created = api().post(BASE, t, body).expect(201).body();
    String id = created.get("id").asString();
    assertThat(Long.parseLong(id)).isPositive();
    assertThat(created.get("clientId").isNull()).isTrue();
    assertThat(created.get("date").asString()).isEqualTo(date.toString());
    assertThat(created.get("timeOfDay").asString()).isEqualTo("PM");
    assertThat(created.get("title").asString()).isEqualTo("결혼식 하객");
    assertThat(created.get("occasion").asString()).isEqualTo("WEDDING");
    assertThat(created.get("concepts").toString()).isEqualTo("[\"MINIMAL\",\"CLASSIC\"]");
    assertThat(created.get("memo").asString()).isEqualTo("신부 측");
    assertThat(created.get("place").get("regionCode").asString()).isEqualTo("BUSAN");
    assertThat(created.get("place").get("lat").asDouble()).isEqualTo(35.18);
    assertThat(created.get("weather").get("temp").asDouble()).isEqualTo(18.5);
    assertThat(created.get("weather").get("rainProb").asDouble()).isEqualTo(20);
    assertThat(created.get("weather").get("manual").asBoolean()).isFalse();
    assertThat(created.get("weatherSource").asString()).isEqualTo("CLIMATE");
    assertThat(created.get("weatherCheckedAt").asString()).isEqualTo("2026-10-18T00:00:00Z");
    assertThat(created.get("forecastReady").asBoolean()).isFalse();
    assertThat(created.get("outfit").get("score").asInt()).isEqualTo(87);
    assertThat(created.get("outfit").get("context").get("mood").asString()).isEqualTo("CHIC");
    assertThat(created.get("missingItemIds").toString()).isEqualTo("[\"item-local\"]");
    assertThat(created.get("createdAt").asString()).endsWith("Z");
    assertThat(created.get("updatedAt").asString()).endsWith("Z");

    // 다른 날짜 예정 하나 더 → 목록은 날짜순, 기간으로 거른다
    Map<String, Object> early = plan(null, today().plusDays(2));
    early.put("timeOfDay", "EVENING");
    String earlyId = api().post(BASE, t, early).expect(201).body().get("id").asString();
    Map<String, Object> morning = plan(null, today().plusDays(2));
    morning.put("timeOfDay", "AM");
    String morningId = api().post(BASE, t, morning).expect(201).body().get("id").asString();
    JsonNode all = api().get(BASE, t).expect(200).body().get("items");
    assertThat(ids(all)).containsExactly(morningId, earlyId, id);
    JsonNode ranged =
        api()
            .get(BASE + "?from=" + today().plusDays(3) + "&to=" + date, t)
            .expect(200)
            .body()
            .get("items");
    assertThat(ids(ranged)).containsExactly(id);
    assertThat(api().get(BASE + "?to=" + today().plusDays(2), t).body().get("items").size())
        .isEqualTo(2);

    // PATCH: 보낸 필드만 바꾸고 null은 비운다
    Map<String, Object> patch = new HashMap<>();
    patch.put("memo", null);
    patch.put("place", null);
    patch.put("weather", Map.of("temp", 3, "min", -1, "max", 7, "rainProb", 70, "cond", "RAIN"));
    patch.put("weatherSource", "FORECAST");
    patch.put("forecastReady", true);
    patch.put("clientId", "ignored");
    JsonNode patched = api().patch(BASE + "/" + id, t, patch).expect(200).body();
    assertThat(patched.get("memo").isNull()).isTrue();
    assertThat(patched.get("place").isNull()).isTrue();
    assertThat(patched.get("weather").get("cond").asString()).isEqualTo("RAIN");
    assertThat(patched.get("weatherSource").asString()).isEqualTo("FORECAST");
    assertThat(patched.get("weatherCheckedAt").asString()).isEqualTo("2026-10-18T00:00:00Z");
    assertThat(patched.get("forecastReady").asBoolean()).isTrue();
    assertThat(patched.get("clientId").isNull()).as("clientId는 바꿀 수 없다").isTrue();
    assertThat(patched.get("title").asString()).isEqualTo("결혼식 하객");
    assertThat(patched.get("outfit").get("items").get("TOP").asString()).isEqualTo("item-local");
    // 코디 빼기
    Map<String, Object> noOutfit = new HashMap<>();
    noOutfit.put("outfit", null);
    JsonNode cleared = api().patch(BASE + "/" + id, t, noOutfit).expect(200).body();
    assertThat(cleared.get("outfit").isNull()).isTrue();
    assertThat(cleared.get("missingItemIds").size()).isZero();
    // 형식 오류
    Resp typeError =
        api()
            .patch(BASE + "/" + id, t, Map.of("weather", Map.of("temp", "hot")))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(typeError.body().get("fieldErrors").get(0).get("field").asString())
        .isEqualTo("weather");
    api().patch(BASE + "/" + id, t, Map.of("title", "")).expectError(400, "VALIDATION_FAILED");

    api().delete(BASE + "/" + id, t).expect(204);
    api().delete(BASE + "/" + id, t).expectError(404, "NOT_FOUND");
    api().patch(BASE + "/" + id, t, Map.of("title", "x")).expectError(404, "NOT_FOUND");
    api().patch(BASE + "/abc", t, Map.of("title", "x")).expectError(404, "NOT_FOUND");
    assertThat(ids(api().get(BASE, t).body().get("items"))).containsExactly(morningId, earlyId);
  }

  @Test
  void 같은_clientId로_다시_올리면_새로_만들지_않고_고친다() {
    Session s = signup();
    String t = s.accessToken();
    JsonNode first = api().post(BASE, t, plan("plan-mn1x2abc", today())).expect(201).body();
    Map<String, Object> again = plan("plan-mn1x2abc", today().plusDays(1));
    again.put("title", "바꾼 제목");
    JsonNode second = api().post(BASE, t, again).expect(200).body();

    assertThat(second.get("id").asString()).isEqualTo(first.get("id").asString());
    assertThat(second.get("clientId").asString()).isEqualTo("plan-mn1x2abc");
    assertThat(second.get("title").asString()).isEqualTo("바꾼 제목");
    assertThat(second.get("date").asString()).isEqualTo(today().plusDays(1).toString());
    assertThat(api().get(BASE, t).body().get("items").size()).isEqualTo(1);
    // 다른 회원은 같은 clientId를 따로 쓴다
    assertThat(api().post(BASE, signup().accessToken(), again).expect(201).body().get("id"))
        .isNotEqualTo(first.get("id"));
  }

  @Test
  void bulk는_요청_순서대로_만들거나_고치고_틀리면_하나도_저장하지_않는다() {
    Session s = signup();
    String t = s.accessToken();
    final String existingId =
        api().post(BASE, t, plan("sample-plan-1", today())).expect(201).body().get("id").asString();

    Map<String, Object> updated = plan("sample-plan-1", today().plusDays(24));
    updated.put("outfit", outfit(Map.of("TOP", "1", "BOTTOM", "2")));
    List<Map<String, Object>> items =
        List.of(
            plan("plan-a", today().plusDays(3)), updated, plan("plan-b", today().minusDays(30)));
    JsonNode res = api().post(BASE + "/bulk", t, Map.of("items", items)).expect(200).body();
    assertThat(res.get("created").asInt()).isEqualTo(2);
    assertThat(res.get("updated").asInt()).isEqualTo(1);
    JsonNode out = res.get("items");
    assertThat(out.size()).isEqualTo(3);
    assertThat(out.get(0).get("clientId").asString()).isEqualTo("plan-a");
    assertThat(out.get(1).get("id").asString()).isEqualTo(existingId);
    assertThat(out.get(1).get("date").asString()).isEqualTo(today().plusDays(24).toString());
    assertThat(out.get(1).get("missingItemIds").toString()).isEqualTo("[\"1\",\"2\"]");
    assertThat(out.get(2).get("date").asString()).isEqualTo(today().minusDays(30).toString());
    // 같은 걸 다시 올려도 늘지 않는다
    JsonNode again = api().post(BASE + "/bulk", t, Map.of("items", items)).expect(200).body();
    assertThat(again.get("created").asInt()).isZero();
    assertThat(again.get("updated").asInt()).isEqualTo(3);
    assertThat(count(s)).isEqualTo(3);

    // 요청 안의 clientId 중복, clientId 없음
    Resp dup =
        api()
            .post(
                BASE + "/bulk",
                t,
                Map.of("items", List.of(plan("plan-x", today()), plan("plan-x", today()))))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(fieldCodes(dup)).containsExactly("items[1].clientId:DUPLICATE");
    Resp missing =
        api()
            .post(BASE + "/bulk", t, Map.of("items", List.of(plan(null, today()))))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(fieldCodes(missing)).containsExactly("items[0].clientId:REQUIRED");
    // 하나라도 틀리면 아무것도 저장하지 않는다
    Map<String, Object> bad = plan("plan-bad", today());
    bad.put("occasion", "PARTY");
    Resp invalid =
        api()
            .post(BASE + "/bulk", t, Map.of("items", List.of(plan("plan-ok", today()), bad)))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(fieldCodes(invalid)).containsExactly("items[1].occasion:NOT_ALLOWED");
    assertThat(count(s)).isEqualTo(3);
    // 101개 → TOO_MANY, items 없음 → REQUIRED, 빈 목록은 200
    List<Map<String, Object>> many = new ArrayList<>();
    for (int i = 0; i < 101; i++) {
      many.add(plan("p-" + i, today()));
    }
    Resp tooMany =
        api().post(BASE + "/bulk", t, Map.of("items", many)).expectError(400, "VALIDATION_FAILED");
    assertThat(fieldCodes(tooMany)).containsExactly("items:TOO_MANY");
    assertThat(tooMany.body().get("fieldErrors").get(0).get("params").get("max").asInt())
        .isEqualTo(100);
    Resp none = api().post(BASE + "/bulk", t, Map.of()).expectError(400, "VALIDATION_FAILED");
    assertThat(fieldCodes(none)).containsExactly("items:REQUIRED");
    assertThat(
            api()
                .post(BASE + "/bulk", t, Map.of("items", List.of()))
                .expect(200)
                .body()
                .get("created")
                .asInt())
        .isZero();
  }

  @Test
  void 남의_예정은_404이고_목록에_안_보인다() {
    Session owner = signup();
    Session other = signup();
    String id =
        api()
            .post(BASE, owner.accessToken(), plan(null, today()))
            .expect(201)
            .body()
            .get("id")
            .asString();

    api()
        .patch(BASE + "/" + id, other.accessToken(), Map.of("title", ""))
        .expectError(404, "NOT_FOUND");
    api().delete(BASE + "/" + id, other.accessToken()).expectError(404, "NOT_FOUND");
    assertThat(api().get(BASE, other.accessToken()).body().get("items").size()).isZero();
    api().get(BASE, null).expectError(401, "UNAUTHORIZED");
    api().post(BASE, null, plan(null, today())).expectError(401, "UNAUTHORIZED");
    assertThat(api().get(BASE, owner.accessToken()).body().get("items").size()).isEqualTo(1);
  }

  @Test
  void 입력_검증() {
    String t = signup().accessToken();
    Map<String, Object> body = plan("bad id", today().plusYears(1).plusDays(1));
    body.put("timeOfDay", "NIGHT");
    body.put("title", "가".repeat(51));
    body.put("concepts", List.of("CLASSIC", "NOPE"));
    body.put("place", Map.of("regionCode", "NOWHERE", "label", "어딘가", "lat", 91, "lon", 10));
    body.put("weatherSource", "GUESS");
    body.put("outfit", outfit(Map.of("HAT", "1")));
    Resp r = api().post(BASE, t, body).expectError(400, "VALIDATION_FAILED");
    assertThat(fieldCodes(r))
        .containsExactly(
            "clientId:INVALID_FORMAT",
            "date:OUT_OF_RANGE",
            "timeOfDay:NOT_ALLOWED",
            "title:TOO_LONG",
            "concepts[1]:NOT_ALLOWED",
            "place.lat:OUT_OF_RANGE",
            "place.regionCode:NOT_ALLOWED",
            "weatherSource:NOT_ALLOWED",
            "outfit.items.HAT:NOT_ALLOWED");
    JsonNode date = r.body().get("fieldErrors").get(1).get("params");
    assertThat(date.get("min").asString()).isEqualTo(today().minusYears(1).toString());
    assertThat(date.get("max").asString()).isEqualTo(today().plusYears(1).toString());

    Map<String, Object> noWeather = plan(null, today());
    noWeather.remove("weather");
    noWeather.put("date", "2026/10/20");
    assertThat(fieldCodes(api().post(BASE, t, noWeather).expectError(400, "VALIDATION_FAILED")))
        .containsExactly("date:INVALID_FORMAT", "weather:REQUIRED");
    // 1년 전·1년 뒤는 된다
    api().post(BASE, t, plan(null, today().minusYears(1))).expect(201);
    api().post(BASE, t, plan(null, today().plusYears(1))).expect(201);

    // 목록 기간
    assertThat(
            fieldCodes(
                api().get(BASE + "?from=yesterday", t).expectError(400, "VALIDATION_FAILED")))
        .containsExactly("from:INVALID_FORMAT");
    assertThat(
            fieldCodes(
                api()
                    .get(BASE + "?from=2026-10-20&to=2026-10-19", t)
                    .expectError(400, "VALIDATION_FAILED")))
        .containsExactly("to:OUT_OF_RANGE");
  }

  @Test
  void 오래된_예정도_날짜를_바꾸지_않으면_고칠_수_있다() {
    Session s = signup();
    String id =
        api()
            .post(BASE, s.accessToken(), plan(null, today()))
            .expect(201)
            .body()
            .get("id")
            .asString();
    jdbc.update(
        "UPDATE tb_planned_outfit SET planned_date = ? WHERE planned_outfit_id = ?",
        today().minusYears(2),
        Long.parseLong(id));

    JsonNode patched =
        api().patch(BASE + "/" + id, s.accessToken(), Map.of("memo", "지난 예정")).expect(200).body();
    assertThat(patched.get("memo").asString()).isEqualTo("지난 예정");
    api()
        .patch(BASE + "/" + id, s.accessToken(), Map.of("date", today().minusYears(3).toString()))
        .expectError(400, "VALIDATION_FAILED");
  }

  @Test
  void 회원당_500개까지_저장한다() {
    Session s = signup();
    String t = s.accessToken();
    long userId = Long.parseLong(s.userId());
    final String id =
        api().post(BASE, t, plan("keep", today())).expect(201).body().get("id").asString();
    jdbc.update(
        "INSERT INTO tb_planned_outfit"
            + " (user_id, planned_date, time_of_day, title, occasion, weather)"
            + " SELECT ?, ?, 'AM', '채움', 'DAILY',"
            + " '{\"temp\":1,\"min\":0,\"max\":2,\"rainProb\":0,\"cond\":\"SUNNY\"}'::jsonb"
            + " FROM generate_series(1, 499)",
        userId,
        today());
    assertThat(count(s)).isEqualTo(500);

    Resp limit = api().post(BASE, t, plan(null, today())).expectError(409, "PLAN_LIMIT");
    assertThat(limit.body().get("params").get("max").asInt()).isEqualTo(500);
    api()
        .post(BASE + "/bulk", t, Map.of("items", List.of(plan("new-one", today()))))
        .expectError(409, "PLAN_LIMIT");
    // 고치기는 개수를 늘리지 않아 된다
    api().post(BASE, t, plan("keep", today().plusDays(1))).expect(200);
    api().post(BASE + "/bulk", t, Map.of("items", List.of(plan("keep", today())))).expect(200);
    api().delete(BASE + "/" + id, t).expect(204);
    api().post(BASE, t, plan(null, today())).expect(201);
  }

  @Test
  void 지워진_아이템은_missingItemIds로_표시하고_코디는_그대로_둔다() {
    Session s = signup();
    String t = s.accessToken();
    String top =
        api()
            .post(
                "/api/v1/wardrobe/items", t, CodeIntegrationTest.item("셔츠", "TOP", "SHIRT", "navy"))
            .expect(201)
            .body()
            .get("id")
            .asString();
    String bottom =
        api()
            .post(
                "/api/v1/wardrobe/items",
                t,
                CodeIntegrationTest.item("슬랙스", "BOTTOM", "SLACKS", "navy"))
            .expect(201)
            .body()
            .get("id")
            .asString();
    Map<String, Object> body = plan(null, today());
    body.put("outfit", outfit(Map.of("TOP", top, "BOTTOM", bottom)));
    String id = api().post(BASE, t, body).expect(201).body().get("id").asString();
    assertThat(api().get(BASE, t).body().get("items").get(0).get("missingItemIds").size()).isZero();

    // 사용 중지한 옷은 아직 있다, 완전 삭제하면 표시
    api()
        .post("/api/v1/wardrobe/items/" + top + "/retire", t, Map.of("reason", "DONATED"))
        .expect(200);
    api().delete("/api/v1/wardrobe/items/" + bottom, t).expect(204);
    JsonNode listed = api().get(BASE, t).body().get("items").get(0);
    assertThat(listed.get("id").asString()).isEqualTo(id);
    assertThat(listed.get("missingItemIds").toString()).isEqualTo("[\"" + bottom + "\"]");
    assertThat(listed.get("outfit").get("items").get("BOTTOM").asString()).isEqualTo(bottom);
    // 남의 아이템 ID도 내 옷장에 없는 것으로 본다
    String othersItem =
        api()
            .post(
                "/api/v1/wardrobe/items",
                signup().accessToken(),
                CodeIntegrationTest.item("남의 옷", "TOP", "SHIRT", "navy"))
            .expect(201)
            .body()
            .get("id")
            .asString();
    JsonNode patched =
        api()
            .patch(
                BASE + "/" + id,
                t,
                Map.of("outfit", outfit(Map.of("TOP", top, "SHOES", othersItem))))
            .expect(200)
            .body();
    assertThat(patched.get("missingItemIds").toString()).isEqualTo("[\"" + othersItem + "\"]");
  }

  @Test
  void 예정이_쓰는_STYLE_코드는_지울_수_없다() {
    Session admin = admin();
    String code = "ZP_" + CodeIntegrationTest.suffix();
    api()
        .post(
            "/api/v1/admin/codes/STYLE",
            admin.accessToken(),
            Map.of("code", code, "label", "예정 전용"))
        .expect(201);
    Session s = signup();
    Map<String, Object> body = plan(null, today());
    body.put("concepts", List.of(code));
    String id = api().post(BASE, s.accessToken(), body).expect(201).body().get("id").asString();

    Resp del =
        api()
            .delete("/api/v1/admin/codes/STYLE/" + code, admin.accessToken())
            .expectError(409, "CODE_IN_USE");
    assertThat(del.body().get("params").get("usages").toString())
        .isEqualTo("[{\"kind\":\"PLAN\",\"count\":1}]");
    api().delete(BASE + "/" + id, s.accessToken()).expect(204);
    api().delete("/api/v1/admin/codes/STYLE/" + code, admin.accessToken()).expect(204);
  }

  @Test
  void 탈퇴하면_예정도_지운다() {
    Session s = signup();
    api().post(BASE, s.accessToken(), plan("plan-1", today())).expect(201);
    api().post(BASE, s.accessToken(), plan("plan-2", today())).expect(201);
    assertThat(count(s)).isEqualTo(2);

    // 탈퇴 유예 기간이 지나 회원을 지운다 (doc/27, 예약 작업이 부르는 것과 같다)
    users.delete(Long.parseLong(s.userId()));
    assertThat(count(s)).isZero();
  }

  private long count(Session s) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM tb_planned_outfit WHERE user_id = ?",
        Long.class,
        Long.parseLong(s.userId()));
  }

  private static List<String> ids(JsonNode items) {
    List<String> out = new ArrayList<>();
    items.forEach(i -> out.add(i.get("id").asString()));
    return out;
  }

  private static List<String> fieldCodes(Resp r) {
    List<String> out = new ArrayList<>();
    r.body()
        .get("fieldErrors")
        .forEach(e -> out.add(e.get("field").asString() + ":" + e.get("code").asString()));
    return out;
  }
}
