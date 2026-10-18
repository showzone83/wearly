package app.wearly.weather.application.port.in.dto;

import java.util.List;
import java.util.Map;

/**
 * 관리자 시험 호출 결과. 저장된 날씨·공급자 상태는 바꾸지 않고, 호출 이력에는 트리거 TEST로 남는다.
 *
 * @param current 실패면 null
 * @param raw 공급자 원본 응답 (실패면 빈 객체)
 */
public record ProviderTestResultDto(
    long runId,
    String providerCode,
    String regionCode,
    boolean success,
    String errorCode,
    String errorMessage,
    Integer httpStatus,
    long durationMs,
    String locationName,
    CurrentWeatherDto current,
    List<DailyForecastDto> days,
    Map<String, Object> raw) {}
