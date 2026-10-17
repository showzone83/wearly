package app.wearly.admin;

import app.wearly.ai.OptionSpec;
import java.util.List;
import java.util.Map;

/** 관리자 AI 공급자 API DTO (07 문서 4장 "AI 공급자"). 키 값은 어디에도 없고 keyConfigured만 준다 */
public final class AdminAiDtos {

    private AdminAiDtos() {
    }

    public record Today(long success, long failed) {
    }

    /** usable = enabled + 모델 + 키 환경변수 값 모두 갖춤 */
    public record ProviderDto(String providerId, String type, String displayName, String model, String apiKeyEnv,
            boolean keyConfigured, boolean enabled, int priority, Map<String, Object> options, boolean usable, Today today) {
    }

    public record CreateProviderRequest(String providerId, String type, String displayName, String model, String apiKeyEnv,
            Boolean enabled, Integer priority, Map<String, Object> options) {
    }

    public record TypeDto(String type, String description, List<OptionSpec> options) {
    }

    public record TypesResponse(List<TypeDto> types, boolean baseUrlAllowed, boolean mockMode) {
    }
}
