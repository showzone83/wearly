package app.wearly.ai.application.port.in.dto;

/** 구조화 JSON 생성 결과: handler가 만든 값과 응답한 공급자 ID·모델 */
public record GeneratedJsonDto<T>(T value, String provider, String model) {}
