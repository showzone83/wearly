package app.wearly.ai.application.port.in.dto;

import app.wearly.common.application.Patch;
import java.util.Map;

/**
 * 공급자 부분 수정 (07 문서 4장). 보낸 필드만 바꾼다. providerId는 바꿀 수 없고(보내면 같은 값이어야 함), options를 보내면 options 전체를
 * 바꾼다(null이면 비움). enabled=true로 두려면 모델이 있어야 한다.
 */
public record PatchProviderDto(
    Patch<String> providerId,
    Patch<String> type,
    Patch<String> displayName,
    Patch<String> model,
    Patch<String> apiKeyEnv,
    Patch<Boolean> enabled,
    Patch<Integer> priority,
    Patch<Map<String, Object>> options) {}
