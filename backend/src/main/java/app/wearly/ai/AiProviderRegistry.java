package app.wearly.ai;

import app.wearly.config.WearlyProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분석 때 쓸 공급자를 고른다 (07 문서 3장 "분석 때 공급자 고르기").
 * 1. 목업 실행(WEARLY_AI_PROVIDER=mock)이면 언제나 목업. DB 설정은 무시한다.
 * 2. 그 밖에는 enabled이고 모델이 있고 키 환경변수에 값이 있는(usable) 공급자를 priority 순으로.
 * 3. 쓸 수 있는 공급자가 없으면 빈 목록 → 분석은 502 AI_UNAVAILABLE (목업으로 대신하지 않는다).
 */
@Component
public class AiProviderRegistry {

    public static final String MODE_MOCK = "mock";
    public static final String MODE_DB = "db";

    private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

    private final AiProviderSettingRepository settings;
    private final AiProviderFactory factory;
    private final ApiKeyResolver keys;
    private final boolean mockMode;
    private final MockAiProvider mock = new MockAiProvider();

    public AiProviderRegistry(AiProviderSettingRepository settings, AiProviderFactory factory, ApiKeyResolver keys,
            WearlyProperties properties) {
        this.settings = settings;
        this.factory = factory;
        this.keys = keys;
        this.mockMode = parseMode(properties.ai().provider());
    }

    /**
     * WEARLY_AI_PROVIDER 값: mock → 목업, db(기본)·claude(1차 기본값, 호환) → DB 공급자.
     * 그 밖의 값(예전 auto 포함)은 시작 실패.
     */
    static boolean parseMode(String raw) {
        String mode = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case MODE_MOCK -> true;
            case MODE_DB, "claude", "" -> false;
            case "auto" -> throw new AiProviderConfigException(
                    "WEARLY_AI_PROVIDER=auto는 더 이상 지원하지 않습니다. 기본값(db)을 쓰거나 "
                            + "목업은 WEARLY_AI_PROVIDER=mock 또는 ./gradlew bootRunMock으로 띄우세요.");
            default -> throw new AiProviderConfigException(
                    "WEARLY_AI_PROVIDER는 db(기본, 관리자 화면의 AI 공급자 설정) 또는 mock이어야 합니다: " + raw);
        };
    }

    public boolean isMockMode() {
        return mockMode;
    }

    public boolean keyConfigured(AiProviderSetting s) {
        return keys.configured(s.getApiKeyEnv());
    }

    /** 사용·모델·키 모두 갖춤 */
    public boolean usable(AiProviderSetting s) {
        return s.isEnabled() && s.hasModel() && keyConfigured(s);
    }

    /** 이번 분석에서 시도할 공급자 (우선순위 순) */
    @Transactional(readOnly = true)
    public List<AiProvider> candidates() {
        if (mockMode) {
            return List.of(mock);
        }
        List<AiProvider> out = new ArrayList<>();
        for (AiProviderSetting s : settings.findAllByOrderByPriorityAscProviderIdAsc()) {
            if (!usable(s)) {
                continue;
            }
            try {
                out.add(factory.create(s));
            } catch (RuntimeException e) {
                log.warn("AI 공급자 {}를 만들 수 없어 건너뜀: {}", s.getProviderId(), e.getMessage());
            }
        }
        return out;
    }

    /** 관리자 시험 분석: 사용 여부와 관계없이 그 공급자 하나 */
    public AiProvider forTest(AiProviderSetting s) {
        return factory.create(s);
    }

    /** 서버가 요청을 받기 시작할 때 공급자 상태를 남긴다. 쓸 수 있는 공급자가 없어도 시작은 한다 */
    @EventListener(ApplicationReadyEvent.class)
    public void logStartup() {
        if (mockMode) {
            log.info("AI 공급자: mock (목업 — 실제 AI를 호출하지 않음, DB 공급자 설정은 무시)");
            return;
        }
        List<String> usableList = settings.findAllByOrderByPriorityAscProviderIdAsc().stream()
                .filter(this::usable)
                .map(s -> s.getProviderId() + "(" + s.getType() + ", model=" + s.getModel() + ", priority=" + s.getPriority() + ")")
                .toList();
        if (usableList.isEmpty()) {
            log.warn("쓸 수 있는 AI 공급자가 없습니다. 사진 분석은 502 AI_UNAVAILABLE이 됩니다. "
                    + "관리자 화면에서 공급자를 켜고 모델을 넣거나, 키 환경변수(예: ANTHROPIC_API_KEY)를 backend/.env에 넣으세요. "
                    + "목업으로 띄우려면 ./gradlew bootRunMock");
        } else {
            log.info("AI 공급자 (시도 순서): {}", String.join(", ", usableList));
        }
    }
}
