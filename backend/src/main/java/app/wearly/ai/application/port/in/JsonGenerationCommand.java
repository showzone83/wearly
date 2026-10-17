package app.wearly.ai.application.port.in;

import java.util.Map;

/**
 * 구조화 JSON 생성 요청. 스키마·목업 응답은 JSON으로 바꿀 수 있는 자바 값(Map·List·String·Number·Boolean·null)으로 준다.
 *
 * @param feature 기록·한도에 쓰는 기능 (AiFeatures)
 * @param schemaName 구조화 출력 스키마 이름 (영문·숫자·밑줄)
 * @param mockOutput 목업 실행(WEARLY_AI_PROVIDER=mock)에서 목업 공급자가 돌려줄 결정적 응답
 */
public record JsonGenerationCommand(
    long userId,
    String feature,
    String schemaName,
    String instructions,
    String prompt,
    Map<String, Object> schema,
    Map<String, Object> mockOutput) {}
