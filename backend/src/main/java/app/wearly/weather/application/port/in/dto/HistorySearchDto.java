package app.wearly.weather.application.port.in.dto;

import java.time.LocalDate;

/**
 * 날씨 이력 조건. regionCode가 null이면 모든 지역. 기간은 [from, to] (한국 날짜, 둘 다 포함). 날짜가 null이면 유스케이스가 기본 기간을 채운다
 */
public record HistorySearchDto(String regionCode, LocalDate from, LocalDate to) {}
