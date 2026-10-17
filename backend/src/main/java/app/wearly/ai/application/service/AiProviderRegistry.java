package app.wearly.ai.application.service;

import app.wearly.ai.application.port.in.AiStatusUseCase;
import app.wearly.ai.application.port.out.AiModelFactoryPort;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.application.port.out.AiProviderPort;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderSelection;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiRunMode;
import app.wearly.ai.domain.AiSettings;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분석 때 쓸 공급자를 고른다 (07 문서 3장 "분석 때 공급자 고르기").
 * 1. 목업 실행(WEARLY_AI_PROVIDER=mock)이면 언제나 목업. DB 설정은 무시한다.
 * 2. 그 밖에는 enabled이고 모델이 있고 키가 있는(usable) 공급자를 priority 순으로 (AiProviderSelection).
 * 3. 쓸 수 있는 공급자가 없으면 빈 목록 → 분석은 502 AI_UNAVAILABLE (목업으로 대신하지 않는다).
 */
@Service
public class AiProviderRegistry implements AiStatusUseCase {

    public static final String MODE_MOCK = AiRunMode.MODE_MOCK;
    public static final String MODE_DB = AiRunMode.MODE_DB;

    private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

    private final AiProviderPort settings;
    private final AiModelFactoryPort factory;
    private final ApiKeyService keys;
    private final boolean mockMode;

    public AiProviderRegistry(AiProviderPort settings, AiModelFactoryPort factory, ApiKeyService keys,
            AiSettings aiSettings) {
        this.settings = settings;
        this.factory = factory;
        this.keys = keys;
        this.mockMode = aiSettings.mockMode();
    }

    /** WEARLY_AI_PROVIDER 해석 (AiRunMode.isMock). 그 밖의 값은 AiProviderConfigException */
    public static boolean parseMode(String raw) {
        return AiRunMode.isMock(raw);
    }

    public boolean isMockMode() {
        return mockMode;
    }

    public boolean keyConfigured(AiProviderSetting s) {
        return keys.configured(s);
    }

    /** 사용·모델·키 모두 갖춤 */
    public boolean usable(AiProviderSetting s) {
        return AiProviderSelection.usable(s, keyConfigured(s));
    }

    /** 이번 분석에서 시도할 공급자 (우선순위 순) */
    @Transactional(readOnly = true)
    public List<AiModelPort> candidates() {
        if (mockMode) {
            return List.of(factory.mock());
        }
        List<AiModelPort> out = new ArrayList<>();
        for (AiProviderSetting s : AiProviderSelection.candidates(settings.findAllOrdered(), this::keyConfigured)) {
            try {
                out.add(create(s));
            } catch (RuntimeException e) {
                log.warn("AI 공급자 {}를 만들 수 없어 건너뜀: {}", s.providerId(), e.getMessage());
            }
        }
        return out;
    }

    /** 관리자 시험 분석: 사용 여부와 관계없이 그 공급자 하나 */
    public AiModelPort forTest(AiProviderSetting s) {
        return create(s);
    }

    /**
     * @throws AiProviderException 모델이 없거나(MODEL_MISSING) 키가 없을 때(KEY_MISSING)
     */
    private AiModelPort create(AiProviderSetting s) {
        if (!s.hasModel()) {
            throw new AiProviderException("MODEL_MISSING", "모델이 설정되지 않았어요.");
        }
        String key = keys.find(s)
                .orElseThrow(() -> new AiProviderException("KEY_MISSING", s.apiKeyEnv() + " 환경변수가 비어 있어요."));
        return factory.create(s, key);
    }

    @Override
    @Transactional(readOnly = true)
    public void logStartupStatus() {
        if (mockMode) {
            log.info("AI 공급자: mock (목업 — 실제 AI를 호출하지 않음, DB 공급자 설정은 무시)");
            return;
        }
        List<String> usableList = settings.findAllOrdered().stream()
                .filter(this::usable)
                .map(s -> s.providerId() + "(" + s.type() + ", model=" + s.model() + ", priority=" + s.priority() + ")")
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
