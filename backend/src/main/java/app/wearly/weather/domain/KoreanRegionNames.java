package app.wearly.weather.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 시·도(코드 그룹 REGION) 이름 맞추기 (19 문서, 장소 검색). 지오코딩은 "서울"·"제주"를 찾지 못하고 "부산"·"대구"·"인천"은 같은 이름의 작은 마을만
 * 주므로, 지오코딩 전에 검색어를 활성 REGION의 label과 정식 이름·별칭에 맞춰 그 지역을 맨 앞에 넣는다.
 *
 * <p>맞는 조건: 검색어가 label(서울)이나 정식 이름·별칭(서울특별시·서울시)과 같거나, 두 글자 이상이면서 그 앞부분(서울특 → 서울특별시, 충청 → 충북·충남)인
 * 경우.
 */
public final class KoreanRegionNames {

  /** 검색어 앞부분 맞추기의 최소 글자 수 ("경" 한 글자로 경기·경북·경남이 모두 나오지 않게) */
  static final int MIN_PREFIX = 2;

  /** REGION 코드 → 정식 이름·별칭 (첫 번째가 정식 이름이고 검색 결과의 region 칸에 쓴다). label은 REGION 코드 행에서 읽는다 */
  static final Map<String, List<String>> NAMES =
      Map.ofEntries(
          Map.entry("SEOUL", List.of("서울특별시", "서울시")),
          Map.entry("BUSAN", List.of("부산광역시", "부산시")),
          Map.entry("DAEGU", List.of("대구광역시", "대구시")),
          Map.entry("INCHEON", List.of("인천광역시", "인천시")),
          // 광주시는 경기도 광주시와 겹쳐 별칭으로 두지 않는다
          Map.entry("GWANGJU", List.of("광주광역시")),
          Map.entry("DAEJEON", List.of("대전광역시", "대전시")),
          Map.entry("ULSAN", List.of("울산광역시", "울산시")),
          Map.entry("SEJONG", List.of("세종특별자치시", "세종시")),
          Map.entry("GYEONGGI", List.of("경기도")),
          Map.entry("GANGWON", List.of("강원특별자치도", "강원도")),
          Map.entry("CHUNGBUK", List.of("충청북도")),
          Map.entry("CHUNGNAM", List.of("충청남도")),
          Map.entry("JEONBUK", List.of("전북특별자치도", "전라북도")),
          Map.entry("JEONNAM", List.of("전라남도")),
          Map.entry("GYEONGBUK", List.of("경상북도")),
          Map.entry("GYEONGNAM", List.of("경상남도")),
          Map.entry("JEJU", List.of("제주특별자치도", "제주도", "제주시")));

  private KoreanRegionNames() {}

  /** 검색어(정리한 값)에 맞는 지역들 (regions 순서 = REGION sort_order) */
  public static List<WeatherRegion> match(List<WeatherRegion> regions, String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    List<WeatherRegion> out = new ArrayList<>();
    for (WeatherRegion r : regions) {
      if (names(r).stream().anyMatch(n -> matches(n, query))) {
        out.add(r);
      }
    }
    return out;
  }

  private static boolean matches(String name, String query) {
    return name.equals(query) || (query.length() >= MIN_PREFIX && name.startsWith(query));
  }

  private static List<String> names(WeatherRegion r) {
    List<String> out = new ArrayList<>();
    out.add(r.label());
    out.addAll(NAMES.getOrDefault(r.code(), List.of()));
    return out;
  }

  /** 지역 → 검색 결과 한 곳 (label = REGION label, region = 정식 이름, 나라 대한민국, 좌표 = REGION 좌표) */
  public static PlaceCandidate candidate(WeatherRegion r) {
    List<String> names = NAMES.getOrDefault(r.code(), List.of());
    return new PlaceCandidate(
        r.label(), names.isEmpty() ? "" : names.getFirst(), "대한민국", r.lat(), r.lon(), true);
  }
}
