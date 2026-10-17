package app.wearly.code;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import app.wearly.support.TestItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** GET /codes 형식·ETag와 코드 기반 아이템·프로필 검증 (doc/06_코드_관리.md 4장) */
class CodesIntegrationTest extends IntegrationTestBase {

    @Autowired
    CodeCatalog catalog;

    @Test
    void codes는_공개이고_그룹_순서대로_비활성까지_내려준다() {
        Resp r = api().get("/api/v1/codes", null).expect(200);
        JsonNode body = r.body();
        assertThat(body.get("version").asString()).contains("#");
        assertThat(r.headers().getETag()).isEqualTo("\"" + body.get("version").asString() + "\"");
        assertThat(r.headers().getCacheControl()).contains("no-cache");

        List<String> groups = body.get("groups").valueStream().map(g -> g.get("group").asString()).toList();
        assertThat(groups).startsWith("CATEGORY", "SUB_TYPE", "COLOR", "PATTERN", "SEASON", "STYLE", "ITEM_STATUS");
        JsonNode category = body.get("groups").get(0);
        assertThat(category.get("name").asString()).isEqualTo("종류");
        assertThat(category.has("description")).isFalse(); // 관리자 응답에만
        JsonNode top = category.get("codes").get(0);
        assertThat(top.toString()).isEqualTo(
                "{\"code\":\"TOP\",\"label\":\"상의\",\"parent\":null,\"sortOrder\":10,\"active\":true,\"attrs\":{\"slot\":\"TOP\"}}");
        JsonNode acc = find(body, "CATEGORY", "ACC");
        assertThat(acc.get("attrs").has("slot")).isTrue();
        assertThat(acc.get("attrs").get("slot").isNull()).isTrue();

        assertThat(find(body, "SUB_TYPE", "JEANS").get("parent").asString()).isEqualTo("BOTTOM");
        JsonNode jeansAttrs = find(body, "SUB_TYPE", "JEANS").get("attrs");
        assertThat(jeansAttrs.get("volume").asInt()).isZero();
        assertThat(jeansAttrs.get("warmth").asInt()).isEqualTo(1);
        assertThat(jeansAttrs.get("shape").asString()).isEqualTo("JEANS");
        JsonNode navy = find(body, "COLOR", "navy");
        assertThat(navy.get("attrs").get("hex").asString()).isEqualTo("#22305A");
        assertThat(navy.get("attrs").get("neutral").asBoolean()).isTrue();
        assertThat(navy.get("attrs").get("pairs").size()).isEqualTo(13);
        assertThat(find(body, "ITEM_STATUS", "LAUNDRY").get("attrs").get("recommendable").asBoolean()).isFalse();
        assertThat(codesOf(body, "SUB_TYPE")).hasSizeGreaterThanOrEqualTo(52);
        assertThat(codesOf(body, "COLOR")).hasSizeGreaterThanOrEqualTo(22);
    }

    @Test
    void ETag가_같으면_304() {
        Resp first = api().get("/api/v1/codes", null).expect(200);
        String etag = first.headers().getETag();

        assertThat(conditional(etag)).isEqualTo(304);
        assertThat(conditional("W/" + etag)).isEqualTo(304);
        assertThat(conditional("\"other\", " + etag)).isEqualTo(304);
        assertThat(conditional("*")).isEqualTo(304);
        assertThat(conditional("\"stale#1\"")).isEqualTo(200);

        // 만료되거나 틀린 토큰을 붙여도 공개 경로라 무시한다
        api().get("/api/v1/codes", "not-a-token").expect(200);
    }

