package app.wearly.ai.application.port.in;

import app.wearly.ai.application.port.in.dto.AiModelListDto;

/**
 * 공급자 모델 목록 (관리자 화면 모델 드롭다운, 07 문서 4장). 키가 있으면 제공사 목록을 받아 공급자 ID + 키 지문으로 10분 캐시하고, 키가 없거나 실패하면 내장
 * 목록(wearly.ai.model-catalog)을 준다. 읽기라 변경 이력은 남기지 않는다.
 */
public interface AiModelListUseCase {

  /**
   * 공급자가 없으면 404. 제공사 호출 실패는 예외 대신 CATALOG + error로 돌려준다. 트랜잭션을 열지 않는다 (외부 HTTP 동안 DB 연결을 잡지 않게).
   *
   * @param refresh true면 캐시를 무시하고 다시 받는다
   */
  AiModelListDto models(String providerId, boolean refresh);
}
