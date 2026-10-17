package app.wearly.admin.application.port.in.dto;

/** AI 기록 검색 조건 (쿼리 문자열 그대로). 검증은 유스케이스가 한다 */
public record AiLogSearchDto(
    String providerId, String status, String userEmail, String feature, String from, String to) {}