    @Test
    void 새_코드로_아이템을_등록한다() {
        Session s = signup();
        Map<String, Object> jeans = TestItems.item("청바지", "BOTTOM", "JEANS", "denim");
        jeans.put("pattern", "FLORAL");
        jeans.put("styles", List.of("FEMININE", "SPORTY"));
        Resp r = api().post("/api/v1/wardrobe/items", s.accessToken(), jeans).expect(201);
        assertThat(r.body().get("subType").asString()).isEqualTo("JEANS");
        assertThat(r.body().get("color").asString()).isEqualTo("denim");

        api().post("/api/v1/wardrobe/items", s.accessToken(), TestItems.item("원피스", "DRESS", "DRESS", "lavender"))
                .expect(201);
        api().post("/api/v1/wardrobe/items/bulk", s.accessToken(), Map.of("items", List.of(
                TestItems.item("점프수트", "DRESS", "JUMPSUIT", "mint"),
                TestItems.item("트렌치", "OUTER", "TRENCH", "beige")))).expect(201);

        JsonNode st = api().get("/api/v1/wardrobe/stats", s.accessToken()).expect(200).body();
        assertThat(st.get("byCategory").get("DRESS").asLong()).isEqualTo(2);
        assertThat(api().get("/api/v1/wardrobe/items?category=DRESS&color=mint", s.accessToken()).body()
                .get("items").size()).isEqualTo(1);
    }

    @Test
    void 모르는_코드와_parent_불일치는_400() {
        Session s = signup();
        Map<String, Object> bad = TestItems.item("이상한 옷", "TOP", "NOPE", "rainbow");
        bad.put("pattern", "PAISLEY");
        bad.put("status", "LOST");
        bad.put("seasons", List.of("SPRING", "MONSOON"));
        bad.put("styles", List.of("GOTHIC"));
        Resp r = api().post("/api/v1/wardrobe/items", s.accessToken(), bad).expectError(400, "VALIDATION_FAILED");
        assertThat(r.body().get("fieldErrors").findValuesAsString("field"))
                .containsExactlyInAnyOrder("subType", "color", "pattern", "status", "seasons[1]", "styles[0]");

        Resp mismatch = api().post("/api/v1/wardrobe/items", s.accessToken(),
                TestItems.item("짝 틀림", "TOP", "JEANS", "navy")).expectError(400, "VALIDATION_FAILED");
        assertThat(mismatch.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("subType");
        assertThat(mismatch.body().get("fieldErrors").get(0).get("message").asString()).contains("카테고리");

        Resp bulk = api().post("/api/v1/wardrobe/items/bulk", s.accessToken(), Map.of("items", List.of(
                TestItems.item("정상", "TOP", "TEE", "white"),
                TestItems.item("모르는 색", "TOP", "TEE", "rainbow")))).expectError(400, "VALIDATION_FAILED");
        assertThat(bulk.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("items[1].color");
        assertThat(api().get("/api/v1/wardrobe/items", s.accessToken()).body().get("items").size()).isZero();
    }

    @Test
    void 비활성_코드는_새로_고를_수_없지만_PATCH에서_안_바꾼_값은_통과한다() {
        Session admin = adminSession();
        String color = "tc" + suffix().toLowerCase();
        String style = "TS" + suffix();
        api().post("/api/v1/admin/codes/COLOR", admin.accessToken(), Map.of("code", color, "label", "시험색",
                "attrs", Map.of("hex", "#123456", "neutral", false, "light", 0.4))).expect(201);
        api().post("/api/v1/admin/codes/STYLE", admin.accessToken(), Map.of("code", style, "label", "시험 스타일",
                "attrs", Map.of())).expect(201);
        Session s = signup();
        try {
            Map<String, Object> input = TestItems.item("시험 옷", "TOP", "TEE", color);
            input.put("styles", List.of("NEAT", style));
            String id = api().post("/api/v1/wardrobe/items", s.accessToken(), input).expect(201).body().get("id").asString();
            Map<String, Object> profile = profile(List.of(style), List.of(color));
            api().put("/api/v1/users/me/profile", s.accessToken(), profile).expect(200);

            api().patch("/api/v1/admin/codes/COLOR/" + color, admin.accessToken(), Map.of("active", false)).expect(200);
            api().patch("/api/v1/admin/codes/STYLE/" + style, admin.accessToken(), Map.of("active", false)).expect(200);
            assertThat(find(api().get("/api/v1/codes", null).body(), "COLOR", color).get("active").asBoolean()).isFalse();

            // 바꾸지 않은 비활성 값은 그대로 두고 다른 필드를 바꿀 수 있다
            Resp patched = api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), Map.of("wornCount", 3)).expect(200);
            assertThat(patched.body().get("color").asString()).isEqualTo(color);
            assertThat(patched.body().get("styles").toString()).contains(style);
            api().get("/api/v1/wardrobe/items/" + id, s.accessToken()).expect(200);
            assertThat(api().get("/api/v1/wardrobe/items?color=" + color, s.accessToken()).body().get("items").size())
                    .isEqualTo(1); // 필터는 비활성 코드도 받는다

            // 새로 고르면 400
            Resp again = api().post("/api/v1/wardrobe/items", s.accessToken(), input).expectError(400, "VALIDATION_FAILED");
            assertThat(again.body().get("fieldErrors").findValuesAsString("field")).containsExactlyInAnyOrder("color",
                    "styles[1]");
            api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), Map.of("color", "navy")).expect(200);
            api().patch("/api/v1/wardrobe/items/" + id, s.accessToken(), Map.of("color", color))
                    .expectError(400, "VALIDATION_FAILED");

