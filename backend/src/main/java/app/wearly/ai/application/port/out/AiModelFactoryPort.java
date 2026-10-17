package app.wearly.ai.application.port.out;

import app.wearly.ai.domain.AiProviderSetting;

/** 공급자 행 + 키로 공급자 구현(클라이언트)을 만든다. 같은 설정이면 만든 것을 다시 쓴다 */
public interface AiModelFactoryPort {

    /** @param apiKey 고른 키 (DB 또는 환경변수). 로그·예외에 남기지 않는다 */
    AiModelPort create(AiProviderSetting setting, String apiKey);

    /** 목업 공급자 (WEARLY_AI_PROVIDER=mock) */
    AiModelPort mock();
}
