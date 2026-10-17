package app.wearly.code.application.port.in.dto;

/** 코드 하나의 사용 수 (CodeUsageSource) */
public record CodeUsageCountDto(String group, String code, long count) {}
