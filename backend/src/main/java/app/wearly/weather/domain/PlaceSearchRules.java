package app.wearly.weather.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 장소 검색 결과 다듬기 (19 문서, Open-Meteo 지오코딩).
 *
 * <p>국내 지명 보정: 지오코딩은 "강릉"으로 찾으면 북한 황해북도의 '강릉' 마을만 주고, "강릉시"로 찾아야 강원도 강릉시를 준다. 그래서 검색어가 한글이고
 * 시·군·구·읍·면·동으로 끝나지 않는데 결과에 한국(KR)도 큰 도시(PPLC·PPLA)도 없으면 "&lt;검색어&gt;시", 그래도 한국이 없으면
 * "&lt;검색어&gt;군"을 더 찾아 합친다.
 *
 * <p>정렬: (한글 검색어면) 한국 먼저 → 행정 중심(PPLC·PPLA…)·도시(PPL…, ADM…) 먼저, 공항·공원 같은 시설은 뒤 → 인구 많은 순. 같은
 * 격자(GridKey)는 앞의 것만 남기고 max개까지.
 */
public final class PlaceSearchRules {

  /** 한글 음절만 (공백 없음) */
  private static final Pattern HANGUL = Pattern.compile("[가-힣]+");

  /** 이 글자로 끝나는 검색어는 이미 행정 구역 이름이라 보정하지 않는다 */
  private static final String ADMIN_SUFFIXES = "시군구읍면동";

  /** 보정 검색에 붙이는 글자 (차례대로, 한국 결과가 나오면 멈춘다) */
  static final List<String> RETRY_SUFFIXES = List.of("시", "군");

  private PlaceSearchRules() {}

  public static boolean hangul(String query) {
    return query != null && HANGUL.matcher(query).matches();
  }

  /** 보정 검색 후보 ("강릉" → [강릉시, 강릉군]). 한글이 아니거나 이미 시·군·구·읍·면·동으로 끝나면 빈 목록 */
  public static List<String> koreanVariants(String query) {
    if (!hangul(query) || ADMIN_SUFFIXES.indexOf(query.charAt(query.length() - 1)) >= 0) {
      return List.of();
    }
    return RETRY_SUFFIXES.stream().map(s -> query + s).toList();
  }

  /** 보정 검색을 할지. 후보가 있고, 결과에 한국도 수도·1급 행정 중심(PPLC·PPLA)도 없을 때만 (도쿄·오사카·파리처럼 큰 해외 도시는 더 부르지 않는다) */
  public static boolean needsKoreanRetry(String query, List<GeoPlace> places) {
    if (koreanVariants(query).isEmpty()) {
      return false;
    }
    return places.stream()
        .noneMatch(
            p -> p.korea() || "PPLC".equals(p.featureCode()) || "PPLA".equals(p.featureCode()));
  }

  public static boolean hasKorea(List<GeoPlace> places) {
    return places.stream().anyMatch(GeoPlace::korea);
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
   * 정렬·중복 제거·개수 제한 후 앱 모양으로 바꾼다.
   *
   * @param query 정리한 검색어 (한글이면 한국 결과를 앞으로. 영어 검색어는 이름이 비슷한 한국 마을이 앞에 오지 않게 나라 순서를 두지 않는다)
   */
  public static List<PlaceCandidate> rank(String query, List<GeoPlace> places, int max) {
    Comparator<GeoPlace> order =
        Comparator.<GeoPlace>comparingInt(p -> featureRank(p.featureCode()))
            .thenComparing(
                p -> p.population() == null ? 0L : p.population(), Comparator.reverseOrder());
    if (hangul(query)) {
      order = Comparator.<GeoPlace>comparingInt(p -> p.korea() ? 0 : 1).thenComparing(order);
    }
    List<GeoPlace> sorted = new ArrayList<>(places);
    sorted.sort(order);
    Map<String, GeoPlace> unique = new LinkedHashMap<>();
    for (GeoPlace p : sorted) {
      unique.putIfAbsent(GridKey.of(p.lat(), p.lon()).key(), p);
    }
    return unique.values().stream().limit(max).map(GeoPlace::toCandidate).toList();
  }
}
