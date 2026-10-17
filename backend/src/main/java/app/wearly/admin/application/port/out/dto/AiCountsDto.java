package app.wearly.admin.application.port.out.dto;

/** 구간 안의 AI 호출 수 (모든 기능) */
public record AiCountsDto(long total, long success, long failed) {}
