package app.wearly.ai;

/** 옷 사진 분석 공급자. 구현은 ClaudeAiProvider(기본)와 MockAiProvider(WEARLY_AI_PROVIDER=mock일 때만) 둘이다. */
public interface AiProvider {

    /** 응답·로그에 쓰는 공급자 이름: "mock" | "claude" */
    String name();

    /** 설정된 모델 ID. 목업은 null */
    String model();

    /**
     * 사진을 분석해 허용 목록 안의 값으로 제안을 만든다.
     *
     * @throws AiProviderException 공급자 오류, 거절, 허용 목록 밖 응답
     */
    AiResult analyze(AiImage image);
}