            // 프로필: 이미 저장된 비활성 값은 다시 저장해도 되고, 새로 고르면 400
            api().put("/api/v1/users/me/profile", s.accessToken(), profile).expect(200);
            Session other = signup();
            Resp p = api().put("/api/v1/users/me/profile", other.accessToken(), profile).expectError(400, "VALIDATION_FAILED");
            assertThat(p.body().get("fieldErrors").findValuesAsString("field")).containsExactlyInAnyOrder("moods[0]",
                    "colors[0]");

            // 통계: 아이템이 있는 비활성 코드는 키로 남는다 (byStatus는 활성 코드 전부)
            JsonNode st = api().get("/api/v1/wardrobe/stats", s.accessToken()).body();
            assertThat(st.get("byStatus").toString()).startsWith("{\"OWNED\":1,\"LAUNDRY\":0,\"REPAIR\":0");
        } finally {
            jdbcTemplate.update("delete from tb_wardrobe_item where user_id = ?", Long.parseLong(s.userId()));
            jdbcTemplate.update("delete from tb_user_profile where user_id = ?", Long.parseLong(s.userId()));
            api().delete("/api/v1/admin/codes/COLOR/" + color, admin.accessToken()).expect(204);
            api().delete("/api/v1/admin/codes/STYLE/" + style, admin.accessToken()).expect(204);
        }
    }

    private int conditional(String ifNoneMatch) {
        RestClient client = RestClient.builder().baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultStatusHandler(status -> true, (req, res) -> {
                })
                .build();
        return client.method(HttpMethod.GET).uri("/api/v1/codes").header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch)
                .retrieve().toBodilessEntity().getStatusCode().value();
    }

    static JsonNode find(JsonNode codes, String group, String code) {
        for (JsonNode g : codes.get("groups")) {
            if (g.get("group").asString().equals(group)) {
                for (JsonNode c : g.get("codes")) {
                    if (c.get("code").asString().equals(code)) {
                        return c;
                    }
                }
            }
        }
        throw new AssertionError("코드 없음: " + group + "/" + code);
    }

    static List<String> codesOf(JsonNode codes, String group) {
        for (JsonNode g : codes.get("groups")) {
            if (g.get("group").asString().equals(group)) {
                return g.get("codes").valueStream().map(c -> c.get("code").asString()).toList();
            }
        }
        return List.of();
    }

    static Map<String, Object> profile(List<String> moods, List<String> colors) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "코드검증");
        body.put("dressCodes", List.of("CASUAL"));
        body.put("moods", moods);
        body.put("colors", colors);
        body.put("workType", "WEEKDAY");
        body.put("workDays", List.of(1, 2));
        body.put("workRule", true);
        body.put("offRule", true);
        body.put("notify", Map.of("morning", true, "evening", true, "dMinus1", true));
        return body;
    }
}
