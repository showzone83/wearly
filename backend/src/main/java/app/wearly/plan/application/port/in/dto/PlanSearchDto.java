package app.wearly.plan.application.port.in.dto;

/**
 * 예정 목록 조회. from·to는 YYYY-MM-DD 문자열(둘 다 선택, 양 끝 포함). 형식 검사는 유스케이스가 한다
 *
 * @param from 이 날짜부터 (없으면 처음부터)
 * @param to 이 날짜까지 (없으면 끝까지)
 */
public record PlanSearchDto(long userId, String from, String to) {}
