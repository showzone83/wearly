package app.wearly.ai.application.port.in.dto;

import java.util.List;

/**
 * 지원 공급자 종류와 서버 설정.
 *
 * @param baseUrlAllowed WEARLY_AI_ALLOW_BASE_URL
 * @param mockMode WEARLY_AI_PROVIDER=mock
 */
public record ProviderCatalogDto(
    List<ProviderTypeDto> types, boolean baseUrlAllowed, boolean mockMode) {}
