package app.wearly.ai.application.port.in;

import app.wearly.common.domain.PatchBody;
import java.util.List;
import java.util.Optional;

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

    /** 마스터 키(WEARLY_SECRET_KEY)가 없으면 409 SECRET_KEY_MISSING */
    void requireSecretStore();

    /**
     * 웹 키 넣기·바꾸기 (쓰기 전용). 공급자가 없으면 404, 마스터 키가 없으면 409 SECRET_KEY_MISSING,
     * 형식이 틀리면 400 VALIDATION_FAILED(apiKey), verify면 모델 목록 조회로 확인해 실패하면 400 API_KEY_INVALID.
     *
     * @param adminUserId 넣은 관리자 (updated_by)
     */
    KeyChange setKey(String providerId, String apiKey, boolean verify, long adminUserId);

    /** 웹 키 지우기 (환경변수 키로 되돌아감). 지운 행이 없으면 빈 값 */
    Optional<KeyChange> deleteKey(String providerId);

    /** 키 변경 전후 공급자 모습 (변경 이력용) */
    record KeyChange(AiProviderInfo before, AiProviderInfo after) {
    }
}
