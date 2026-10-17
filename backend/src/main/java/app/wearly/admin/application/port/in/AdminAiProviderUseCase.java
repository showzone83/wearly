package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.AdminViews.ProviderView;
import app.wearly.ai.application.port.in.CreateProviderCommand;
import app.wearly.ai.application.port.in.ProviderTestResult;
import app.wearly.ai.application.port.in.ProviderTypeInfo;
import app.wearly.common.domain.ImageUpload;
import app.wearly.common.domain.PatchBody;
import java.util.List;

/** 관리자 AI 공급자 API (07 문서 3·4장). 실제 변경은 ai 컨텍스트가 하고, 변경 이력을 같은 트랜잭션에 남긴다 */
public interface AdminAiProviderUseCase {

    List<ProviderView> list();

    ProviderView create(long adminId, CreateProviderCommand command);

    ProviderView patch(long adminId, String providerId, PatchBody body);

    void delete(long adminId, String providerId);

    ProviderTestResult test(long adminId, String providerId, ImageUpload image);

    Types types();

    record Types(List<ProviderTypeInfo> types, boolean baseUrlAllowed, boolean mockMode) {
    }
}
