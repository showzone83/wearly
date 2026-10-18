package app.wearly.weather.application.port.in.dto;

import java.time.Instant;

/**
 * 호출 이력 조건. null이면 거르지 않는다. 기간은 [from, to).
 *
 * @param callKind FORECAST·ON_DEMAND·SEARCH·CLIMATE (대문자로 바꿔 넘긴다. 검사는 유스케이스가 한다)
 */
public record CallSearchDto(
    String providerCode,
    String callKind,
    String regionCode,
    Boolean success,
    Boolean fallback,
    String errorCode,
    Instant from,
    Instant to) {}
