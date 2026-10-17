package app.wearly.ai;

import app.wearly.code.CodeSnapshot;

/**
 * 옷 사진 분석 공급자. 구현은 ClaudeAiProvider·OpenAiProvider·GeminiAiProvider(tb_ai_provider 행마다 하나)와
 * MockAiProvider(목업 실행일 때만)다.
 */
public interface AiProvider {

    /** 응답·로그에 쓰는 공급자 ID: tb_ai_provider.provider_id 또는 "mock" */
    String id();

    /** 설정된 모델 ID. 목업은 null */
    String model();

    /**
     * 사진을 분석해 활성 코드 안의 값으로 제안을 만든다.
     *
     * @param codes 허용 값 목록·스키마를 만들고 응답을 검증할 코드
     * @throws AiProviderException 공급자 오류, 시간 초과, 거절, 허용 목록 밖 응답
     */
    AiResult analyze(AiImage image, CodeSnapshot codes);
}
