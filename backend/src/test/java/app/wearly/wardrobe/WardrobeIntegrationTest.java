package app.wearly.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.config.ClockConfig;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import app.wearly.support.TestItems;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

class WardrobeIntegrationTest extends IntegrationTestBase {

    static final LocalDate TODAY = LocalDate.now(ClockConfig.SEOUL);

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WardrobeQueryMapper mapper;

    @Test
    void 아이템_등록_조회_수정_삭제() {
        Session s = signup();
        Map<String, Object> input = item("네이비 니트", "TOP", "KNIT", "navy");
        input.put("brand", "유니클로");
        input.put("seasons", List.of("FALL", "WINTER", "FALL"));
        input.put("lastWornAt", "2026-10-01");
        input.put("wornCount", 4);

        Resp created = api().post("/api/v1/wardrobe/items", s.accessToken(), input).expect(201);
        JsonNode item = created.body();
        String id = item.get("id").asString();
        assertThat(item.get("id").isString()).isTrue();
        assertThat(item.get("name").asString()).isEqualTo("네이비 니트");
        assertThat(item.get("category").asString()).isEqualTo("TOP");
        assertThat(item.get("subType").asString()).isEqualTo("KNIT");
        assertThat(item.get("color").asString()).isEqualTo("navy");
        assertThat(item.get("seasons").toString()).isEqualTo("[\"FALL\",\"WINTER\"]"); // 중복 제거, 순서 유지
        assertThat(item.get("styles").toString()).isEqualTo("[\"NEAT\"]");
        assertThat(item.get("formality").asInt()).isEqualTo(2);
        assertThat(item.get("status").asString()).isEqualTo("OWNED");
        assertThat(item.get("brand").asString()).isEqualTo("유니클로");
        assertThat(item.get("wornCount").asInt()).isEqualTo(4);
        assertThat(item.get("lastWornAt").asString()).isEqualTo("2026-10-01");
        assertThat(item.get("createdAt").asString()).endsWith("Z");
        assertThat(item.has("imageUri")).isFalse();

        assertThat(api().get("/api/v1/wardrobe/items/" + id, s.accessToken()).expect(200).body()).isEqualTo(item);

        // 기본값: wornCount 0, lastWornAt null, brand null
        Resp minimal = api().post("/api/v1/wardrobe/items", s.accessToken(), item("로퍼", "SHOES", "LOAFER", "black"))
                .expect(201);
        assertThat(minimal.body().get("wornCount").asInt()).isZero();
        assertThat(minimal.body().get("lastWornAt").isNull()).isTrue();
        assertThat(minimal.body().get("brand").isNull()).isTrue();

        // PATCH: 보낸 필드만 바뀌고 null은 값을 비운다
        Resp patched = api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(),
                "{\"status\":\"LAUNDRY\",\"wornCount\":5,\"brand\":null,\"id\":\"999\"}").expect(200);
        assertThat(patched.body().get("status").asString()).isEqualTo("LAUNDRY");
        assertThat(patched.body().get("wornCount").asInt()).isEqualTo(5);
        assertThat(patched.body().get("brand").isNull()).isTrue();
        assertThat(patched.body().get("id").asString()).isEqualTo(id);
        assertThat(patched.body().get("name").asString()).isEqualTo("네이비 니트");
        assertThat(patched.body().get("createdAt").asString()).isEqualTo(item.get("createdAt").asString());

