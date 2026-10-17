package app.wearly.ai.adapter.out.env;

import app.wearly.ai.application.port.out.ApiKeyEnvPort;
import app.wearly.ai.domain.AiProviderRules;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * tb_ai_provider.api_key_env가 가리키는 환경변수에서 키를 읽는다. 환경변수와 backend/.env 둘 다 Spring Environment로 읽힌다.
 * 다른 서버 비밀(DB 비밀번호, JWT 키 등)을 공급자에게 보내지 않도록 이름은 *_API_KEY 형식만 허용한다.
 */
@Component
class EnvApiKeyAdapter implements ApiKeyEnvPort {

  private final Environment environment;

  EnvApiKeyAdapter(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Optional<String> resolve(String envName) {
    if (!AiProviderRules.validEnvName(envName)) {
      return Optional.empty();
    }
    String value = environment.getProperty(envName);
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
  }
}
