package app.wearly.admin.application.port.in.dto;

/** 날씨 호출 이력 검색 조건 (쿼리 문자열 그대로). 검증은 유스케이스가 한다 */
public record WeatherCallFilterDto(
    String providerCode,
    // FORECAST·ON_DEMAND·SEARCH·CLIMATE (대소문자 무관)
    String callKind,
    String regionCode,
    String success,
    String fallback,
    String errorCode,
    String from,
    String to) {}
