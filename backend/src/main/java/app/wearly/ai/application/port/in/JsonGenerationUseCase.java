package app.wearly.ai.application.port.in;

/**
 * 이미지 없는 구조화 JSON 생성 (09 문서 4장). 다른 컨텍스트(예: 코디 추천)가 프롬프트·스키마를 만들어 넘기고, 이 유스케이스는 공급자를 우선순위 순으로
 * 시도한다(07 문서 3장). 공급자 오류와 handler가 거부한 응답(남은 후보 0개 등)은 모두 실패로 기록하고 다음 공급자로 넘어간다. 성공한 호출만 그 기능의 일일
 * 한도에 센다.
 */
public interface JsonGenerationUseCase {

  /**
   * 공급자를 차례로 불러 handler가 받아들인 첫 결과를 돌려준다. 성공·실패를 tb_ai_analysis_log에 command.feature로 남긴다.
   *
   * @throws app.wearly.common.error.ApiException 쓸 수 있는 공급자가 없거나 모두 실패하면 502 AI_UNAVAILABLE
   */
  <T> GeneratedJson<T> generate(JsonGenerationCommand command, JsonOutputHandler<T> handler);

  /** 오늘(한국 시간) 그 회원이 그 기능으로 성공한 호출 수 */
  long successToday(long userId, String feature);
}
