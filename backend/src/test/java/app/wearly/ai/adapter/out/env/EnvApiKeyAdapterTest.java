package app.wearly.ai.adapter.out.env;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 서버 설정 키 찾기 (08 문서 6장, Spring 컨텍스트 없이): wearly.ai.keys.<이름> → 같은 이름 속성·OS 환경변수 */
class EnvApiKeyAdapterTest {

  static final String NAME = "ANTHROPIC_API_KEY";

  @Test
  void yml의_wearly_ai_keys가_같은_이름_속성보다_먼저다() {
    MockEnvironment env =
        new MockEnvironment()
            .withProperty("wearly.ai.keys." + NAME, "  sk-from-yml  ")
            .withProperty(NAME, "sk-from-env");
    assertThat(new EnvApiKeyAdapter(env).resolve(NAME)).hasValue("sk-from-yml");
  }

  @Test
  void wearly_ai_keys가_없거나_비었으면_같은_이름_속성을_쓴다() {
    MockEnvironment onlyEnv = new MockEnvironment().withProperty(NAME, "sk-from-env");
    assertThat(new EnvApiKeyAdapter(onlyEnv).resolve(NAME)).hasValue("sk-from-env");

    MockEnvironment blankYml =
        new MockEnvironment()
            .withProperty("wearly.ai.keys." + NAME, " ")
            .withProperty(NAME, "sk-from-env");
    assertThat(new EnvApiKeyAdapter(blankYml).resolve(NAME)).hasValue("sk-from-env");
  }

  @Test
  void 둘_다_없거나_비었으면_빈_값() {
    assertThat(new EnvApiKeyAdapter(new MockEnvironment()).resolve(NAME)).isEmpty();
    MockEnvironment blank =
        new MockEnvironment().withProperty("wearly.ai.keys." + NAME, "").withProperty(NAME, "  ");
    assertThat(new EnvApiKeyAdapter(blank).resolve(NAME)).isEmpty();
  }

  @Test
  void API_KEY_형식이_아닌_이름은_찾지_않는다() {
    MockEnvironment env =
        new MockEnvironment()
            .withProperty("wearly.ai.keys.WEARLY_JWT_SECRET", "secret")
            .withProperty("WEARLY_JWT_SECRET", "secret")
            .withProperty("spring.datasource.password", "secret");
    EnvApiKeyAdapter adapter = new EnvApiKeyAdapter(env);
    assertThat(adapter.resolve("WEARLY_JWT_SECRET")).isEmpty();
    assertThat(adapter.resolve("spring.datasource.password")).isEmpty();
    assertThat(adapter.resolve(null)).isEmpty();
  }
}
