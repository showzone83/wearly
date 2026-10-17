package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import app.wearly.support.TestItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 관리자 코드 관리: CRUD·순서·사용 중 삭제·pairs 양방향·캐시 갱신·변경 이력 (doc/07_관리자_사이트.md 4장) */
class AdminCodeIntegrationTest extends IntegrationTestBase {

    @Test
    void 관리자가_아니면_403_토큰이_없으면_401() {
        Session user = signup();
        for (String path : List.of("/api/v1/admin/codes", "/api/v1/admin/dashboard", "/api/v1/admin/users",
                "/api/v1/admin/ai/providers", "/api/v1/admin/ai/logs", "/api/v1/admin/audit-logs")) {
            api().get(path, user.accessToken()).expectError(403, "FORBIDDEN");
            api().get(path, null).expectError(401, "UNAUTHORIZED");
        }
        api().post("/api/v1/admin/codes/COLOR", user.accessToken(), Map.of("code", "x")).expectError(403, "FORBIDDEN");
        assertThat(api().get("/api/v1/users/me", user.accessToken()).body().get("role").asString()).isEqualTo("USER");
    }

    @Test
    void 관리자_코드_목록에는_description과_usageCount가_있다() {
        Session admin = adminSession();
        Session s = signup();
        api().post("/api/v1/wardrobe/items", s.accessToken(), TestItems.item("청바지", "BOTTOM", "JEANS", "denim"))
                .expect(201);
        JsonNode body = api().get("/api/v1/admin/codes", admin.accessToken()).expect(200).body();
        assertThat(body.get("version").asString()).isEqualTo(api().get("/api/v1/codes", null).body().get("version").asString());
        JsonNode color = group(body, "COLOR");
        assertThat(color.get("description").asString()).contains("hex");
        assertThat(code(color, "denim").get("usageCount").asLong()).isPositive();
        assertThat(code(group(body, "SEASON"), "SPRING").get("usageCount").asLong()).isPositive();
        assertThat(code(group(body, "SUB_TYPE"), "JEANS").has("usageCount")).isTrue();
    }

    @Test
    void 색_추가_수정_삭제와_pairs_양방향_반영_캐시_갱신() {
        Session admin = adminSession();
        String t = admin.accessToken();
        String c = "tc" + suffix().toLowerCase();
        String v0 = version();

        Resp created = api().post("/api/v1/admin/codes/COLOR", t, Map.of("code", c, "label", "시험 청록",
                "attrs", Map.of("hex", "#00aa88", "neutral", false, "light", 0.5, "pairs", List.of("white", "navy", "white"))))
                .expect(201);
        assertThat(created.body().get("attrs").get("hex").asString()).isEqualTo("#00AA88");
        assertThat(created.body().get("attrs").get("pairs").toString()).isEqualTo("[\"navy\",\"white\"]");
        assertThat(created.body().get("active").asBoolean()).isTrue();
        assertThat(created.body().get("sortOrder").asInt()).isGreaterThan(220); // 맨 뒤 +10

        String v1 = version();
        assertThat(v1).isNotEqualTo(v0);
        JsonNode codes = api().get("/api/v1/codes", null).body();
        assertThat(pairs(codes, "navy")).contains(c);
        assertThat(pairs(codes, "white")).contains(c);

        // pairs를 바꾸면 빠진 색에서도 빠진다
        api().patch("/api/v1/admin/codes/COLOR/" + c, t, Map.of("attrs",
                Map.of("hex", "#00AA88", "neutral", false, "light", 0.5, "pairs", List.of("black")))).expect(200);
        codes = api().get("/api/v1/codes", null).body();
        assertThat(pairs(codes, "navy")).doesNotContain(c);
        assertThat(pairs(codes, "white")).doesNotContain(c);
        assertThat(pairs(codes, "black")).contains(c);
        assertThat(version()).isNotEqualTo(v1);

        // 라벨만 바꾸기, 코드 문자열은 못 바꾼다
        api().patch("/api/v1/admin/codes/COLOR/" + c, t, Map.of("label", "시험 민트")).expect(200);
        api().patch("/api/v1/admin/codes/COLOR/" + c, t, Map.of("code", "other")).expectError(400, "VALIDATION_FAILED");
        assertThat(find(api().get("/api/v1/codes", null).body(), "COLOR", c).get("label").asString()).isEqualTo("시험 민트");

        // 쓰는 아이템이 있으면 409, 지우면 삭제되고 상대 pairs에서도 빠진다
        Session s = signup();
        String itemId = api().post("/api/v1/wardrobe/items", s.accessToken(), TestItems.item("옷", "TOP", "TEE", c))
                .expect(201).body().get("id").asString();
        Resp inUse = api().delete("/api/v1/admin/codes/COLOR/" + c, t).expectError(409, "CODE_IN_USE");
        assertThat(inUse.body().get("message").asString()).contains("아이템 1개");
        assertThat(code(group(api().get("/api/v1/admin/codes", t).body(), "COLOR"), c).get("usageCount").asLong()).isEqualTo(1);
        api().delete("/api/v1/wardrobe/items/" + itemId, s.accessToken()).expect(204);
        api().delete("/api/v1/admin/codes/COLOR/" + c, t).expect(204);
        codes = api().get("/api/v1/codes", null).body();
        assertThat(CodesHelper.codesOf(codes, "COLOR")).doesNotContain(c);
        assertThat(pairs(codes, "black")).doesNotContain(c);
        api().delete("/api/v1/admin/codes/COLOR/" + c, t).expectError(404, "NOT_FOUND");

        // 변경 이력: 생성·수정·pairs 반영·삭제가 before/after와 함께 남는다
        JsonNode audits = api().get("/api/v1/admin/audit-logs?targetType=CODE&size=100", t).expect(200).body();
        List<String> actions = new ArrayList<>();
        for (JsonNode a : audits.get("items")) {
            if (a.get("targetId").asString().equals("COLOR:" + c)) {
                actions.add(a.get("action").asString());
                assertThat(a.get("adminUserId").asString()).isEqualTo(admin.userId());
            }
        }
        assertThat(actions).contains("CODE_CREATE", "CODE_UPDATE", "CODE_DELETE");
        JsonNode create = audits.get("items").valueStream()
                .filter(a -> a.get("targetId").asString().equals("COLOR:" + c) && a.get("action").asString().equals("CODE_CREATE"))
                .findFirst().orElseThrow();
        assertThat(create.get("before").isNull()).isTrue();
        assertThat(create.get("after").get("label").asString()).isEqualTo("시험 청록");
        assertThat(audits.get("items").valueStream().anyMatch(a -> a.get("action").asString().equals("CODE_PAIRS_SYNC")
                && a.get("targetId").asString().equals("COLOR:navy"))).isTrue();
        assertThat(audits.get("page").asInt()).isZero();
        assertThat(audits.get("size").asInt()).isEqualTo(100);
        assertThat(audits.get("total").asLong()).isPositive();
    }

