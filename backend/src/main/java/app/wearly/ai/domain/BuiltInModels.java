package app.wearly.ai.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 공급자 종류별 내장 모델 목록 (설정 wearly.ai.model-catalog.&lt;종류&gt;). 키가 없거나 제공사 목록을 받지 못했을 때 보여 주고, 제공사
 * 목록에서도 여기 있는 ID에는 추천 표시를 그대로 붙인다. 없는 종류는 빈 목록 (화면에서 직접 입력).
 */
public record BuiltInModels(Map<AiProviderType, List<AiModelEntry>> byType) {

  public static final BuiltInModels EMPTY = new BuiltInModels(Map.of());

  public BuiltInModels {
    Map<AiProviderType, List<AiModelEntry>> copy = new EnumMap<>(AiProviderType.class);
    byType.forEach((k, v) -> copy.put(k, List.copyOf(v)));
    byType = Collections.unmodifiableMap(copy);
  }

  public List<AiModelEntry> of(AiProviderType type) {
    return byType.getOrDefault(type, List.of());
  }
}
