package app.wearly.ai.config;

import app.wearly.ai.domain.AiModelEntry;
import app.wearly.ai.domain.AiProviderRules;
import app.wearly.ai.domain.AiProviderType;
import app.wearly.ai.domain.AiRunMode;
import app.wearly.ai.domain.AiSettings;
import app.wearly.ai.domain.BuiltInModels;
import app.wearly.common.config.WearlyProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 환경변수(wearly.ai.*)를 ai 유스케이스가 쓰는 설정 값으로 바꾼다. 유스케이스는 Spring 설정 클래스를 직접 보지 않는다 */
@Configuration
public class AiConfig {

  /** WEARLY_AI_PROVIDER가 틀리면 여기서 시작이 멈춘다 (AiProviderFailureAnalyzer가 안내) */
  @Bean
  AiSettings aiSettings(WearlyProperties properties) {
    WearlyProperties.Ai ai = properties.ai();
    return new AiSettings(AiRunMode.isMock(ai.provider()), ai.dailyLimit(), ai.allowBaseUrl());
  }

  /**
   * wearly.ai.model-catalog.&lt;종류&gt; → 내장 모델 목록. 종류 이름이 틀리거나 모델 ID 형식이 틀리면(관리자 입력 규칙과 같다) 시작을
   * 멈춘다. 같은 ID가 두 번 있으면 처음 것만 쓴다.
   */
  @Bean
  BuiltInModels builtInModels(WearlyProperties properties) {
    Map<AiProviderType, List<AiModelEntry>> out = new EnumMap<>(AiProviderType.class);
    properties
        .ai()
        .modelCatalog()
        .forEach(
            (rawType, models) -> {
              AiProviderType type = type(rawType);
              List<AiModelEntry> list = new ArrayList<>();
              Set<String> seen = new HashSet<>();
              for (WearlyProperties.CatalogModel m :
                  models == null ? List.<WearlyProperties.CatalogModel>of() : models) {
                String id = m == null || m.id() == null ? "" : m.id().strip();
                if (!AiProviderRules.MODEL.matcher(id).matches()) {
                  throw new IllegalStateException(
                      "wearly.ai.model-catalog." + rawType + "의 모델 ID 형식이 틀렸어요: '" + id + "'");
                }
                if (seen.add(id)) {
                  list.add(new AiModelEntry(id, m.displayName(), null, m.recommended()));
                }
              }
              out.merge(
                  type,
                  list,
                  (a, b) -> {
                    List<AiModelEntry> merged = new ArrayList<>(a);
                    merged.addAll(b);
                    return merged;
                  });
            });
    return new BuiltInModels(out);
  }

  private static AiProviderType type(String raw) {
    String name = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
    return Arrays.stream(AiProviderType.values())
        .filter(t -> t.name().equals(name))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "wearly.ai.model-catalog의 공급자 종류 '"
                        + raw
                        + "'를 모르겠어요. "
                        + Arrays.toString(AiProviderType.values())
                        + " 중 하나로 적어 주세요."));
  }
}
