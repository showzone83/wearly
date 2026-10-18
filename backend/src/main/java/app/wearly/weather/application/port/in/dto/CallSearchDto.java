package app.wearly.weather.application.port.in.dto;

import java.time.Instant;

/** 호출 이력 조건. null이면 거르지 않는다. 기간은 [from, to) */
public record CallSearchDto(
    String providerCode,
    String regionCode,
    Boolean success,
    Boolean fallback,
    String errorCode,
    Instant from,
    Instant to) {}
