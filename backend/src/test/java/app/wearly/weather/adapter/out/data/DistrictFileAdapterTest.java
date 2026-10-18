package app.wearly.weather.adapter.out.data;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.weather.adapter.out.data.DistrictFileAdapter.Row;
import app.wearly.weather.domain.KoreanDistricts;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 19 문서: 시·군·구 자료 파일(resources/data/korea-districts.json) 형식. 필수 칸, 상위 시·도 코드, 좌표 범위(대한민국 영역·소수 넷째
 * 자리), 이름 중복 규칙(같은 이름은 다른 시·도나 다른 시에만), 근사 항목은 시·도 좌표. 자료를 만드는 스크립트는 실행하지 않는다.
 */
class DistrictFileAdapterTest {

  /** REGION 코드 → 정식 이름 (세종은 시·군·구가 없다) */
  static final Map<String, String> REGIONS =
      Map.ofEntries(
          Map.entry("SEOUL", "서울특별시"),
          Map.entry("BUSAN", "부산광역시"),
          Map.entry("DAEGU", "대구광역시"),
          Map.entry("INCHEON", "인천광역시"),
          Map.entry("GWANGJU", "광주광역시"),
          Map.entry("DAEJEON", "대전광역시"),
          Map.entry("ULSAN", "울산광역시"),
          Map.entry("GYEONGGI", "경기도"),
          Map.entry("GANGWON", "강원특별자치도"),
          Map.entry("CHUNGBUK", "충청북도"),
          Map.entry("CHUNGNAM", "충청남도"),
          Map.entry("JEONBUK", "전북특별자치도"),
          Map.entry("JEONNAM", "전라남도"),
          Map.entry("GYEONGBUK", "경상북도"),
          Map.entry("GYEONGNAM", "경상남도"),
          Map.entry("JEJU", "제주특별자치도"));

  /** REGION 좌표 (근사 항목이 쓰는 값, doc/06_코드_초기값.json) */
  static final Map<String, double[]> REGION_COORDS =
      Map.ofEntries(
          Map.entry("SEOUL", new double[] {37.5665, 126.978}),
          Map.entry("BUSAN", new double[] {35.1796, 129.0756}),
          Map.entry("DAEGU", new double[] {35.8714, 128.6014}),
          Map.entry("INCHEON", new double[] {37.4563, 126.7052}),
          Map.entry("GWANGJU", new double[] {35.1595, 126.8526}),
          Map.entry("DAEJEON", new double[] {36.3504, 127.3845}),
          Map.entry("ULSAN", new double[] {35.5384, 129.3114}),
          Map.entry("GYEONGGI", new double[] {37.2636, 127.0286}),
          Map.entry("GANGWON", new double[] {37.8813, 127.7298}),
          Map.entry("CHUNGBUK", new double[] {36.6424, 127.489}),
          Map.entry("CHUNGNAM", new double[] {36.6588, 126.6728}),
          Map.entry("JEONBUK", new double[] {35.8242, 127.148}),
          Map.entry("JEONNAM", new double[] {34.8161, 126.4629}),
          Map.entry("GYEONGBUK", new double[] {36.576, 128.5056}),
          Map.entry("GYEONGNAM", new double[] {35.228, 128.6811}),
          Map.entry("JEJU", new double[] {33.4996, 126.5312}));

  static final List<Row> ROWS =
      DistrictFileAdapter.load(JsonMapper.builder().build(), DistrictFileAdapter.RESOURCE);

