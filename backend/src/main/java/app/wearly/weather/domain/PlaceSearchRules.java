package app.wearly.weather.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 장소 검색 결과 다듬기 (19 문서, Open-Meteo 지오코딩). 시·도 이름은 지오코딩 전에 KoreanRegionNames로 따로 맞춘다.
 *
 * <p>국내 지명 보정: 지오코딩은 "강릉"으로 찾으면 북한 황해북도의 '강릉' 마을만 주고, "강릉시"로 찾아야 강원도 강릉시를 준다. 그래서 검색어가 한글이고
 * 시·군·구·읍·면·동으로 끝나지 않는데 결과에 한국의 행정 중심·인구 있는 곳이 없고 큰 해외 도시(PPLC·PPLA)도 없으면 "&lt;검색어&gt;시",
 * "&lt;검색어&gt;군", "&lt;검색어&gt;광역시", "&lt;검색어&gt;특별시" 차례로 더 찾아 합친다 (한국 행정 중심·인구 있는 곳이 나오면 멈춤, 최대
 * MAX_RETRY_CALLS번).
 *
 * <p>정렬 (한글 검색어면 'Pusan'처럼 이름에 한글이 없는 국내 결과는 뺀다): (한글 검색어면) 한국의 행정 중심·인구 있는 곳 먼저 → 행정
 * 중심(PPLC·PPLA…) → 도시·마을(PPL…, ADM…) → 공항·공원·산 같은 시설·지형 → 인구 많은 순 → (한글 검색어면) 한국 먼저. 같은
 * 격자(GridKey)는 앞의 것만 남기고 max개까지.
 */
public final class PlaceSearchRules {

  /** 한글 음절만 (공백 없음) */
  private static final Pattern HANGUL = Pattern.compile("[가-힣]+");

  /** 이 글자로 끝나는 검색어는 이미 행정 구역 이름이라 보정하지 않는다 */
  private static final String ADMIN_SUFFIXES = "시군구읍면동";

  /** 보정 검색에 붙이는 말 (차례대로) */
  static final List<String> RETRY_SUFFIXES = List.of("시", "군", "광역시", "특별시");

  /**
   * 보정 검색 최대 호출 수 (원래 검색까지 합쳐 3번). 광역시·특별시는 시·도 이름 맞추기(KoreanRegionNames)가 먼저 해결하므로 이 수를 늘릴 때만 닿는다
   */
  public static final int MAX_RETRY_CALLS = 2;

  private PlaceSearchRules() {}

  public static boolean hangul(String query) {
    return query != null && HANGUL.matcher(query).matches();
  }

  /** 보정 검색 후보 ("강릉" → [강릉시, 강릉군]). 한글이 아니거나 이미 시·군·구·읍·면·동으로 끝나면 빈 목록. 최대 MAX_RETRY_CALLS개 */
  public static List<String> koreanVariants(String query) {
    if (!hangul(query) || ADMIN_SUFFIXES.indexOf(query.charAt(query.length() - 1)) >= 0) {
      return List.of();
    }
    return RETRY_SUFFIXES.stream().limit(MAX_RETRY_CALLS).map(s -> query + s).toList();
  }

  /**
   * 보정 검색을 할지. 후보가 있고, 결과에 한국의 행정 중심·인구 있는 곳도 해외 수도·1급 행정 중심(PPLC·PPLA)도 없을 때만 (도쿄·오사카·파리처럼 큰 해외
   * 도시는 더 부르지 않는다)
   */
  public static boolean needsKoreanRetry(String query, List<GeoPlace> places) {
    if (koreanVariants(query).isEmpty()) {
      return false;
    }
    return !hasKoreaMajor(places)
        && places.stream()
            .noneMatch(p -> "PPLC".equals(p.featureCode()) || "PPLA".equals(p.featureCode()));
  }

  /** 한국의 행정 중심이나 인구 있는 곳이 있는지 (보정 검색을 멈추는 조건) */
  public static boolean hasKoreaMajor(List<GeoPlace> places) {
    return places.stream().anyMatch(p -> p.korea() && p.major());
  }

  /** 지형 코드 순위: 0 수도·행정 중심, 1 도시·마을·행정 구역, 2 시설·자연 지형·모름 */
  static int featureRank(String code) {
    if (code == null) {
      return 2;
    }
    if (code.equals("PPLC") || code.startsWith("PPLA") || code.equals("PPLG")) {
      return 0;
    }
    if (code.startsWith("PPL") || code.startsWith("ADM")) {
      return 1;
    }
    return 2;
  }

  /**
   * 거르기·정렬·중복 제거·개수 제한 후 앱 모양으로 바꾼다.
   *
   * @param query 정리한 검색어 (한글이면 한국 결과를 앞으로. 영어 검색어는 이름이 비슷한 한국 마을이 앞에 오지 않게 나라 순서를 두지 않는다)
   */
  public static List<PlaceCandidate> rank(String query, List<GeoPlace> places, int max) {
    boolean hangul = hangul(query);
    Comparator<GeoPlace> order =
        Comparator.<GeoPlace>comparingInt(p -> featureRank(p.featureCode()))
            .thenComparing(
                p -> p.population() == null ? 0L : p.population(), Comparator.reverseOrder());
    if (hangul) {
      order =
          Comparator.<GeoPlace>comparingInt(p -> p.korea() && p.major() ? 0 : 1)
              .thenComparing(order)
              .thenComparingInt(p -> p.korea() ? 0 : 1);
    }
    List<GeoPlace> sorted =
        new ArrayList<>(
            places.stream().filter(p -> !hangul || !p.korea() || p.hangulName()).toList());
    sorted.sort(order);
    Map<String, GeoPlace> unique = new LinkedHashMap<>();
    for (GeoPlace p : sorted) {
      unique.putIfAbsent(GridKey.of(p.lat(), p.lon()).key(), p);
    }
    return unique.values().stream().limit(max).map(GeoPlace::toCandidate).toList();
  }
}
