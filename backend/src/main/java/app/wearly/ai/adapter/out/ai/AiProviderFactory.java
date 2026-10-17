package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.adapter.out.ai.mock.MockAiProvider;
import app.wearly.ai.application.port.out.AiModelFactoryPort;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.common.config.WearlyProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * tb_ai_provider 행 + 키로 공급자 구현을 만든다 (종류별 만들기는 AiProviderModule). 같은 설정이면 만든 클라이언트를 다시 쓴다 (행이 바뀌면
 * updated_at이, 키가 바뀌면 키 해시가 달라져 새로 만든다).
 */
@Component
public class AiProviderFactory implements AiModelFactoryPort {

  private final WearlyProperties.Ai props;
  private final AiProviderModules modules;
  private final MockAiProvider mock = new MockAiProvider();
  private final Map<String, AiModelPort> cache = new ConcurrentHashMap<>();

  public AiProviderFactory(WearlyProperties properties, AiProviderModules modules) {
    this.props = properties.ai();
    this.modules = modules;
  }

  @Override
  public AiModelPort mock() {
    return mock;
  }

  @Override
  public AiModelPort create(AiProviderSetting s, String key) {
    // 키 자체는 캐시 키에 넣지 않고 해시만 쓴다 (메모리 안에서만 쓰는 값)
    String cacheKey =
        String.join(
            "|",
            s.providerId(),
            s.type().name(),
            s.model(),
            s.apiKeyEnv(),
            String.valueOf(s.updatedAt()),
            Integer.toHexString(key.hashCode()),
            String.valueOf(s.options()));
    AiModelPort cached = cache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    synchronized (cache) {
      // 같은 공급자의 예전 설정으로 만든 것은 버린다
      cache.keySet().removeIf(old -> old.startsWith(s.providerId() + "|") && !old.equals(cacheKey));
      return cache.computeIfAbsent(
          cacheKey, k -> modules.of(s.type()).create(s, key, ProviderOptions.of(s, props)));
    }
  }
}
