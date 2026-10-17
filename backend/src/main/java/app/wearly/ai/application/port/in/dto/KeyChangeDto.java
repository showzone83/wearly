package app.wearly.ai.application.port.in.dto;

/** 키 변경 전후 공급자 모습 (변경 이력용) */
public record KeyChangeDto(AiProviderDto before, AiProviderDto after) {}
