package app.wearly.weather.application.port.in.dto;

/** 실행 목록 조건 (트리거·상태 이름). null이면 거르지 않는다. 틀린 이름은 400 */
public record RunSearchDto(String trigger, String status) {}
