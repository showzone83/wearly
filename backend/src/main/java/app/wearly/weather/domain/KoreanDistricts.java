package app.wearly.weather.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 전국 시·군·구 이름 맞추기 (19 문서, 장소 검색). 지오코딩은 "해운대구"·"마포구" 같은 구 이름을 못 찾거나 공원·시설만 주므로, 시·도 이름 맞추기
 * (KoreanRegionNames) 다음·지오코딩 전에 자료 파일(resources/data/korea-districts.json)의 시·군·구에 맞춘다.
 *
 * <p>검색어의 마지막 낱말이 시·군·구 이름과
 *
 * <ul>
 *   <li>같거나 (해운대구, 성남시) — {@link #EXACT}
 *   <li>끝의 시·군·구를 뗀 이름과 같거나 (해운대, 분당, 성남. 두 글자 이상) — {@link #STEM}
 *   <li>두 글자 이상 앞부분이면 (해운, 일산 → 일산동구·일산서구) — {@link #PREFIX}
 * </ul>
 *
 * 맞는다. 앞 낱말이 있으면 모두 그 곳의 시·도(부산·부산광역시) 또는 일반구의 시(성남·성남시)를 가리켜야 한다 ("부산 중구", "성남 분당", "경기도 성남시
 * 분당구"). 검색어가 시·도 이름과 글자까지 같으면(부산) 앞부분만 맞는 곳(부산진구)은 빼서 시·도가 먼저 보이게 한다.
 *
 * <p>이름이 겹치는 곳(중구·동구·서구·남구·북구·강서구·고성군)은 label 앞에 시·도 이름(부산 중구)이나 일반구의 시 이름(포항 남구)을 붙인다.
 */
public final class KoreanDistricts {

  /** 맞춘 정도: 이름 그대로 */
  public static final int EXACT = 0;

  /** 맞춘 정도: 끝의 시·군·구를 뗀 이름 */
  public static final int STEM = 1;

  /** 맞춘 정도: 이름 앞부분 (두 글자 이상) */
  public static final int PREFIX = 2;

  /** 앞부분 맞추기의 최소 글자 수 */
  static final int MIN_PREFIX = 2;

  public static final KoreanDistricts EMPTY = new KoreanDistricts(List.of());

  private final List<KoreanDistrict> all;

  /** 두 곳 이상에 있는 이름 (중구·동구·고성군 …) */
  private final Set<String> sharedNames;

  public KoreanDistricts(List<KoreanDistrict> all) {
    this.all = List.copyOf(all);
    this.sharedNames =
        all.stream()
            .collect(Collectors.groupingBy(KoreanDistrict::name, Collectors.counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
  }

  public List<KoreanDistrict> all() {
    return all;
  }

  /**
   * 맞은 시·군·구 한 곳.
   *
   * @param label 화면 이름 (해운대구, 겹치는 이름이면 부산 중구)
   * @param quality EXACT·STEM·PREFIX
   */
  public record Match(KoreanDistrict district, String label, int quality) {

    /** 이름 그대로이거나 줄기 이름으로 맞았는지 (사용자가 시·군·구를 뜻한 검색어) */
    public boolean named() {
      return quality <= STEM;
    }

    /** 검색 결과 한 곳 (region = 상위 이름, 나라 대한민국, 좌표 = 자료 좌표) */
    public PlaceCandidate candidate() {
      return new PlaceCandidate(
          label, district.parentName(), "대한민국", district.lat(), district.lon(), true);
    }
  }

  /**
   * 검색어(정리한 값)에 맞는 시·군·구. 맞춘 정도 순, 같으면 자료 순서(시·도 sort_order 순).
   *
   * @param regions 시·도 (label과 앞 낱말 맞추기에 쓴다, 비활성 포함)
   */
  public List<Match> match(List<WeatherRegion> regions, String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    String[] words = query.strip().split(" ");
    String last = words[words.length - 1];
    List<String> parents = List.of(words).subList(0, words.length - 1);
    boolean regionExact = words.length == 1 && KoreanRegionNames.exact(regions, query);
    Map<String, String> regionLabels = new HashMap<>();
    regions.forEach(r -> regionLabels.put(r.code(), r.label()));
    List<Match> out = new ArrayList<>();
    for (KoreanDistrict d : all) {
      Optional<Integer> q = quality(d, last);
      if (q.isEmpty() || (regionExact && q.get() == PREFIX)) {
        continue;
      }
      if (parents.stream().allMatch(p -> parentMatches(d, regionLabels.get(d.regionCode()), p))) {
        out.add(new Match(d, label(d, regionLabels.get(d.regionCode())), q.get()));
      }
    }
    out.sort(Comparator.comparingInt(Match::quality));
    return out;
  }

  static Optional<Integer> quality(KoreanDistrict d, String word) {
    if (word.equals(d.name())) {
      return Optional.of(EXACT);
    }
    if (word.equals(d.stem())) {
      return Optional.of(STEM);
    }
    if (word.length() >= MIN_PREFIX && d.name().startsWith(word)) {
      return Optional.of(PREFIX);
    }
    return Optional.empty();
  }

  /** 앞 낱말이 그 곳의 시·도나 일반구의 시를 가리키는지 */
  static boolean parentMatches(KoreanDistrict d, String regionLabel, String word) {
    if (KoreanRegionNames.matchesRegion(d.regionCode(), regionLabel, word)) {
      return true;
    }
    String city = d.city();
    return city != null
        && (word.equals(city)
            || word.equals(d.cityStem())
            || (word.length() >= MIN_PREFIX && city.startsWith(word)));
  }

  /** 화면 이름: 겹치는 이름이면 앞에 일반구의 시(포항 남구)나 시·도 label(부산 중구)을 붙인다 */
  String label(KoreanDistrict d, String regionLabel) {
    if (!sharedNames.contains(d.name())) {
      return d.name();
    }
    String prefix = d.city() != null ? Optional.ofNullable(d.cityStem()).orElse(d.city()) : null;
    if (prefix == null) {
      prefix = regionLabel != null ? regionLabel : d.parentName();
    }
    return prefix + " " + d.name();
  }
}
