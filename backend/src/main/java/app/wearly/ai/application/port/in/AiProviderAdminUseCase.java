package app.wearly.ai.application.port.in;

import app.wearly.ai.application.port.in.dto.AiProviderDto;
import app.wearly.ai.application.port.in.dto.CreateProviderDto;
import app.wearly.ai.application.port.in.dto.PatchProviderDto;
import app.wearly.ai.application.port.in.dto.ProviderCatalogDto;
import java.util.List;

/** 관리자 AI 공급자 관리 (07 문서 3·4장). 변경 이력은 부르는 쪽(관리자 컨텍스트)이 남긴다. 웹 입력 키는 AiProviderKeyUseCase. */
public interface AiProviderAdminUseCase {

  /** priority 순 */
  List<AiProviderDto> list();

  /** 없으면 404 */
  AiProviderDto get(String providerId);

  /** 검증 오류 400, 같은 ID가 있으면 409 PROVIDER_EXISTS */
  AiProviderDto create(CreateProviderDto command);

  /**
   * 보낸 필드만 바꾼다 (patch가 null이면 본문이 JSON 객체가 아닌 것, 400). options를 보내면 options 전체를 바꾼다. enabled=true로
   * 두려면 모델이 있어야 한다
   */
  AiProviderDto patch(String providerId, PatchProviderDto patch);

  /** 행만 지운다. 분석 기록(tb_ai_analysis_log.provider)은 남는다. @return 지우기 전 모습 */
  AiProviderDto delete(String providerId);

  /** 지원 공급자 종류와 서버 설정 (GET /admin/ai/types) */
  ProviderCatalogDto catalog();
}
