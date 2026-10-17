package app.wearly.wardrobe.application.port.in;

/** 목록 조회 조건 (쿼리 문자열 그대로). 검증은 유스케이스가 한다 */
public record ListItemsQuery(long userId, String category, String status, String color, String q, String sort) {
}
