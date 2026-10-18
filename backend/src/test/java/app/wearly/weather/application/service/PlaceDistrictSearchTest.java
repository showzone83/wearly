package app.wearly.weather.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.weather.application.port.in.dto.PlaceCandidateDto;
import app.wearly.weather.domain.GeoPlace;
import app.wearly.weather.domain.KoreanDistrict;
import app.wearly.weather.domain.PlaceWeatherOptions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 19 문서: 장소 검색의 시·군·구 맞추기 (서버에 넣은 자료 파일 그대로). 순서는 시·도 → 시·군·구 → 지오코딩, 시·군·구 결과는 regionCode
 * null·parentRegionCode = 상위 시·도, 이름으로 맞으면 지오코딩을 부르지 않고, 한도 초과·공급자 실패여도 시·군·구 결과는 준다.
 */
class PlaceDistrictSearchTest {

  PlaceWeatherFakes fakes;
  PlaceWeatherService service;

  /** 지오코딩이 "일산"에 주는 답: 전남 장성의 일산동 마을, 고양 일산동구와 같은 격자의 일산역 */
  static final GeoPlace ILSAN_VILLAGE =
      new GeoPlace("일산동", "전라남도", "대한민국", "KR", "PPL", null, 35.28657, 126.76683);

  @BeforeEach
  void setUp() {
    fakes = new PlaceWeatherFakes();
    fakes.base.provider("WEATHERAPI", 1, null);
    fakes.base.regions = PlaceWeatherFakes.allRegions();
    fakes.districts = PlaceWeatherFakes.realDistricts();
    geo(Map.of());
  }

  void geo(Map<String, List<GeoPlace>> answers) {
    fakes.geoEnabled = true;
    fakes.geoAnswer = q -> PlaceWeatherFakes.geoOk(answers.getOrDefault(q, List.of()));
    service = fakes.service();
  }

  KoreanDistrict district(String fullName) {
    return fakes.districts.all().stream()
        .filter(d -> d.fullName().equals(fullName))
        .findFirst()
        .orElseThrow();
  }

  static BusinessException error(Runnable r) {
    try {
      r.run();
    } catch (BusinessException e) {
      return e;
    }
    throw new AssertionError("BusinessException이 나지 않았어요");
  }

  @Test
  void 해운대는_해운대구이고_좌표_방식으로_쓰게_regionCode는_없으며_지오코딩을_부르지_않는다() {
    geo(
        Map.of(
            "해운대",
            List.of(new GeoPlace("해운대공원", "부산광역시", "대한민국", "KR", "PRK", null, 35.156, 129.153))));
    KoreanDistrict h = district("부산광역시 해운대구");

    for (String q : List.of("해운대", "해운대구", "부산 해운대", "부산광역시 해운대구")) {
      assertThat(service.searchPlaces(1, q))
          .as(q)
          .singleElement()
          .isEqualTo(
              new PlaceCandidateDto("해운대구", "부산광역시", "대한민국", h.lat(), h.lon(), null, "BUSAN"));
    }
    assertThat(fakes.geocoded).isEmpty();
    assertThat(fakes.usage).isEmpty();
    assertThat(fakes.base.callRows).isEmpty();
  }

  @Test
  void 중구는_시도별로_모두_나오고_label에_시도를_붙인다() {
    List<PlaceCandidateDto> items = service.searchPlaces(1, "중구");

    assertThat(items)
        .extracting(
            PlaceCandidateDto::label,
            PlaceCandidateDto::region,
            PlaceCandidateDto::regionCode,
            PlaceCandidateDto::parentRegionCode)
        .containsExactly(
            tuple("서울 중구", "서울특별시", null, "SEOUL"),
            tuple("부산 중구", "부산광역시", null, "BUSAN"),
            tuple("대구 중구", "대구광역시", null, "DAEGU"),
            tuple("대전 중구", "대전광역시", null, "DAEJEON"),
            tuple("울산 중구", "울산광역시", null, "ULSAN"));
    assertThat(service.searchPlaces(1, "부산 중구"))
        .extracting(PlaceCandidateDto::label)
        .containsExactly("부산 중구");
    // 포항의 일반구는 시 이름을 붙인다
    assertThat(service.searchPlaces(1, "남구"))
        .extracting(PlaceCandidateDto::label)
        .contains("부산 남구", "포항 남구");
    assertThat(fakes.geocoded).isEmpty();
  }