  @Test
  void 전국_시군구가_있고_필수_칸이_차_있다() {
    // 자치구 69(인천 개편 후 70) · 시 75 · 군 82 · 일반구 · 제주 행정시 2
    assertThat(ROWS).hasSizeBetween(250, 280);
    for (Row r : ROWS) {
      assertThat(r.name()).as("name").isNotBlank().matches("[가-힣]+[시군구]");
      assertThat(r.regionCode()).as(r.fullName()).isIn(REGIONS.keySet());
      String region = REGIONS.get(r.regionCode());
      String expected =
          r.city() == null ? region + " " + r.name() : region + " " + r.city() + " " + r.name();
      assertThat(r.fullName()).isEqualTo(expected);
      if (r.city() != null) {
        assertThat(r.city()).as(r.fullName()).endsWith("시");
        assertThat(r.name()).as(r.fullName()).endsWith("구");
        // 일반구의 시도 자료에 한 줄로 있다
        assertThat(ROWS)
            .as(r.fullName())
            .anyMatch(p -> p.city() == null && p.name().equals(r.city()));
      }
    }
    // 세종을 뺀 16개 시·도가 모두 있다
    assertThat(ROWS.stream().map(Row::regionCode).collect(Collectors.toSet()))
        .isEqualTo(REGIONS.keySet());
  }

  @Test
  void 좌표는_대한민국_영역이고_소수_넷째_자리까지다() {
    for (Row r : ROWS) {
      assertThat(r.lat()).as(r.fullName()).isBetween(33.0, 38.7);
      assertThat(r.lon()).as(r.fullName()).isBetween(124.5, 131.0);
      assertThat(BigDecimal.valueOf(r.lat()).scale()).as(r.fullName()).isLessThanOrEqualTo(4);
      assertThat(BigDecimal.valueOf(r.lon()).scale()).as(r.fullName()).isLessThanOrEqualTo(4);
    }
  }

  @Test
  void 근사_항목은_시도_좌표이고_찾은_항목은_찾은_검색어가_있다() {
    for (Row r : ROWS) {
      if (r.approx()) {
        assertThat(new double[] {r.lat(), r.lon()})
            .as(r.fullName())
            .containsExactly(REGION_COORDS.get(r.regionCode()));
        assertThat(r.via()).as(r.fullName()).isNull();
      } else {
        assertThat(r.via()).as(r.fullName()).isNotBlank();
      }
    }
  }

  @Test
  void 정식_이름은_하나씩이고_같은_이름은_다른_시도나_다른_시에만_있다() {
    Set<String> full = new HashSet<>();
    for (Row r : ROWS) {
      assertThat(full.add(r.fullName())).as("정식 이름 중복: " + r.fullName()).isTrue();
    }
    Map<String, List<Row>> byName = ROWS.stream().collect(Collectors.groupingBy(Row::name));
    byName.forEach(
        (name, rows) -> {
          Set<String> parents =
              rows.stream().map(r -> r.regionCode() + "/" + r.city()).collect(Collectors.toSet());
          assertThat(parents).as(name).hasSize(rows.size());
        });
    assertThat(
            byName.entrySet().stream().filter(e -> e.getValue().size() > 1).map(Map.Entry::getKey))
        .containsExactlyInAnyOrder("중구", "동구", "서구", "남구", "북구", "강서구", "고성군");
  }

  @Test
  void 최근_행정구역_변경이_들어_있다() {
    Map<String, Row> byFull =
        ROWS.stream().collect(Collectors.toMap(Row::fullName, Function.identity()));
    assertThat(byFull)
        .containsKeys(
            "대구광역시 군위군",
            "강원특별자치도 춘천시",
            "전북특별자치도 전주시",
            "경기도 부천시 원미구",
            "경기도 화성시 동탄구",
            "인천광역시 제물포구",
            "인천광역시 영종구",
            "인천광역시 검단구")
        .doesNotContainKeys("경상북도 군위군", "인천광역시 중구", "인천광역시 동구");
  }

  @Test
  void 어댑터가_자료를_도메인으로_읽는다() {
    KoreanDistricts d = new DistrictFileAdapter(JsonMapper.builder().build()).districts();
    assertThat(d.all()).hasSize(ROWS.size());
    assertThat(d.all())
        .filteredOn(x -> x.fullName().equals("부산광역시 해운대구"))
        .singleElement()
        .satisfies(x -> assertThat(x.regionCode()).isEqualTo("BUSAN"));
  }
}
