package app.wearly.weather.domain;

/**
 * 시·군·구 한 곳 (자료 파일 resources/data/korea-districts.json, 19 문서 장소 검색).
 *
 * @param name 시·군·구 이름 (해운대구·성남시·분당구·가평군)
 * @param fullName 정식 이름 (부산광역시 해운대구, 경기도 성남시 분당구)
 * @param city 일반구의 상위 시 (분당구 → 성남시). 자치구·시·군은 null
 * @param regionCode 상위 시·도 (코드 그룹 REGION, 예: BUSAN)
 * @param lat 대표 좌표 위도 (구청·시청·군청 근처, 소수 넷째 자리)
 * @param lon 대표 좌표 경도
 * @param approx 지오코딩으로 찾지 못해 상위 시·도 좌표를 쓴 곳
 */
public record KoreanDistrict(
    String name,
    String fullName,
    String city,
    String regionCode,
    double lat,
    double lon,
    boolean approx) {

  /** 끝의 시·군·구를 뗀 이름 (해운대구 → 해운대, 분당구 → 분당). 두 글자 이상 남을 때만, 아니면 null (중구 → null) */
  public String stem() {
    return stemOf(name);
  }

  /** 일반구의 상위 시 줄기 이름 (성남시 → 성남). 없으면 null */
  public String cityStem() {
    return city == null ? null : stemOf(city);
  }

  /** 검색 결과의 region 칸: 정식 이름에서 자기 이름을 뺀 앞부분 (부산광역시, 경기도 성남시) */
  public String parentName() {
    String suffix = " " + name;
    return fullName.endsWith(suffix)
        ? fullName.substring(0, fullName.length() - suffix.length())
        : fullName;
  }

  static String stemOf(String name) {
    if (name == null || name.length() < 3) {
      return null;
    }
    char last = name.charAt(name.length() - 1);
    return last == '시' || last == '군' || last == '구' ? name.substring(0, name.length() - 1) : null;
  }
}