        // PATCH로 카테고리만 바꾸면 짝이 깨지므로 400, 둘 다 바꾸면 성공
        Resp mismatch = api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), Map.of("category", "BOTTOM"))
                .expectError(400, "VALIDATION_FAILED");
        assertThat(mismatch.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("subType");
        api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), Map.of("category", "BOTTOM", "subType", "WIDE"))
                .expect(200);
        api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), "{\"name\":null}").expectError(400, "VALIDATION_FAILED");
        api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), "{\"color\":\"rainbow\"}")
                .expectError(400, "VALIDATION_FAILED");
        api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), "{\"formality\":4}")
                .expectError(400, "VALIDATION_FAILED");

        api().delete("/api/v1/wardrobe/items/" + id, s.accessToken()).expect(204);
        api().get("/api/v1/wardrobe/items/" + id, s.accessToken()).expectError(404, "NOT_FOUND");
        api().delete("/api/v1/wardrobe/items/" + id, s.accessToken()).expectError(404, "NOT_FOUND");
    }

    @Test
    void 남의_아이템은_404() {
        Session owner = signup();
        Session other = signup();
        String id = api().post("/api/v1/wardrobe/items", owner.accessToken(), item("코트", "OUTER", "COAT", "camel"))
                .expect(201).body().get("id").asString();

        api().get("/api/v1/wardrobe/items/" + id, other.accessToken()).expectError(404, "NOT_FOUND");
        api().patch("/api/v1/wardrobe/items/" + id, other.accessToken(), Map.of("status", "REPAIR"))
                .expectError(404, "NOT_FOUND");
        api().delete("/api/v1/wardrobe/items/" + id, other.accessToken()).expectError(404, "NOT_FOUND");
        assertThat(api().get("/api/v1/wardrobe/items", other.accessToken()).body().get("items").size()).isZero();

        api().get("/api/v1/wardrobe/items/abc", owner.accessToken()).expectError(404, "NOT_FOUND");
        api().get("/api/v1/wardrobe/items/" + id, owner.accessToken()).expect(200);
    }

    @Test
    void 카테고리와_세부_종류_짝이_틀리면_400() {
        Session s = signup();
        Resp r = api().post("/api/v1/wardrobe/items", s.accessToken(), item("이상한 옷", "TOP", "COAT", "navy"))
                .expectError(400, "VALIDATION_FAILED");
        assertThat(r.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("subType");

        Map<String, Object> missing = item("이름", "TOP", "TEE", "white");
        missing.remove("status");
        missing.put("formality", 0);
        Resp m = api().post("/api/v1/wardrobe/items", s.accessToken(), missing).expectError(400, "VALIDATION_FAILED");
        assertThat(m.body().get("fieldErrors").findValuesAsString("field")).contains("status", "formality");

        Map<String, Object> badSeason = item("이름", "TOP", "TEE", "white");
        badSeason.put("seasons", List.of("AUTUMN"));
        Resp b = api().post("/api/v1/wardrobe/items", s.accessToken(), badSeason).expectError(400, "VALIDATION_FAILED");
        assertThat(b.body().get("fieldErrors").get(0).get("field").asString()).startsWith("seasons");
    }

    @Test
    void 필터와_정렬() {
        Session s = signup();
        String t = s.accessToken();
        Map<String, Object> a = item("화이트 셔츠", "TOP", "SHIRT", "white");
        a.put("wornCount", 10);
        a.put("lastWornAt", TODAY.minusDays(1).toString());
        Map<String, Object> b = item("블랙 슬랙스", "BOTTOM", "SLACKS", "black");
        b.put("brand", "COS");
        b.put("wornCount", 3);
        b.put("status", "LAUNDRY");
        Map<String, Object> c = item("100% 울 니트", "TOP", "KNIT", "navy");
        c.put("wornCount", 3);
        c.put("lastWornAt", TODAY.minusDays(5).toString());
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> m : List.of(a, b, c)) {
            ids.add(api().post("/api/v1/wardrobe/items", t, m).expect(201).body().get("id").asString());
        }

        assertThat(names(api().get("/api/v1/wardrobe/items", t))).containsExactly("100% 울 니트", "블랙 슬랙스", "화이트 셔츠");
        assertThat(names(api().get("/api/v1/wardrobe/items?sort=recent", t)))
                .containsExactly("100% 울 니트", "블랙 슬랙스", "화이트 셔츠");
        assertThat(names(api().get("/api/v1/wardrobe/items?sort=worn", t)))
                .containsExactly("화이트 셔츠", "100% 울 니트", "블랙 슬랙스"); // 같은 횟수면 최근 착용이 먼저
        assertThat(names(api().get("/api/v1/wardrobe/items?sort=name", t)))
                .containsExactly("100% 울 니트", "블랙 슬랙스", "화이트 셔츠");
        assertThat(names(api().get("/api/v1/wardrobe/items?category=TOP", t))).containsExactly("100% 울 니트", "화이트 셔츠");
        assertThat(names(api().get("/api/v1/wardrobe/items?status=LAUNDRY", t))).containsExactly("블랙 슬랙스");
        assertThat(names(api().get("/api/v1/wardrobe/items?color=navy", t))).containsExactly("100% 울 니트");
        assertThat(names(api().get("/api/v1/wardrobe/items?q=셔츠", t))).containsExactly("화이트 셔츠");
        assertThat(names(api().get("/api/v1/wardrobe/items?q=cos", t))).containsExactly("블랙 슬랙스"); // 브랜드, 대소문자 무시
        assertThat(names(api().get("/api/v1/wardrobe/items?q={q}", t, Map.of("q", "%")))).containsExactly("100% 울 니트"); // LIKE 이스케이프
        assertThat(names(api().get("/api/v1/wardrobe/items?q={q}", t, Map.of("q", "_")))).isEmpty();
        assertThat(names(api().get("/api/v1/wardrobe/items?category=TOP&color=white&status=OWNED", t)))
                .containsExactly("화이트 셔츠");
        assertThat(names(api().get("/api/v1/wardrobe/items?category=&q=", t))).hasSize(3);

        Resp bad = api().get("/api/v1/wardrobe/items?category=HAT&sort=price&color=rainbow&status=GONE", t)
                .expectError(400, "VALIDATION_FAILED");
        assertThat(bad.body().get("fieldErrors").findValuesAsString("field"))
                .containsExactlyInAnyOrder("category", "sort", "color", "status");

        // MyBatis 매퍼가 TEXT[]를 List로 읽는다
        List<ItemRow> rows = mapper.findItems(new ItemFilter(Long.parseLong(s.userId()), "TOP", null, null, null, "name"));
        assertThat(rows).extracting(ItemRow::getStyles).containsOnly(List.of("NEAT"));
        assertThat(rows).extracting(ItemRow::getSeasons).containsOnly(List.of("SPRING", "FALL"));
    }

    @Test
    void 통계() {
        Session s = signup();
        String t = s.accessToken();
        Resp empty = api().get("/api/v1/wardrobe/stats", t).expect(200);
        assertThat(empty.body().get("total").asLong()).isZero();
        assertThat(empty.body().get("utilization30d").asDouble()).isZero();
        // 활성 CATEGORY 코드 전부가 0으로 들어간다 (원피스 포함, 코드 순서)
        assertThat(empty.body().get("byCategory").toString())
                .startsWith("{\"TOP\":0,\"BOTTOM\":0,\"DRESS\":0,\"OUTER\":0,\"SHOES\":0,\"BAG\":0,\"ACC\":0");
        assertThat(empty.body().get("byStatus").toString()).isEqualTo("{\"OWNED\":0,\"LAUNDRY\":0,\"REPAIR\":0}");

        List<Map<String, Object>> inputs = List.of(
                withWorn(item("셔츠1", "TOP", "SHIRT", "white"), TODAY, List.of("NEAT", "MINIMAL")),
                withWorn(item("셔츠2", "TOP", "SHIRT", "white"), TODAY.minusDays(29), List.of("NEAT")),
                withWorn(item("슬랙스", "BOTTOM", "SLACKS", "navy"), TODAY.minusDays(30), List.of("CLASSIC")),
                withWorn(item("코트", "OUTER", "COAT", "camel"), TODAY.minusDays(60), List.of("CLASSIC")),
                withWorn(item("로퍼", "SHOES", "LOAFER", "black"), TODAY.minusDays(61), List.of("NEAT")),
                withWorn(item("시계", "ACC", "WATCH", "black"), null, List.of()));
        inputs.get(2).put("status", "REPAIR");
        inputs.get(3).put("status", "LAUNDRY");
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> m : inputs) {
            ids.add(api().post("/api/v1/wardrobe/items", t, m).expect(201).body().get("id").asString());
        }

        JsonNode st = api().get("/api/v1/wardrobe/stats", t).expect(200).body();
        assertThat(st.get("total").asLong()).isEqualTo(6);
        assertThat(st.get("byCategory").toString())
                .startsWith("{\"TOP\":2,\"BOTTOM\":1,\"DRESS\":0,\"OUTER\":1,\"SHOES\":1,\"BAG\":0,\"ACC\":1");
        assertThat(st.get("byColor").toString()).isEqualTo(
                "[{\"color\":\"black\",\"count\":2},{\"color\":\"white\",\"count\":2},"
                        + "{\"color\":\"camel\",\"count\":1},{\"color\":\"navy\",\"count\":1}]");
        assertThat(st.get("byStyle").toString()).isEqualTo(
                "[{\"style\":\"NEAT\",\"count\":3},{\"style\":\"CLASSIC\",\"count\":2},{\"style\":\"MINIMAL\",\"count\":1}]");
        assertThat(st.get("byStatus").toString()).isEqualTo("{\"OWNED\":4,\"LAUNDRY\":1,\"REPAIR\":1}");
        assertThat(st.get("wornLast30Days").asLong()).isEqualTo(2); // 오늘, 29일 전 (30일 전은 제외)
        assertThat(st.get("utilization30d").asDouble()).isEqualTo(0.33);
        // 한 번도 안 입은 것(시계) 먼저, 그다음 61일 전(로퍼). 60일 전(코트)은 아직 아니다
        List<String> sleeping = new ArrayList<>();
        st.get("sleeping").forEach(n -> sleeping.add(n.asString()));
        assertThat(sleeping).containsExactly(ids.get(5), ids.get(4));
    }

    @Test
    void 잠든_아이템은_최대_10개() {
        Session s = signup();
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(item("티 " + i, "TOP", "TEE", "gray"));
        }
        api().post("/api/v1/wardrobe/items/bulk", s.accessToken(), Map.of("items", many)).expect(201);
        JsonNode st = api().get("/api/v1/wardrobe/stats", s.accessToken()).expect(200).body();
        assertThat(st.get("sleeping").size()).isEqualTo(10);
        assertThat(st.get("sleeping").get(0).isString()).isTrue();
    }

    @Test
    void 일괄_등록은_요청_순서를_지키고_100개까지() {
        Session s = signup();
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            list.add(item("아이템 " + i, "BAG", i % 2 == 0 ? "TOTE" : "SHOULDER", "brown"));
        }
        Resp r = api().post("/api/v1/wardrobe/items/bulk", s.accessToken(), Map.of("items", list)).expect(201);
        assertThat(r.body().get("items").size()).isEqualTo(100);
        for (int i = 0; i < 100; i++) {
            assertThat(r.body().get("items").get(i).get("name").asString()).isEqualTo("아이템 " + i);
        }
        assertThat(api().get("/api/v1/wardrobe/items", s.accessToken()).body().get("items").size()).isEqualTo(100);

        List<Map<String, Object>> tooMany = new ArrayList<>(list);
        tooMany.add(item("101번째", "BAG", "TOTE", "brown"));
        api().post("/api/v1/wardrobe/items/bulk", s.accessToken(), Map.of("items", tooMany))
                .expectError(400, "VALIDATION_FAILED");
    }

    @Test
    void 일괄_등록에_틀린_항목이_있으면_하나도_저장하지_않는다() {
        Session s = signup();
        Resp r = api().post("/api/v1/wardrobe/items/bulk", s.accessToken(), Map.of("items", List.of(
                item("정상", "TOP", "TEE", "white"),
                item("짝 틀림", "SHOES", "TOTE", "white")))).expectError(400, "VALIDATION_FAILED");
        assertThat(r.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("items[1].subType");
        assertThat(api().get("/api/v1/wardrobe/items", s.accessToken()).body().get("items").size()).isZero();

        api().post("/api/v1/wardrobe/items/bulk", s.accessToken(), Map.of("items", List.of())).expect(201);
        api().post("/api/v1/wardrobe/items/bulk", s.accessToken(), Map.of()).expectError(400, "VALIDATION_FAILED");
    }

    static Map<String, Object> item(String name, String category, String subType, String color) {
        return TestItems.item(name, category, subType, color);
    }

    private static Map<String, Object> withWorn(Map<String, Object> m, LocalDate lastWorn, List<String> styles) {
        m.put("lastWornAt", lastWorn == null ? null : lastWorn.toString());
        m.put("styles", styles);
        return m;
    }

    private static List<String> names(Resp r) {
        r.expect(200);
        List<String> names = new ArrayList<>();
        r.body().get("items").forEach(n -> names.add(n.get("name").asString()));
        return names;
    }
}
