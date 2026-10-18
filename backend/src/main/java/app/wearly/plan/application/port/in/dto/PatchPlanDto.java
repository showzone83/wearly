package app.wearly.plan.application.port.in.dto;

import app.wearly.common.application.Patch;
import app.wearly.common.domain.FieldError;
import java.util.List;
import java.util.Map;

/**
 * 예정 부분 수정 (보낸 필드만). null을 보내면 비운다 (memo·place·outfit·weatherSource·weatherCheckedAt). clientId는 바꿀
 * 수 없어 받지 않는다.
 *
 * @param inputErrors 입력 어댑터가 찾은 형식 오류 (JSON 타입 등)
 */
public record PatchPlanDto(
    Patch<String> date,
    Patch<String> timeOfDay,
    Patch<String> title,
    Patch<String> occasion,
    Patch<List<String>> concepts,
    Patch<String> memo,
    Patch<PlanPlaceDto> place,
    Patch<PlanWeatherDto> weather,
    Patch<String> weatherSource,
    Patch<String> weatherCheckedAt,
    Patch<Boolean> forecastReady,
    Patch<Map<String, Object>> outfit,
    List<FieldError> inputErrors) {}
