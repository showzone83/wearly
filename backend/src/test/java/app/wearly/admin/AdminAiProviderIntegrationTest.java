package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 07 문서 3·4장: AI 공급자 CRUD, keyConfigured, 검증, 변경 이력 (목업 실행 컨텍스트) */
class AdminAiProviderIntegrationTest extends IntegrationTestBase {

    @Test
    void 초기값_세_공급자와_종류_목록() {
        String t = admin().accessToken();
        JsonNode list = api().get("/api/v1/admin/ai/providers", t).expect(200).body();
        JsonNode claude = byId(list, "claude");
        assertThat(claude.get("type").asString()).isEqualTo("CLAUDE");
        assertThat(claude.get("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(claude.get("apiKeyEnv").asString()).isEqualTo("ANTHROPIC_API_KEY");
        assertThat(claude.get("keyConfigured").asBoolean()).isFalse(); // 테스트에서는 비워 둔다
        assertThat(claude.get("usable").asBoolean()).isFalse();
        assertThat(claude.get("options").get("effort").asString()).isEqualTo("medium");
        assertThat(claude.get("today").has("success")).isTrue();
        assertThat(byId(list, "openai").get("model").isNull()).isTrue();
        assertThat(byId(list, "openai").get("enabled").asBoolean()).isFalse();
        assertThat(byId(list, "gemini").get("apiKeyEnv").asString()).isEqualTo("GEMINI_API_KEY");

        JsonNode types = api().get("/api/v1/admin/ai/types", t).expect(200).body();
        assertThat(types.get("types").findValuesAsString("type")).containsExactly("CLAUDE", "OPENAI", "GEMINI");
        assertThat(types.get("types").get(0).get("options").findValuesAsString("name")).contains("effort", "maxTokens");
        assertThat(types.get("mockMode").asBoolean()).isTrue();
    }

    @Test
    void 추가_수정_삭제와_keyConfigured_usable() {
        String t = admin().accessToken();
        String id = "t-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> body = new HashMap<>();
        body.put("providerId", id);
        body.put("type", "OPENAI");
        body.put("displayName", "시험 OpenAI");
        body.put("apiKeyEnv", "TEST_CRUD_API_KEY");
        body.put("options", Map.of("temperature", 0.3, "detail", "low"));
        Resp created = api().post("/api/v1/admin/ai/providers", t, body).expect(201);
        assertThat(created.body().get("keyConfigured").asBoolean()).isTrue();
        assertThat(created.body().get("enabled").asBoolean()).isFalse();
        assertThat(created.body().get("usable").asBoolean()).isFalse();
        assertThat(created.body().get("priority").asInt()).isGreaterThanOrEqualTo(4);
        // 키 값은 어떤 응답에도 없다
        assertThat(created.body().toString()).doesNotContain("test-crud-key-value");
        api().post("/api/v1/admin/ai/providers", t, body).expectError(409, "PROVIDER_EXISTS");

        // 모델 없이 켜면 400, 모델을 넣고 켜면 usable
        api().patch("/api/v1/admin/ai/providers/" + id, t, Map.of("enabled", true)).expectError(400, "VALIDATION_FAILED");
        Resp on = api().patch("/api/v1/admin/ai/providers/" + id, t, Map.of("model", "gpt-test", "enabled", true,
                "priority", 50)).expect(200);
        assertThat(on.body().get("usable").asBoolean()).isTrue();
        assertThat(on.body().get("priority").asInt()).isEqualTo(50);
        assertThat(on.body().get("options").get("detail").asString()).isEqualTo("low"); // 안 보낸 필드는 그대로

        Resp noKey = api().patch("/api/v1/admin/ai/providers/" + id, t, Map.of("apiKeyEnv", "MISSING_TEST_API_KEY"))
                .expect(200);
        assertThat(noKey.body().get("keyConfigured").asBoolean()).isFalse();
        assertThat(noKey.body().get("usable").asBoolean()).isFalse();

        String listed = api().get("/api/v1/admin/ai/providers", t).body().toString();
        assertThat(listed).contains(id).doesNotContain("test-crud-key-value");

        api().delete("/api/v1/admin/ai/providers/" + id, t).expect(204);
        api().delete("/api/v1/admin/ai/providers/" + id, t).expectError(404, "NOT_FOUND");
        api().patch("/api/v1/admin/ai/providers/" + id, t, Map.of("enabled", false)).expectError(404, "NOT_FOUND");

        JsonNode audits = api().get("/api/v1/admin/audit-logs?targetType=AI_PROVIDER&size=50", t).body().get("items");
        List<String> actions = new java.util.ArrayList<>();
        audits.forEach(a -> {
            if (a.get("targetId").asString().equals(id)) {
                actions.add(a.get("action").asString());
            }
        });
        assertThat(actions).containsExactly("AI_PROVIDER_DELETE", "AI_PROVIDER_UPDATE", "AI_PROVIDER_UPDATE",
                "AI_PROVIDER_CREATE");
        assertThat(audits.toString()).doesNotContain("test-crud-key-value");
    }

    @Test
    void 입력_검증() {
        String t = admin().accessToken();
        Resp r = api().post("/api/v1/admin/ai/providers", t, Map.of("providerId", "mock", "type", "MOCK",
                "displayName", "", "apiKeyEnv", "SPRING_DATASOURCE_PASSWORD", "model", "bad model!")).expectError(400,
                "VALIDATION_FAILED");
        assertThat(r.body().get("fieldErrors").findValuesAsString("field")).contains("providerId", "type", "displayName",
                "apiKeyEnv", "model");

        Resp opt = api().post("/api/v1/admin/ai/providers", t, Map.of("providerId", "t-opt", "type", "CLAUDE",
                "displayName", "x", "apiKeyEnv", "TEST_CRUD_API_KEY", "enabled", true,
                "options", Map.of("effort", "turbo", "maxTokens", 0, "unknown", 1, "baseUrl", "ftp://x"))).expectError(400,
                "VALIDATION_FAILED");
        assertThat(opt.body().get("fieldErrors").findValuesAsString("field")).contains("model", "options.effort",
                "options.maxTokens", "options.unknown", "options.baseUrl");

        api().post("/api/v1/admin/ai/providers", t, Map.of("providerId", "Bad Id", "type", "GEMINI", "displayName", "x",
                "apiKeyEnv", "WEARLY_JWT_SECRET")).expectError(400, "VALIDATION_FAILED");
    }

    static JsonNode byId(JsonNode list, String id) {
        for (JsonNode p : list) {
            if (p.get("providerId").asString().equals(id)) {
                return p;
            }
        }
        return null;
    }
}
