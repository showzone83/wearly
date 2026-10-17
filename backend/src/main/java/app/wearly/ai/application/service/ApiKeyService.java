package app.wearly.ai.application.service;

import app.wearly.ai.application.port.out.ApiKeyEnvPort;
import app.wearly.ai.domain.AiProviderSetting;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 공급자가 쓸 키 고르기: api_key_env 환경변수 → 없음 */
@Service
public class ApiKeyService {

    private final ApiKeyEnvPort env;

    public ApiKeyService(ApiKeyEnvPort env) {
        this.env = env;
    }

    public Optional<String> find(AiProviderSetting s) {
        return env.resolve(s.apiKeyEnv());
    }

    public boolean configured(AiProviderSetting s) {
        return find(s).isPresent();
    }
}
