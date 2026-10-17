package app.wearly.wardrobe.application.port.in.dto;

/**
 * 목록 조회 조건 (쿼리 문자열 그대로). 검증은 유스케이스가 한다. retired는 false(기본, 사용 중인 옷만)·true(사용 중지한 옷만)·all (13 문서 3장)
 */
public record ItemSearchDto(
    long userId,
    String category,
    String status,
    String color,
    String q,
    String sort,
    String retired) {

  public static final String RETIRED_ALL = "all";
}
