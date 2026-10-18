package app.wearly.plan.domain;

/**
 * 예정 장소 (19 문서 1장 place). 시·도는 regionCode(REGION 코드), 그 밖은 검색 결과의 이름·좌표만.
 *
 * @param regionCode REGION 코드 또는 null
 */
public record PlanPlace(String regionCode, String label, double lat, double lon) {}