    @Test
    void 코드_검증_실패와_중복() {
        Session admin = adminSession();
        String t = admin.accessToken();
        api().post("/api/v1/admin/codes/COLOR", t, Map.of("code", "navy", "label", "또 네이비",
                "attrs", Map.of("hex", "#000000", "neutral", true, "light", 0.1))).expectError(409, "CODE_EXISTS");

        Resp bad = api().post("/api/v1/admin/codes/COLOR", t, Map.of("code", "Bad-Code", "label", "",
                "attrs", Map.of("hex", "blue", "neutral", "yes", "light", 2, "pairs", List.of("nope"), "extra", 1)))
                .expectError(400, "VALIDATION_FAILED");
        assertThat(bad.body().get("fieldErrors").findValuesAsString("field")).contains("code", "label", "attrs.hex",
                "attrs.neutral", "attrs.light", "attrs.pairs[0]", "attrs.extra");

        Resp sub = api().post("/api/v1/admin/codes/SUB_TYPE", t, Map.of("code", "TX" + suffix(), "label", "시험",
                "attrs", Map.of("volume", 2, "warmth", 4, "shape", ""))).expectError(400, "VALIDATION_FAILED");
        assertThat(sub.body().get("fieldErrors").findValuesAsString("field")).contains("parent", "attrs.volume",
                "attrs.warmth", "attrs.shape");
        api().post("/api/v1/admin/codes/SUB_TYPE", t, Map.of("code", "TX" + suffix(), "label", "시험", "parent", "HAT",
                "attrs", Map.of("volume", 0, "warmth", 0, "shape", "X"))).expectError(400, "VALIDATION_FAILED");
        api().post("/api/v1/admin/codes/PATTERN", t, Map.of("code", "TX" + suffix(), "label", "시험", "parent", "TOP",
                "attrs", Map.of())).expectError(400, "VALIDATION_FAILED");
        api().post("/api/v1/admin/codes/CATEGORY", t, Map.of("code", "TX" + suffix(), "label", "시험",
                "attrs", Map.of("slot", "HAT"))).expectError(400, "VALIDATION_FAILED");
        api().post("/api/v1/admin/codes/NOPE", t, Map.of("code", "X", "label", "x", "attrs", Map.of()))
                .expectError(404, "NOT_FOUND");
        api().patch("/api/v1/admin/codes/COLOR/navy", t, Map.of("attrs", Map.of("hex", "#zzzzzz")))
                .expectError(400, "VALIDATION_FAILED");
    }

