package app.wearly.wardrobe.application.port.in.dto;

/** 사용하지 않는 옷으로 옮기기 입력 (13 문서 3장). reason은 RETIRE_REASON 코드, memo는 선택 */
public record RetireItemDto(String reason, String memo) {}
