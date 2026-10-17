package app.wearly.code.application.port.in.dto;

/** 코드 수정 전후 모습 (변경 이력용) */
public record CodeChangeDto(CodeDto before, CodeDto after) {}
