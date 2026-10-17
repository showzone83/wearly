package app.wearly.weather.application.port.in.dto;

import java.util.List;

/** 목록 한 쪽과 전체 개수 */
public record PageResultDto<T>(List<T> items, long total) {}
