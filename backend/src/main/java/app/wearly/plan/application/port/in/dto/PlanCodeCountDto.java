package app.wearly.plan.application.port.in.dto;

/** 코드 하나를 쓰는 예정 수 */
public record PlanCodeCountDto(String group, String code, long count) {}
