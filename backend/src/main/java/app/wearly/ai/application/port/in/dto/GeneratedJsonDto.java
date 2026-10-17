package app.wearly.ai.application.port.in.dto;

/**
 * 구조화 JSON 생성 결과: handler가 만든 값과 응답한 공급자 ID·모델.
 *
 * @param usedToday 이 호출까지 센 오늘 사용 수 (남은 횟수 = 한도 - usedToday)
 */
public record GeneratedJsonDto<T>(T value, String provider, String model, long usedToday) {}
