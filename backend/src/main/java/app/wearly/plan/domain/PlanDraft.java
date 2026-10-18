package app.wearly.plan.domain;

import java.util.List;
import java.util.Map;

/**
 * 검증 전 예정 코디 입력 (요청 그대로, PATCH는 저장된 값 위에 보낸 필드를 덮어쓴 결과). PlanRules가 검증하고 PlanInput으로 바꾼다.
 *
 * @param clientId 앱이 기기에서 만든 예정 ID (같은 clientId면 새로 만들지 않고 고친다). 선택
 * @param date YYYY-MM-DD
 * @param outfit 앱 Outfit 모양 그대로 (items: 칸 → 아이템 ID). 없으면 코디 없음
 */
public record PlanDraft(
    String clientId,
    String date,
    String timeOfDay,
    String title,
    String occasion,
    List<String> concepts,
    String memo,
    Place place,
    Weather weather,
    String weatherSource,
    String weatherCheckedAt,
    Boolean forecastReady,
    Map<String, Object> outfit) {

  /** 장소. 없으면 내 지역 */
  public record Place(String regionCode, String label, Double lat, Double lon) {}

  /** 앱 Weather 모양 (manual: 사용자가 체감 온도를 직접 고름) */
  public record Weather(
      Double temp, Double min, Double max, Double rainProb, String cond, Boolean manual) {}
}
