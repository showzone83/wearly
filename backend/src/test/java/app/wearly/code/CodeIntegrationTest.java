package app.wearly.code;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/** 06 문서: GET /codes, 코드 기반 검증, 통계 키 */
public class CodeIntegrationTest extends IntegrationTestBase {

    @Test
    void codes는_공개이고_그룹과_코드를_순서대로_준다() {
        Resp r = api().get("/api/v1/codes", null).expect(200);
        JsonNode body = r.body();
        String version = body.get("version").asString();
        assertThat(version).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z#\\d+");
        List<String> groups = new ArrayList<>();
        body.get("groups").forEach(g -> groups.add(g.get("group").asString()));
        assertThat(groups).containsExactly("CATEGORY", "SUB_TYPE", "COLOR", "PATTERN", "SEASON", "STYLE", "ITEM_STATUS");

        JsonNode category = body.get("groups").get(0);
        assertThat(category.get("name").asString()).isEqualTo("종류");
        assertThat(category.has("description")).isFalse(); // 설명은 관리자 API에만
        JsonNode top = category.get("codes").get(0);
        assertThat(top.toString()).isEqualTo(
                "{\"code\":\"TOP\",\"label\":\"상의\",\"parent\":null,\"sortOrder\":10,\"active\":true,\"attrs\":{\"slot\":\"TOP\"}}");
        JsonNode acc = find(body, "CATEGORY", "ACC");
        assertThat(acc.get("attrs").has("slot")).isTrue();
        assertThat(acc.get("attrs").get("slot").isNull()).isTrue();

        JsonNode jeans = find(body, "SUB_TYPE", "JEANS");
        assertThat(jeans.get("parent").asString()).isEqualTo("BOTTOM");
        // JSONB라 키 순서는 보장하지 않는다
        assertThat(jeans.get("attrs").get("volume").asInt()).isZero();
        assertThat(jeans.get("attrs").get("warmth").asInt()).isEqualTo(1);
        assertThat(jeans.get("attrs").get("shape").asString()).isEqualTo("JEANS");
        assertThat(jeans.get("attrs").size()).isEqualTo(3);
        JsonNode denim = find(body, "COLOR", "denim");
        assertThat(denim.get("attrs").get("hex").asString()).isEqualTo("#4A6A8C");
        assertThat(denim.get("attrs").get("neutral").asBoolean()).isTrue();
        assertThat(denim.get("attrs").get("pairs").toString()).contains("\"white\"");
        assertThat(find(body, "ITEM_STATUS", "LAUNDRY").get("attrs").get("recommendable").asBoolean()).isFalse();

        assertThat(count(body, "CATEGORY")).isGreaterThanOrEqualTo(7);
        assertThat(count(body, "SUB_TYPE")).isGreaterThanOrEqualTo(52);
        assertThat(count(body, "COLOR")).isGreaterThanOrEqualTo(22);
        assertThat(count(body, "PATTERN")).isGreaterThanOrEqualTo(7);
    }

    @Test
    void ETag가_같으면_304() {
        Resp first = api().get("/api/v1/codes", null).expect(200);
        String etag = first.headers().getETag();
        assertThat(etag).isEqualTo("\"" + first.body().get("version").asString() + "\"");
        assertThat(first.headers().getCacheControl()).contains("no-cache");

        HttpHeaders h = new HttpHeaders();
        h.setIfNoneMatch(etag);
        Resp again = api().get("/api/v1/codes", null, h);
        assertThat(again.status()).isEqualTo(304);
        assertThat(again.body()).isNull();
        assertThat(again.headers().getETag()).isEqualTo(etag);

        HttpHeaders other = new HttpHeaders();
        other.setIfNoneMatch("\"old#1\"");
        api().get("/api/v1/codes", null, other).expect(200);

        // 만료·위조 토큰이 붙어 와도 공개 경로라 무시한다
        api().get("/api/v1/codes", "not-a-jwt").expect(200);
    }

