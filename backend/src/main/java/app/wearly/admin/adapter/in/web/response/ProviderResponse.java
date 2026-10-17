package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminProviderDto;
import app.wearly.common.web.Times;
import java.util.Map;

/**
 * 공급자 한 행 (07 문서 4·6장). 키 값은 어디에도 없다. usable = enabled + 모델 + 키 모두 갖춤. keySource = DB | ENV | NONE
 * (keyConfigured = keySource != NONE). keyLast4·keyUpdatedAt·keyUpdatedBy는 DB 키일 때만.
 */
public record ProviderResponse(
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
    Today today,
    String keySource,
    String keyLast4,
    String keyUpdatedAt,
    String keyUpdatedBy,
    boolean secretStoreEnabled) {

  public record Today(long success, long failed) {}

  public static ProviderResponse from(AdminProviderDto p) {
    return new ProviderResponse(
        p.providerId(),
        p.type(),
        p.displayName(),
        p.model(),
        p.apiKeyEnv(),
        p.keyConfigured(),
        p.enabled(),
        p.priority(),
        p.options(),
        p.usable(),
        new Today(p.today().success(), p.today().failed()),
        p.keySource(),
        p.keyLast4(),
        Times.iso(p.keyUpdatedAt()),
        p.keyUpdatedBy(),
        p.secretStoreEnabled());
  }
}
