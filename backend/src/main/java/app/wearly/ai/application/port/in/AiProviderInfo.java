package app.wearly.ai.application.port.in;

import java.time.Instant;
import java.util.Map;

/**
 * 공급자 한 행 + 키 상태 (07 문서 4·6장). 키 값은 어디에도 없다. keyConfigured = keySource != NONE, usable = enabled +
 * 모델 + 키 모두 갖춤. keyLast4·keyUpdatedAt·keyUpdatedBy(넣은 관리자 이메일)는 DB 키를 쓸 때만 있다. secretStoreEnabled =
 * 서버에 WEARLY_SECRET_KEY가 있어 웹 키를 쓸 수 있는지.
 */
public record AiProviderInfo(
    String providerId,
    String type,
    String displayName,
    String model,
    String apiKeyEnv,
    boolean keyConfigured,
    boolean enabled,
    int priority,
    Map<String, Object> options,
    boolean usable,
    String keySource,
    String keyLast4,
    Instant keyUpdatedAt,
    String keyUpdatedBy,
    boolean secretStoreEnabled) {}
