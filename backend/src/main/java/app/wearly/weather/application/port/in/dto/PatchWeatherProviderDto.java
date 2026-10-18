package app.wearly.weather.application.port.in.dto;

import app.wearly.common.application.Patch;

/** 공급자 설정 부분 수정. monthlyQuota는 null을 보내면 한도 없음 */
public record PatchWeatherProviderDto(
    Patch<Boolean> enabled,
    Patch<Integer> priority,
    Patch<Integer> monthlyQuota,
    Patch<Integer> maxForecastDays) {}
