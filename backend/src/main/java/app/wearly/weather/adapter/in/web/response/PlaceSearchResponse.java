package app.wearly.weather.adapter.in.web.response;

import app.wearly.weather.application.port.in.dto.PlaceCandidateDto;
import java.util.List;

/**
 * GET /weather/places 응답 (19 문서): { items: [{ label, region, country, lat, lon, regionCode }] }, 최대
 * 10개
 */
public record PlaceSearchResponse(List<Item> items) {

  /**
   * @param region 시·도·주 이름 (없으면 빈 문자열)
   * @param regionCode 국내 결과가 REGION 좌표와 30km 안이면 그 코드, 아니면 null
   */
  public record Item(
      String label, String region, String country, double lat, double lon, String regionCode) {}

  public static PlaceSearchResponse from(List<PlaceCandidateDto> items) {
    return new PlaceSearchResponse(
        items.stream()
            .map(
                c -> new Item(c.label(), c.region(), c.country(), c.lat(), c.lon(), c.regionCode()))
            .toList());
  }
}
