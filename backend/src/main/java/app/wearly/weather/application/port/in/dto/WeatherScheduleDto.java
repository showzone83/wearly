package app.wearly.weather.application.port.in.dto;

import java.time.Instant;

/**
 * 관리자 스케줄 화면: 설정 + 다음 실행 예정 + 마지막 실행 + 실행 중 여부.
 *
 * @param nextRunAt 스케줄이 꺼져 있거나 서버 주기 실행이 꺼져 있으면 null
 * @param lastRun 끝난 실행이 없으면 null
 * @param schedulerEnabled 서버 설정 wearly.weather.scheduler-enabled
 */
public record WeatherScheduleDto(
    WeatherSettingDto setting,
    Instant nextRunAt,
    boolean running,
    RefreshRunDto lastRun,
    int activeRegions,
    boolean schedulerEnabled) {}
