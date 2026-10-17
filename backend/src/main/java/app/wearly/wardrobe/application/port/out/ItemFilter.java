package app.wearly.wardrobe.application.port.out;

/** 목록 필터. 값은 이미 검증된 DB 문자열이다. qPattern은 LIKE 특수문자를 이스케이프한 '%검색어%' 형태다. */
public record ItemFilter(
    long userId, String category, String status, String color, String qPattern, String sort) {}
