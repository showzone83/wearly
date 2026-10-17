package app.wearly.ai.application.port.in;

import app.wearly.common.domain.PatchBody;
import java.util.List;

/** 관리자 AI 공급자 관리 (07 문서 3·4장). 변경 이력은 부르는 쪽(관리자 컨텍스트)이 남긴다 */
public interface AiProviderAdminUseCase {

    /** priority 순 */
    List<AiProviderInfo> list();

    /** 없으면 404 */
    AiProviderInfo get(String providerId);

    /** 검증 오류 400, 같은 ID가 있으면 409 PROVIDER_EXISTS */
    AiProviderInfo create(CreateProviderCommand command);

    /** 보낸 필드만 바꾼다. options를 보내면 options 전체를 바꾼다. enabled=true로 두려면 모델이 있어야 한다 */
    AiProviderInfo patch(String providerId, PatchBody body);

    /** 행만 지운다. 분석 기록(tb_ai_analysis_log.provider)은 남는다. @return 지우기 전 모습 */
    AiProviderInfo delete(String providerId);

    List<ProviderTypeInfo> types();

    /** WEARLY_AI_ALLOW_BASE_URL */
    boolean baseUrlAllowed();

    /** WEARLY_AI_PROVIDER=mock */
    boolean mockMode();
}
