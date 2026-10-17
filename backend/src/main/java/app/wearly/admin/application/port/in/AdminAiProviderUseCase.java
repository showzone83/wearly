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

  /**
   * 웹 키 넣기·바꾸기 (07 문서 6장). 404 → 409 SECRET_KEY_MISSING → 403 REAUTH_FAILED(currentPassword) → 400
   * VALIDATION_FAILED(apiKey) → 400 API_KEY_INVALID(verify, 기본 true) 순서로 확인한다. 변경 이력
   * AI_PROVIDER_KEY_SET (before·after에 keySource·keyLast4만)
   */
  ProviderView setKey(
      long adminId, String providerId, String apiKey, String currentPassword, Boolean verify);

  /** 웹 키 지우기 (환경변수 키로 되돌아감). 404 → 403 REAUTH_FAILED. 지운 키가 있을 때만 AI_PROVIDER_KEY_DELETE */
  ProviderView deleteKey(long adminId, String providerId, String currentPassword);

  record Types(List<ProviderTypeInfo> types, boolean baseUrlAllowed, boolean mockMode) {}
}
