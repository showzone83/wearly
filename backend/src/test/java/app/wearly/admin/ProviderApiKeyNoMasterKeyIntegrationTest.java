package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 07 문서 6장: WEARLY_SECRET_KEY가 없으면 웹 키 저장이 꺼진다 (기본 테스트 컨텍스트는 마스터 키 없음) */
class ProviderApiKeyNoMasterKeyIntegrationTest extends IntegrationTestBase {

  @Test
  void 마스터_키가_없으면_409_SECRET_KEY_MISSING이고_환경변수_키는_그대로() {
    String t = admin().accessToken();
    api()
        .put(
            "/api/v1/admin/ai/providers/claude/api-key",
            t,
            Map.of(
                "apiKey",
                "sk-ant-0123456789abcdefghij",
                "currentPassword",
                ADMIN_PASSWORD,
                "verify",
                false))
        .expectError(409, "SECRET_KEY_MISSING");
    // 마스터 키 확인이 비밀번호 확인보다 먼저다
    api()
        .put(
            "/api/v1/admin/ai/providers/claude/api-key",
            t,
            Map.of("apiKey", "x", "currentPassword", "wrong"))
        .expectError(409, "SECRET_KEY_MISSING");
    api()
        .put("/api/v1/admin/ai/providers/nope/api-key", t, Map.of("apiKey", "x"))
        .expectError(404, "NOT_FOUND");

    JsonNode list = api().get("/api/v1/admin/ai/providers", t).expect(200).body();
    for (JsonNode p : list) {
      assertThat(p.get("secretStoreEnabled").asBoolean()).isFalse();
      assertThat(p.get("keySource").asString()).isIn("ENV", "NONE");
      assertThat(p.get("keyConfigured").asBoolean())
          .isEqualTo(!p.get("keySource").asString().equals("NONE"));
    }
    assertThat(AdminAiProviderIntegrationTest.byId(list, "claude").get("keySource").asString())
        .isEqualTo("NONE");
  }
}
