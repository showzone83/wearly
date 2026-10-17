package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.ai.adapter.out.ai.ProviderOptions;
import app.wearly.ai.adapter.out.ai.claude.ClaudeModule;
import app.wearly.ai.adapter.out.ai.gemini.GeminiModule;
import app.wearly.ai.adapter.out.ai.openai.OpenAiModule;
import app.wearly.ai.domain.AiModelEntry;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.support.FakeHttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공급자별 모델 목록 조회 (실제 키·실제 API 없이). 로컬 가짜 서버에 고정 JSON 픽스처(src/test/resources/fixtures/models)를 걸어 요청
 * 모양(경로·인증 헤더)·응답 파싱·거르기·오류 처리를 본다. 픽스처 모양은 각 사 공식 API 명세(Anthropic GET /v1/models, OpenAI GET
 * /v1/models, Gemini GET /v1beta/models)를 따른다.
 */
class ModelListModulesTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final String KEY = "sk-test-model-list-key-0123456789";

  FakeHttpServer server;

  @BeforeEach
  void start() {
    server = new FakeHttpServer();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  ProviderOptions options(String baseUrl) {
    return new ProviderOptions(Map.of(), Duration.ofSeconds(5), baseUrl);
  }

  static String fixture(String name) {
    try (InputStream in =
        ModelListModulesTest.class.getResourceAsStream("/fixtures/models/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void Claude는_SDK로_v1_models를_부르고_폐기된_모델을_뺀다() {
    server.respond(200, fixture("anthropic.json"));
    List<AiModelEntry> models = new ClaudeModule(JSON).listModels(KEY, options(server.url()));

    assertThat(server.last().method()).isEqualTo("GET");
    assertThat(server.last().path()).isEqualTo("/v1/models");
    assertThat(server.last().query()).contains("limit=100");
    assertThat(server.last().header("x-api-key")).isEqualTo(KEY);
    assertThat(models)
        .extracting(AiModelEntry::id)
        .containsExactly("claude-sonnet-5-5", "claude-new-preview", "claude-opus-5-5");
    assertThat(models.get(0).displayName()).isEqualTo("Claude Sonnet 5.5");
    assertThat(models.get(0).createdAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    assertThat(models).noneMatch(AiModelEntry::recommended);
  }

  @Test
  void Claude_오류는_사유_코드로_바꾸고_키를_남기지_않는다() {
    server.respond(
        401,
        "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"bad "
            + KEY
            + "\"}}");
    ClaudeModule claude = new ClaudeModule(JSON);
    assertThatThrownBy(() -> claude.listModels(KEY, options(server.url())))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            e -> {
              assertThat(e.reason()).isEqualTo("API_401");
              assertThat(e.getMessage()).doesNotContain(KEY);
            });
    server.respond(
        500, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"x\"}}");
    assertThatThrownBy(() -> claude.listModels(KEY, options(server.url())))
        .isInstanceOfSatisfying(
            AiProviderException.class, e -> assertThat(e.reason()).isEqualTo("API_500"));
  }

  @Test
  void OpenAI는_v1_models에서_채팅_이미지_입력_계열만_남기고_created를_시각으로() {
    server.respond(200, fixture("openai.json"));
    List<AiModelEntry> models =
        new OpenAiModule(JSON).listModels(KEY, options(server.url() + "/v1"));

    assertThat(server.last().method()).isEqualTo("GET");
    assertThat(server.last().path()).isEqualTo("/v1/models");
    assertThat(server.last().header("Authorization")).isEqualTo("Bearer " + KEY);
    assertThat(models)
        .extracting(AiModelEntry::id)
        .containsExactly("gpt-5.1", "gpt-5.1-mini", "o4-mini", "o3", "gpt-4o");
    assertThat(models.get(0).displayName()).isEqualTo("gpt-5.1");
    assertThat(models.get(0).createdAt()).isEqualTo(Instant.ofEpochSecond(1760000000L));
  }

  @Test
  void OpenAI_오류와_형식_오류() {
    OpenAiModule openai = new OpenAiModule(JSON);
    server.respond(401, "{\"error\":{\"message\":\"Incorrect API key provided: " + KEY + "\"}}");
    assertThatThrownBy(() -> openai.listModels(KEY, options(server.url() + "/v1")))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            e -> {
              assertThat(e.reason()).isEqualTo("API_401");
              assertThat(e.getMessage()).doesNotContain(KEY);
            });
    server.respond(200, "<html>not json</html>");
    assertThatThrownBy(() -> openai.listModels(KEY, options(server.url() + "/v1")))
        .isInstanceOfSatisfying(
            AiProviderException.class, e -> assertThat(e.reason()).isEqualTo("BAD_RESPONSE"));
    server.respond(200, "{\"data\":{}}");
    assertThatThrownBy(() -> openai.listModels(KEY, options(server.url() + "/v1")))
        .isInstanceOfSatisfying(
            AiProviderException.class, e -> assertThat(e.reason()).isEqualTo("BAD_RESPONSE"));
    server.respond(200, "{\"object\":\"list\",\"data\":[]}");
    assertThat(openai.listModels(KEY, options(server.url() + "/v1"))).isEmpty();
  }

  @Test
  void Gemini는_v1beta_models에서_generateContent만_models_접두사를_떼고() {
    server.respond(200, fixture("gemini.json"));
    List<AiModelEntry> models = new GeminiModule(JSON).listModels(KEY, options(server.url()));

    assertThat(server.requests()).hasSize(1);
    assertThat(server.last().path()).isEqualTo("/v1beta/models");
    assertThat(server.last().query()).isEqualTo("pageSize=1000");
    assertThat(server.last().header("x-goog-api-key")).isEqualTo(KEY);
    assertThat(server.last().query()).doesNotContain(KEY); // 키는 쿼리에 넣지 않는다
    assertThat(models)
        .extracting(AiModelEntry::id)
        .containsExactly("gemini-2.5-flash", "gemini-2.5-pro", "gemini-flash-latest");
    assertThat(models.get(0).displayName()).isEqualTo("Gemini 2.5 Flash");
    assertThat(models.get(2).displayName()).isEqualTo("gemini-flash-latest");
    assertThat(models).allMatch(m -> m.createdAt() == null);
  }

  @Test
  void Gemini는_nextPageToken을_따라가되_같은_토큰이_되풀이되면_멈춘다() {
    server.respond(
        200,
        "{\"models\":[{\"name\":\"models/gemini-2.5-flash\",\"supportedGenerationMethods\":"
            + "[\"generateContent\"]}],\"nextPageToken\":\"tok+/=1\"}");
    List<AiModelEntry> models = new GeminiModule(JSON).listModels(KEY, options(server.url()));
    assertThat(server.requests()).hasSize(2);
    assertThat(server.requests().get(1).query()).isEqualTo("pageSize=1000&pageToken=tok%2B%2F%3D1");
    assertThat(models).extracting(AiModelEntry::id).containsOnly("gemini-2.5-flash");

    // 빈 목록이면 models 필드가 없을 수 있다
    server.clear();
    server.respond(200, "{}");
    assertThat(new GeminiModule(JSON).listModels(KEY, options(server.url()))).isEmpty();
    server.respond(403, "{\"error\":{\"message\":\"API key not valid\"}}");
    assertThatThrownBy(() -> new GeminiModule(JSON).listModels(KEY, options(server.url())))
        .isInstanceOfSatisfying(
            AiProviderException.class, e -> assertThat(e.reason()).isEqualTo("API_403"));
  }
}
