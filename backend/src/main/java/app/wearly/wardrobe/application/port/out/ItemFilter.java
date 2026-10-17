package app.wearly.wardrobe.application.port.out;

/** 목록 필터. 값은 이미 검증된 DB 문자열이다. query는 앞뒤 공백을 지운 원래 검색어이고, LIKE 패턴은 쿼리 어댑터가 만든다 (L3). */
public record ItemFilter(
    long userId, String category, String status, String color, String query, String sort) {}
