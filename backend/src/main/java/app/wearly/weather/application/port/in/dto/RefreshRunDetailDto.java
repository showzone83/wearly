package app.wearly.weather.application.port.in.dto;

import java.util.List;

/** 실행 상세: 그 실행의 호출 전부 (시각순) */
public record RefreshRunDetailDto(RefreshRunDto run, List<WeatherCallDto> calls) {}