    @Test
    void 종류를_비활성화하면_하위_세부_종류도_새로_고를_수_없다() {
        Session admin = adminSession();
        String t = admin.accessToken();
        String cat = "TC" + suffix();
        String sub = "TS" + suffix();
        api().post("/api/v1/admin/codes/CATEGORY", t, Map.of("code", cat, "label", "시험 종류", "attrs", Map.of("slot", "TOP")))
                .expect(201);
        api().post("/api/v1/admin/codes/SUB_TYPE", t, Map.of("code", sub, "label", "시험 세부", "parent", cat,
                "attrs", Map.of("volume", 1, "warmth", 2, "shape", "UNKNOWN_SHAPE"))).expect(201);
        Session s = signup();
        try {
            api().post("/api/v1/wardrobe/items", s.accessToken(), TestItems.item("시험", cat, sub, "navy"))
                    .expect(201);
            api().patch("/api/v1/admin/codes/CATEGORY/" + cat, t, Map.of("active", false)).expect(200);
            Resp r = api().post("/api/v1/wardrobe/items", s.accessToken(), TestItems.item("시험2", cat, sub, "navy"))
                    .expectError(400, "VALIDATION_FAILED");
            assertThat(r.body().get("fieldErrors").findValuesAsString("field")).containsExactlyInAnyOrder("category", "subType");
            // 하위 행은 그대로 활성
            assertThat(find(api().get("/api/v1/codes", null).body(), "SUB_TYPE", sub).get("active").asBoolean()).isTrue();
            // 하위 세부 종류가 있으면 종류는 못 지운다
            jdbcTemplate.update("delete from tb_wardrobe_item where user_id = ?", Long.parseLong(s.userId()));
            api().delete("/api/v1/admin/codes/CATEGORY/" + cat, t).expectError(409, "CODE_IN_USE");
        } finally {
            jdbcTemplate.update("delete from tb_wardrobe_item where user_id = ?", Long.parseLong(s.userId()));
            api().delete("/api/v1/admin/codes/SUB_TYPE/" + sub, t).expect(204);
            api().delete("/api/v1/admin/codes/CATEGORY/" + cat, t).expect(204);
        }
    }

    @Test
    void 순서_바꾸기() {
        Session admin = adminSession();
        String t = admin.accessToken();
        List<String> original = CodesHelper.codesOf(api().get("/api/v1/codes", null).body(), "SEASON");
        List<String> reversed = new ArrayList<>(original);
        java.util.Collections.reverse(reversed);
        try {
            Resp r = api().put("/api/v1/admin/codes/SEASON/order", t, Map.of("codes", reversed)).expect(200);
            assertThat(r.body().get(0).get("code").asString()).isEqualTo(reversed.get(0));
            assertThat(r.body().get(0).get("sortOrder").asInt()).isEqualTo(10);
            assertThat(r.body().get(1).get("sortOrder").asInt()).isEqualTo(20);
            assertThat(CodesHelper.codesOf(api().get("/api/v1/codes", null).body(), "SEASON")).containsExactlyElementsOf(reversed);

            api().put("/api/v1/admin/codes/SEASON/order", t, Map.of("codes", reversed.subList(0, 2)))
                    .expectError(400, "VALIDATION_FAILED");
            api().put("/api/v1/admin/codes/SEASON/order", t, Map.of("codes", List.of("SPRING", "SPRING", "FALL", "WINTER")))
                    .expectError(400, "VALIDATION_FAILED");
        } finally {
            api().put("/api/v1/admin/codes/SEASON/order", t, Map.of("codes", original)).expect(200);
        }
        assertThat(CodesHelper.codesOf(api().get("/api/v1/codes", null).body(), "SEASON")).containsExactlyElementsOf(original);
    }

    private String version() {
        return api().get("/api/v1/codes", null).body().get("version").asString();
    }

    private static List<String> pairs(JsonNode codes, String color) {
        return find(codes, "COLOR", color).get("attrs").get("pairs").valueStream().map(JsonNode::asString).toList();
    }

    static JsonNode find(JsonNode codes, String group, String code) {
        return code(group(codes, group), code);
    }

    static JsonNode group(JsonNode codes, String group) {
        return codes.get("groups").valueStream().filter(g -> g.get("group").asString().equals(group)).findFirst()
                .orElseThrow();
    }

    static JsonNode code(JsonNode group, String code) {
        return group.get("codes").valueStream().filter(c -> c.get("code").asString().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("코드 없음: " + code));
    }

    static final class CodesHelper {
        static List<String> codesOf(JsonNode codes, String g) {
            return group(codes, g).get("codes").valueStream().map(c -> c.get("code").asString()).toList();
        }
    }
}
