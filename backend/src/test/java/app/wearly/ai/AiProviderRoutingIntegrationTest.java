package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.FakeHttpServer;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.ProviderReplies;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationListener;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.node.ObjectNode;

/**
 * DB 공급자 모드(WEARLY_AI_PROVIDER=db): tb_ai_provider를 우선순위 순으로 시도하고 실패하면 다음으로 넘어간다.
 * 세 공급자 모두 로컬 가짜 서버(Claude Messages·OpenAI Responses·Gemini generateContent)를 가리킨다.
 * 키는 테스트 속성으로 준 *_API_KEY 값이다 (실제 키 아님).
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "wearly.ai.provider=db",
        "wearly.ai.allow-base-url=true", // 가짜 서버 주소를 쓰려면 필요 (기본 false)
        "WEARLY_IT_CLAUDE_API_KEY=" + AiProviderRoutingIntegrationTest.CLAUDE_KEY,
        "WEARLY_IT_OPENAI_API_KEY=" + AiProviderRoutingIntegrationTest.OPENAI_KEY,
        "WEARLY_IT_GEMINI_API_KEY=" + AiProviderRoutingIntegrationTest.GEMINI_KEY,
})
class AiProviderRoutingIntegrationTest extends IntegrationTestBase {

    static final String CLAUDE_KEY = "sk-ant-it-claude-secret-0001";
    static final String OPENAI_KEY = "sk-it-openai-secret-0002";
    static final String GEMINI_KEY = "AIzaItGeminiSecret0003";
    static final String PATH = "/api/v1/wardrobe/items/analyze";

    static final FakeHttpServer SERVER = new FakeHttpServer();

    @Autowired
    AiAnalysisService service;

    @Autowired
    ApplicationListener<ApplicationReadyEvent> aiProviderStartupLog;

    @AfterAll
    static void stopServer() {
        SERVER.close();
    }

    @BeforeEach
    void clearProviders() {
        SERVER.clear();
        jdbcTemplate.update("delete from tb_ai_provider");
    }

    /** 다른 테스트 클래스를 위해 초기값 세 행을 되돌린다 */
    @AfterEach
    void restoreSeed() {
        jdbcTemplate.update("delete from tb_ai_provider");
        jdbcTemplate.update("""
                insert into tb_ai_provider (provider_id, type, display_name, model, api_key_env, enabled, priority, options) values
                ('claude', 'CLAUDE', 'Claude', 'claude-opus-5-5', 'ANTHROPIC_API_KEY', true, 1, '{"effort": "medium"}'),
                ('openai', 'OPENAI', 'OpenAI', null, 'OPENAI_API_KEY', false, 2, '{}'),
                ('gemini', 'GEMINI', 'Gemini', null, 'GEMINI_API_KEY', false, 3, '{}')""");
    }

    @Test
    void 우선순위_순으로_시도하고_실패하면_다음_공급자로() {
        insertClaude("claude-it", true, 1);
        insertOpenAi("openai-it", true, 2, "{}");
        insertGemini("gemini-it", true, 3);
        SERVER.reply("/v1/messages", 200, ProviderReplies.claude(ProviderReplies.suggestion().put("color", "rainbow"),
                "claude-opus-5-5")); // 허용 목록 밖 → 응답 검증 실패
        SERVER.reply("/openai/v1/responses", 500, "{\"error\":{\"message\":\"server error\"}}");
        SERVER.reply("/gemini/v1beta/models/", 200, ProviderReplies.gemini(
                ProviderReplies.JSON.writeValueAsString(ProviderReplies.suggestion()), "gemini-it-001"));

        Session s = signup();
        Resp r = api().upload(PATH, s.accessToken(), "image", photo(1), "a.jpg", "image/jpeg").expect(200);
        assertThat(r.body().get("provider").asString()).isEqualTo("gemini-it");
        assertThat(r.body().get("model").asString()).isEqualTo("gemini-it-001");
        assertThat(r.body().get("suggestion").get("subType").asString()).isEqualTo("JEANS");
        assertThat(r.body().get("remainingToday").asInt()).isEqualTo(2);

        assertThat(SERVER.requests()).extracting(FakeHttpServer.Request::path)
                .containsExactly("/v1/messages", "/openai/v1/responses", "/gemini/v1beta/models/gemini-test:generateContent");
        assertThat(SERVER.requests().get(0).header("x-api-key")).isEqualTo(CLAUDE_KEY);
        assertThat(SERVER.requests().get(1).header("Authorization")).isEqualTo("Bearer " + OPENAI_KEY);
        assertThat(SERVER.requests().get(2).header("x-goog-api-key")).isEqualTo(GEMINI_KEY);

        List<Map<String, Object>> logs = logs(s);
        assertThat(logs).extracting(m -> m.get("provider") + ":" + m.get("status") + ":" + m.get("error_code"))
                .containsExactly("claude-it:FAILED:INVALID_OUTPUT", "openai-it:FAILED:API_500", "gemini-it:SUCCESS:null");
        assertThat(logs.get(2).get("input_tokens")).isEqualTo(900);
        assertThat(logs.get(2).get("output_tokens")).isEqualTo(100);
        assertThat(service.usedToday(Long.parseLong(s.userId()))).isEqualTo(1); // 성공만 센다
    }

    @Test
    void 사용_안함_모델_없음_키_없음은_건너뛴다() {
        insertClaude("claude-off", false, 1);
        insert("openai-nokey", "OPENAI", "gpt-test", "WEARLY_IT_UNSET_API_KEY", true, 2,
                "{\"baseUrl\":\"" + SERVER.baseUrl() + "/openai/v1\"}");
        insert("gemini-nomodel", "GEMINI", null, "WEARLY_IT_GEMINI_API_KEY", false, 3, "{}");
        insertOpenAi("openai-ok", true, 9, "{\"temperature\":0.1}");
        SERVER.reply("/openai/v1/responses", 200, ProviderReplies.openai(
                ProviderReplies.JSON.writeValueAsString(ProviderReplies.suggestion()), "gpt-test-2026"));

        Session s = signup();
        Resp r = api().upload(PATH, s.accessToken(), "image", photo(2), "a.jpg", "image/jpeg").expect(200);
        assertThat(r.body().get("provider").asString()).isEqualTo("openai-ok");
        assertThat(r.body().get("model").asString()).isEqualTo("gpt-test-2026");
        assertThat(SERVER.requests()).hasSize(1);
        assertThat(logs(s)).hasSize(1);
    }

    @Test
    void 시간_초과면_다음_공급자로() {
        insertOpenAi("openai-slow", true, 1, "{\"timeoutSeconds\":1}");
        insertGemini("gemini-it", true, 2);
        SERVER.reply("/openai/v1/responses", 200, ProviderReplies.openai("{}", "gpt"), 2500);
        SERVER.reply("/gemini/v1beta/models/", 200, ProviderReplies.gemini(
                ProviderReplies.JSON.writeValueAsString(ProviderReplies.suggestion()), "gemini-it-001"));

        Session s = signup();
        api().upload(PATH, s.accessToken(), "image", photo(3), "a.jpg", "image/jpeg").expect(200);
        assertThat(logs(s)).extracting(m -> m.get("provider") + ":" + m.get("error_code"))
                .containsExactly("openai-slow:TIMEOUT", "gemini-it:null");
    }

    @Test
    void 전부_실패하면_502이고_각각_기록하며_한도에_세지_않는다() {
        insertOpenAi("openai-it", true, 1, "{}");
        insertGemini("gemini-it", true, 2);
        SERVER.reply("/openai/v1/responses", 503, "{}");
        SERVER.reply("/gemini/v1beta/models/", 429, "{\"error\":{\"code\":429,\"message\":\"quota\"}}");

        Session s = signup();
        api().upload(PATH, s.accessToken(), "image", photo(4), "a.jpg", "image/jpeg").expectError(502, "AI_UNAVAILABLE");
        assertThat(logs(s)).extracting(m -> m.get("provider") + ":" + m.get("error_code"))
                .containsExactly("openai-it:API_503", "gemini-it:API_429");
        assertThat(service.usedToday(Long.parseLong(s.userId()))).isZero();
    }

    @Test
    void 쓸_수_있는_공급자가_없으면_502이고_목업으로_대신하지_않는다(CapturedOutput output) {
        insert("openai-nokey", "OPENAI", "gpt-test", "WEARLY_IT_UNSET_API_KEY", true, 1, "{}");
        Session s = signup();
        Resp r = api().upload(PATH, s.accessToken(), "image", photo(5), "a.jpg", "image/jpeg")
                .expectError(502, "AI_UNAVAILABLE");
        assertThat(r.body().toString()).doesNotContain("mock");
        assertThat(logs(s)).isEmpty();

        // 시작 로그: 쓸 수 있는 공급자가 없으면 WARN (시작은 한다)
        aiProviderStartupLog.onApplicationEvent(null);
        assertThat(output.getOut()).contains("WARN").contains("쓸 수 있는 AI 공급자가 없습니다");

        insertGemini("gemini-it", true, 2);
        aiProviderStartupLog.onApplicationEvent(null);
        assertThat(output.getOut()).contains("AI 공급자(우선순위 순): gemini-it(GEMINI, model=gemini-test)");
    }

    @Test
    void 관리자_시험_분석은_사용_여부와_관계없이_호출하고_한도에_세지_않는다() {
        insertClaude("claude-admin", false, 1); // 사용 안 함이어도 시험은 된다
        SERVER.reply("/v1/messages", 200, ProviderReplies.claude(ProviderReplies.suggestion(), "claude-opus-5-5"));
        Session admin = adminSession();

        for (int i = 0; i < 5; i++) { // 테스트 한도 3회보다 많이
            Resp r = api().upload("/api/v1/admin/ai/providers/claude-admin/test", admin.accessToken(), "image", photo(10 + i),
                    "a.jpg", "image/jpeg").expect(200);
            assertThat(r.body().get("ok").asBoolean()).isTrue();
            assertThat(r.body().get("suggestion").get("color").asString()).isEqualTo("denim");
            assertThat(r.body().get("inputTokens").asLong()).isEqualTo(1500);
            assertThat(r.body().get("outputTokens").asLong()).isEqualTo(80);
            assertThat(r.body().get("model").asString()).isEqualTo("claude-opus-5-5");
            assertThat(r.body().get("latencyMs").asInt()).isGreaterThanOrEqualTo(0);
        }
        long adminId = Long.parseLong(admin.userId());
        assertThat(service.usedToday(adminId)).isZero();
        assertThat(logs(admin)).hasSize(5).allSatisfy(m -> assertThat(m.get("feature")).isEqualTo("ADMIN_TEST"));
        // 회원 분석은 여전히 3회 다 남아 있다 (사용 안 함이라 실제 분석은 502)
        api().upload(PATH, admin.accessToken(), "image", photo(99), "a.jpg", "image/jpeg").expectError(502, "AI_UNAVAILABLE");

        // 실패 결과도 같은 모양으로 준다
        SERVER.reply("/v1/messages", 400, "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\","
                + "\"message\":\"bad key " + CLAUDE_KEY + "\"}}");
        Resp failed = api().upload("/api/v1/admin/ai/providers/claude-admin/test", admin.accessToken(), "image", photo(20),
                "a.jpg", "image/jpeg").expect(200);
        assertThat(failed.body().get("ok").asBoolean()).isFalse();
        assertThat(failed.body().get("errorCode").asString()).isEqualTo("API_400");
        assertThat(failed.body().toString()).doesNotContain(CLAUDE_KEY).doesNotContain("sk-ant");

        // 목록: 키 값은 없고 keyConfigured만
        Resp list = api().get("/api/v1/admin/ai/providers", admin.accessToken()).expect(200);
        assertThat(list.body().get(0).get("keyConfigured").asBoolean()).isTrue();
        assertThat(list.body().get(0).get("usable").asBoolean()).isFalse();
        assertThat(list.body().get(0).get("today").get("success").asLong()).isEqualTo(5);
        assertThat(list.body().get(0).get("today").get("failed").asLong()).isEqualTo(1);
        assertThat(list.body().toString()).doesNotContain(CLAUDE_KEY).doesNotContain(OPENAI_KEY).doesNotContain(GEMINI_KEY);
    }

    // --- 공급자 행 준비 ---

    void insertClaude(String id, boolean enabled, int priority) {
        insert(id, "CLAUDE", "claude-opus-5-5", "WEARLY_IT_CLAUDE_API_KEY", enabled, priority,
                "{\"effort\":\"low\",\"maxTokens\":4000,\"baseUrl\":\"" + SERVER.baseUrl() + "\"}");
    }

    void insertOpenAi(String id, boolean enabled, int priority, String extraOptions) {
        ObjectNode o = (ObjectNode) ProviderReplies.JSON.readTree(extraOptions);
        o.put("baseUrl", SERVER.baseUrl() + "/openai/v1");
        insert(id, "OPENAI", "gpt-test", "WEARLY_IT_OPENAI_API_KEY", enabled, priority, o.toString());
    }

    void insertGemini(String id, boolean enabled, int priority) {
        insert(id, "GEMINI", "gemini-test", "WEARLY_IT_GEMINI_API_KEY", enabled, priority,
                "{\"baseUrl\":\"" + SERVER.baseUrl() + "/gemini/v1beta\"}");
    }

    void insert(String id, String type, String model, String keyEnv, boolean enabled, int priority, String options) {
        jdbcTemplate.update("insert into tb_ai_provider (provider_id, type, display_name, model, api_key_env, enabled, "
                + "priority, options) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb)", id, type, id, model, keyEnv, enabled,
                priority, options);
    }

    List<Map<String, Object>> logs(Session s) {
        return jdbcTemplate.queryForList("select provider, status, error_code, feature, input_tokens, output_tokens "
                + "from tb_ai_analysis_log where user_id = ? order by ai_analysis_log_id", Long.parseLong(s.userId()));
    }

    static byte[] photo(long seed) {
        byte[] b = new byte[64];
        new Random(seed).nextBytes(b);
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        return b;
    }
}