    @Test
    void 새_코드로_아이템을_등록한다() {
        Session s = signup();
        Map<String, Object> dress = item("셔츠 원피스", "DRESS", "SHIRT_DRESS", "denim");
        dress.put("pattern", "FLORAL");
        dress.put("styles", List.of("FEMININE", "SPORTY"));
        Resp r = api().post("/api/v1/wardrobe/items", s.accessToken(), dress).expect(201);
        assertThat(r.body().get("category").asString()).isEqualTo("DRESS");
        assertThat(r.body().get("subType").asString()).isEqualTo("SHIRT_DRESS");
        assertThat(r.body().get("color").asString()).isEqualTo("denim");
        assertThat(r.body().get("pattern").asString()).isEqualTo("FLORAL");

        api().post("/api/v1/wardrobe/items", s.accessToken(), item("청바지", "BOTTOM", "JEANS", "denim")).expect(201);
        api().post("/api/v1/wardrobe/items/bulk", s.accessToken(), Map.of("items", List.of(
                item("핑크 니트", "TOP", "KNIT", "pink"), item("점프수트", "DRESS", "JUMPSUIT", "lavender")))).expect(201);

        JsonNode stats = api().get("/api/v1/wardrobe/stats", s.accessToken()).expect(200).body();
        assertThat(stats.get("byCategory").get("DRESS").asLong()).isEqualTo(2);
        assertThat(stats.get("byColor").get(0).toString()).isEqualTo("{\"color\":\"denim\",\"count\":2}");
        assertThat(api().get("/api/v1/wardrobe/items?category=DRESS&color=lavender", s.accessToken()).body()
                .get("items").size()).isEqualTo(1);
    }