  @Test
  void 분당과_성남_분당은_경기도_성남시_분당구다() {
    for (String q : List.of("분당", "성남 분당", "경기도 성남시 분당구")) {
      assertThat(service.searchPlaces(1, q))
          .as(q)
          .singleElement()
          .satisfies(
              c -> {
                assertThat(c.label()).isEqualTo("분당구");
                assertThat(c.region()).isEqualTo("경기도 성남시");
                assertThat(c.regionCode()).isNull();
                assertThat(c.parentRegionCode()).isEqualTo("GYEONGGI");
              });
    }
    assertThat(service.searchPlaces(1, "강남"))
        .extracting(PlaceCandidateDto::label, PlaceCandidateDto::region)
        .containsExactly(tuple("강남구", "서울특별시"));
  }

  @Test
  void 시도_이름과_겹치면_시도가_먼저이고_부산진구처럼_앞부분만_맞는_곳은_빠진다() {
    // 광주: 시·도 광주(광주광역시) → 경기도 광주시. 시·군·구 이름으로 맞았으니 지오코딩은 부르지 않는다
    assertThat(service.searchPlaces(1, "광주"))
        .extracting(
            PlaceCandidateDto::label,
            PlaceCandidateDto::region,
            PlaceCandidateDto::regionCode,
            PlaceCandidateDto::parentRegionCode)
        .containsExactly(
            tuple("광주", "광주광역시", "GWANGJU", null), tuple("광주시", "경기도", null, "GYEONGGI"));
    assertThat(fakes.geocoded).isEmpty();

    // 부산: 시·도만 (부산진구는 앞부분만 맞음). 시·군·구 이름으로 맞지 않았으니 지오코딩은 예전처럼 부른다
    assertThat(service.searchPlaces(1, "부산"))
        .extracting(PlaceCandidateDto::label)
        .containsExactly("부산");
    assertThat(fakes.geocoded).containsExactly("부산");
    assertThat(service.searchPlaces(1, "부산진"))
        .extracting(PlaceCandidateDto::label)
        .containsExactly("부산진구");
  }

  @Test
  void 앞부분만_맞으면_시군구_다음에_지오코딩_결과를_붙이고_같은_격자는_뺀다() {
    KoreanDistrict east = district("경기도 고양시 일산동구");
    GeoPlace sameGrid =
        new GeoPlace("일산역", "경기도", "대한민국", "KR", "RSTN", null, east.lat(), east.lon());
    geo(Map.of("일산", List.of(ILSAN_VILLAGE, sameGrid)));

    assertThat(service.searchPlaces(1, "일산"))
        .extracting(
            PlaceCandidateDto::label,
            PlaceCandidateDto::region,
            PlaceCandidateDto::parentRegionCode)
        .containsExactly(
            tuple("일산동구", "경기도 고양시", "GYEONGGI"),
            tuple("일산서구", "경기도 고양시", "GYEONGGI"),
            tuple("일산동", "전라남도", null));
    // 시·군·구와 맞았으니 일산시·일산군 보정 검색은 하지 않는다
    assertThat(fakes.geocoded).containsExactly("일산");
  }

  @Test
  void 한도를_넘거나_검색_공급자가_없어도_시군구_결과는_준다() {
    fakes.options =
        new PlaceWeatherOptions(
            Duration.ofHours(3), 30, Duration.ofDays(1), 1, true, 10, Duration.ofSeconds(2));
    geo(Map.of());
    service.searchPlaces(1, "도쿄");
    assertThat(error(() -> service.searchPlaces(1, "파리")).code())
        .isEqualTo(ErrorCode.WEATHER_LIMIT);

    // 앞부분만 맞아 지오코딩을 부르려다 한도에 걸려도 시·군·구는 준다
    assertThat(service.searchPlaces(1, "일산"))
        .extracting(PlaceCandidateDto::label)
        .containsExactly("일산동구", "일산서구");
    // 이름으로 맞으면 한도를 쓰지 않는다
    assertThat(service.searchPlaces(1, "해운대")).hasSize(1);
    assertThat(fakes.usage).containsOnlyKeys("SEARCH:2026-10-17:1");

    // 지오코딩이 꺼져 있고 검색되는 날씨 공급자도 없으면 503이지만 시·군·구는 준다
    PlaceWeatherFakes other = new PlaceWeatherFakes();
    other.base.provider("NOSEARCH", 1, null);
    other.base.regions = PlaceWeatherFakes.allRegions();
    other.districts = fakes.districts;
    PlaceWeatherService noSearch = other.service();
    assertThat(error(() -> noSearch.searchPlaces(1, "도쿄")).code())
        .isEqualTo(ErrorCode.WEATHER_UNAVAILABLE);
    assertThat(noSearch.searchPlaces(1, "일산")).hasSize(2);
    assertThat(noSearch.searchPlaces(1, "중구")).hasSize(5);
  }
}
