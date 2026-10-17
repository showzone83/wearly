package app.wearly.ai.domain;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 구조화 JSON 생성 요청 (공급자와 무관). 사진 분석과 이미지 없는 생성(코디 추천)이 같은 모양을 쓴다. 프롬프트와 JSON 스키마는 호출하는 쪽이 만든다.
 *
 * @param schemaName 구조화 출력 스키마 이름 (OpenAI text.format.name). 영문·숫자·밑줄
 * @param instructions 시스템 지시 (Claude system, OpenAI instructions, Gemini systemInstruction). 없으면
 *     null
 * @param prompt 사용자 메시지 본문
 * @param schema JSON 스키마 (모든 객체 additionalProperties=false, 모든 필드 required)
 * @param mockOutput 목업 공급자가 그대로 돌려줄 결정적 응답. 목업 실행에서만 계산한다. 없으면 목업은 실패한다
 */
public record AiJsonPrompt(
    String schemaName,
    String instructions,
    String prompt,
    Map<String, Object> schema,
    Supplier<Map<String, Object>> mockOutput) {}
