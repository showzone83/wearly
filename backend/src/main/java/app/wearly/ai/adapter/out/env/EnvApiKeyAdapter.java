package app.wearly.ai.adapter.out.env;

import app.wearly.ai.application.port.out.ApiKeyEnvPort;
import app.wearly.ai.domain.AiProviderRules;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * tb_ai_provider.api_key_env의 키 이름(예: ANTHROPIC_API_KEY)으로 서버 설정에서 키를 찾는다 (08 문서 6장).
 *
 * <p>찾는 순서: 설정 {@code wearly.ai.keys.<키 이름>}(backend/config/application-local.yml 등) → 같은 이름의 속성이나
 * OS 환경변수. 둘 다 Spring Environment로 읽는다. 다른 서버 비밀(DB 비밀번호, JWT 키 등)을 공급자에게 보내지 않도록 이름은 *_API_KEY 형식만
 * 허용한다.
 */
@Component
class EnvApiKeyAdapter implements ApiKeyEnvPort {

  static final String KEYS_PREFIX = "wearly.ai.keys.";

  private final Environment environment;

  EnvApiKeyAdapter(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Optional<String> resolve(String envName) {
    if (!AiProviderRules.validEnvName(envName)) {
      return Optional.empty();
    }
    return nonBlank(environment.getProperty(KEYS_PREFIX + envName))
        .or(() -> nonBlank(environment.getProperty(envName)));
  }

  private static Optional<String> nonBlank(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
  }
}
