package app.wearly.ai;

import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시작 로그 (doc/07_관리자_사이트.md 3장 5번).
 * - 목업 실행: INFO 한 줄.
 * - 그 밖: 쓸 수 있는 공급자 목록을 INFO로, 하나도 없으면 WARN (시작은 한다. 관리자가 화면에서 고칠 수 있게).
 * WEARLY_AI_PROVIDER 값이 틀리면 AiProviderRegistry를 만들 때 시작이 실패한다(AiProviderConfigException).
 */
@Configuration
public class AiProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(AiProviderConfig.class);

    static final String NONE_USABLE = "쓸 수 있는 AI 공급자가 없습니다. 사진 분석은 502 AI_UNAVAILABLE로 응답합니다. "
            + "관리자 화면(AI 공급자)에서 공급자를 켜고 모델을 넣거나, 키 환경변수(예: ANTHROPIC_API_KEY)를 backend/.env에 넣으세요. "
            + "목업으로 띄우려면 ./gradlew bootRunMock";

    @Bean
    ApplicationListener<ApplicationReadyEvent> aiProviderStartupLog(AiProviderRegistry registry) {
        return event -> {
            if (registry.mode() == AiMode.MOCK) {
                log.info("AI 공급자: mock (목업 실행 — DB 설정을 무시하고 실제 AI를 호출하지 않음)");
                return;
            }
            List<AiProviderSetting> usable = registry.usableSettings();
            if (usable.isEmpty()) {
                log.warn(NONE_USABLE);
            } else {
                log.info("AI 공급자(우선순위 순): {}", usable.stream()
                        .map(s -> s.getProviderId() + "(" + s.getType() + ", model=" + s.getModel() + ")")
                        .collect(Collectors.joining(", ")));
            }
        };
    }
}