    @Test
    void 모르는_코드와_짝이_틀린_값은_400() {
        Session s = signup();
        Map<String, Object> bad = item("이상한 옷", "HAT", "FEDORA", "fuchsia");
        bad.put("pattern", "PAISLEY");
        bad.put("status", "LOST");
        bad.put("seasons", List.of("SPRING", "MONSOON"));
        bad.put("styles", List.of("GOTHIC"));
        Resp r = api().post("/api/v1/wardrobe/items", s.accessToken(), bad).expectError(400, "VALIDATION_FAILED");
        assertThat(r.body().get("fieldErrors").findValuesAsString("field")).containsExactlyInAnyOrder(
                "category", "subType", "color", "pattern", "status", "seasons[1]", "styles[0]");

        Resp mismatch = api().post("/api/v1/wardrobe/items", s.accessToken(), item("원피스", "TOP", "DRESS", "navy"))
                .expectError(400, "VALIDATION_FAILED");
        assertThat(mismatch.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("subType");
        assertThat(mismatch.body().get("fieldErrors").get(0).get("message").asString()).contains("카테고리");

        // 코드는 대소문자를 구분한다 (색은 소문자)
        api().post("/api/v1/wardrobe/items", s.accessToken(), item("네이비", "TOP", "TEE", "NAVY"))
                .expectError(400, "VALIDATION_FAILED");
    }

    @Test
    void 비활성_코드는_새로_고를_수_없지만_저장된_값은_PATCH에서_그대로_둔다() {
        Session admin = admin();
        Session s = signup();
        String pattern = "ZT_" + suffix();
        String color = "zt_" + suffix().toLowerCase();
        api().post("/api/v1/admin/codes/PATTERN", admin.accessToken(), Map.of("code", pattern, "label", "시험 무늬"))
                .expect(201);
        api().post("/api/v1/admin/codes/COLOR", admin.accessToken(), Map.of("code", color, "label", "시험 색",
                "attrs", Map.of("hex", "#123456", "neutral", false, "light", 0.5))).expect(201);
        String id;
        try {
            Map<String, Object> in = item("시험 셔츠", "TOP", "SHIRT", color);
            in.put("pattern", pattern);
            id = api().post("/api/v1/wardrobe/items", s.accessToken(), in).expect(201).body().get("id").asString();
            api().put("/api/v1/users/me/profile", s.accessToken(), profile(List.of(color))).expect(200);

            api().patch("/api/v1/admin/codes/PATTERN/" + pattern, admin.accessToken(), Map.of("active", false)).expect(200);
            api().patch("/api/v1/admin/codes/COLOR/" + color, admin.accessToken(), Map.of("active", false)).expect(200);

            // 새로 고르면 400
            Resp r = api().post("/api/v1/wardrobe/items", s.accessToken(), in).expectError(400, "VALIDATION_FAILED");
            assertThat(r.body().get("fieldErrors").findValuesAsString("field")).containsExactlyInAnyOrder("color", "pattern");
            assertThat(r.body().get("fieldErrors").get(0).get("message").asString()).contains("고를 수 없는");

            // PATCH에서 안 바꾼 비활성 필드는 통과, 같은 값을 다시 보내도 통과
            Resp p = api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), Map.of("name", "이름만 바꿈")).expect(200);
            assertThat(p.body().get("pattern").asString()).isEqualTo(pattern);
            api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), Map.of("pattern", pattern, "color", color))
                    .expect(200);
            // 다른 값으로 바꿨다가 비활성 값으로 되돌리는 것은 새로 고르는 것
            api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), Map.of("pattern", "SOLID")).expect(200);
            api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), Map.of("pattern", pattern))
                    .expectError(400, "VALIDATION_FAILED");

            // 프로필: 저장돼 있던 비활성 색은 그대로 둘 수 있고, 다른 사람이 새로 고르면 400
            api().put("/api/v1/users/me/profile", s.accessToken(), profile(List.of(color, "navy"))).expect(200);
            Resp other = api().put("/api/v1/users/me/profile", signup().accessToken(), profile(List.of(color)))
                    .expectError(400, "VALIDATION_FAILED");
            assertThat(other.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("colors[0]");

            // 목록 필터는 저장된 값을 찾는 것이라 비활성 코드도 받는다
            api().get("/api/v1/wardrobe/items?color=" + color, s.accessToken()).expect(200);
            // 통계의 byColor에도 그대로 나온다
            assertThat(api().get("/api/v1/wardrobe/stats", s.accessToken()).body().get("byColor").toString())
                    .contains(color);
        } finally {
            jdbc().update("delete from tb_wardrobe_item where user_id = ?", Long.parseLong(s.userId()));
            jdbc().update("delete from tb_user_profile where user_id = ?", Long.parseLong(s.userId()));
            api().delete("/api/v1/admin/codes/PATTERN/" + pattern, admin.accessToken()).expect(204);
            api().delete("/api/v1/admin/codes/COLOR/" + color, admin.accessToken()).expect(204);
        }
    }

    @Test
    void 종류를_비활성화하면_하위_세부_종류도_못_고르고_통계에는_아이템이_있을_때만_나온다() {
        Session admin = admin();
        Session s = signup();
        String cat = "ZC_" + suffix();
        String sub = "ZS_" + suffix();
        api().post("/api/v1/admin/codes/CATEGORY", admin.accessToken(),
                Map.of("code", cat, "label", "시험 종류", "attrs", Map.of("slot", "TOP"))).expect(201);
        api().post("/api/v1/admin/codes/SUB_TYPE", admin.accessToken(), Map.of("code", sub, "label", "시험 세부", "parent", cat,
                "attrs", Map.of("volume", 0, "warmth", 1, "shape", "TEE"))).expect(201);
        try {
            api().post("/api/v1/wardrobe/items", s.accessToken(), item("시험", cat, sub, "navy")).expect(201);
            assertThat(api().get("/api/v1/wardrobe/stats", signup().accessToken()).body().get("byCategory").has(cat))
                    .isTrue(); // 활성 코드는 0이어도 키가 있다

            api().patch("/api/v1/admin/codes/CATEGORY/" + cat, admin.accessToken(), Map.of("active", false)).expect(200);
            Resp r = api().post("/api/v1/wardrobe/items", s.accessToken(), item("시험2", cat, sub, "navy"))
                    .expectError(400, "VALIDATION_FAILED");
            assertThat(r.body().get("fieldErrors").findValuesAsString("field")).containsExactlyInAnyOrder("category",
                    "subType");

            // 비활성 종류: 아이템이 있는 회원에게만 키가 있다 (마지막)
            JsonNode mine = api().get("/api/v1/wardrobe/stats", s.accessToken()).body().get("byCategory");
            assertThat(mine.get(cat).asLong()).isEqualTo(1);
            assertThat(api().get("/api/v1/wardrobe/stats", signup().accessToken()).body().get("byCategory").has(cat))
                    .isFalse();
        } finally {
            jdbc().update("delete from tb_wardrobe_item where user_id = ?", Long.parseLong(s.userId()));
            api().delete("/api/v1/admin/codes/SUB_TYPE/" + sub, admin.accessToken()).expect(204);
            api().delete("/api/v1/admin/codes/CATEGORY/" + cat, admin.accessToken()).expect(204);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    public static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public static JsonNode find(JsonNode body, String group, String code) {
        for (JsonNode g : body.get("groups")) {
            if (g.get("group").asString().equals(group)) {
                for (JsonNode c : g.get("codes")) {
                    if (c.get("code").asString().equals(code)) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    public static int count(JsonNode body, String group) {
        for (JsonNode g : body.get("groups")) {
            if (g.get("group").asString().equals(group)) {
                return g.get("codes").size();
            }
        }
        return 0;
    }

    public static Map<String, Object> item(String name, String category, String subType, String color) {
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

    public static Map<String, Object> profile(List<String> colors) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "프로필");
        body.put("dressCodes", List.of("CASUAL"));
        body.put("moods", List.of("NEAT"));
        body.put("colors", colors);
        body.put("workType", "WEEKDAY");
        body.put("workDays", List.of(1, 2));
        body.put("workRule", true);
        body.put("offRule", true);
        body.put("notify", Map.of("morning", true, "evening", true, "dMinus1", true));
        return body;
    }
}
