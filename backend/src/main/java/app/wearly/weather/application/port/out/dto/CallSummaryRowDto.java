package app.wearly.weather.application.port.out.dto;

/** 기간 호출 집계. 호출이 없으면 avgDurationMs는 null */
public record CallSummaryRowDto(long calls, long failures, long fallbacks, Double avgDurationMs) {}
