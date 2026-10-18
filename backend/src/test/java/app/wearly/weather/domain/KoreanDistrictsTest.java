package app.wearly.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 19 문서: 시·군·구 이름 맞추기 (정식·줄기·앞부분, 앞 낱말로 시·도·시 좁히기, 겹치는 이름의 label, 시·도 이름 우선) */
class KoreanDistrictsTest {

  static final List<WeatherRegion> REGIONS =
      List.of(
          new WeatherRegion("SEOUL", "서울", 37.5665, 126.978, true),
          new WeatherRegion("BUSAN", "부산", 35.1796, 129.0756, true),
          new WeatherRegion("GWANGJU", "광주", 35.1595, 126.8526, true),
          new WeatherRegion("GYEONGGI", "경기", 37.2636, 127.0286, true),
          new WeatherRegion("GANGWON", "강원", 37.8813, 127.7298, true),
          new WeatherRegion("GYEONGBUK", "경북", 36.576, 128.5056, true),
          new WeatherRegion("GYEONGNAM", "경남", 35.228, 128.6811, true));

  static KoreanDistrict d(String full, String city, String code) {
    String name = full.substring(full.lastIndexOf(' ') + 1);
    return new KoreanDistrict(name, full, city, code, 35.0, 128.0, false);
  }

  static final KoreanDistricts DATA =
      new KoreanDistricts(
          List.of(
              d("서울특별시 중구", null, "SEOUL"),
              d("서울특별시 강남구", null, "SEOUL"),
              d("부산광역시 중구", null, "BUSAN"),
              d("부산광역시 부산진구", null, "BUSAN"),
              d("부산광역시 남구", null, "BUSAN"),
              d("부산광역시 해운대구", null, "BUSAN"),
              d("경기도 성남시", null, "GYEONGGI"),
              d("경기도 성남시 분당구", "성남시", "GYEONGGI"),
              d("경기도 고양시 일산동구", "고양시", "GYEONGGI"),
              d("경기도 고양시 일산서구", "고양시", "GYEONGGI"),
              d("경기도 광주시", null, "GYEONGGI"),
              d("강원특별자치도 고성군", null, "GANGWON"),
              d("경상북도 포항시 남구", "포항시", "GYEONGBUK"),
              d("경상남도 고성군", null, "GYEONGNAM")));

  static List<KoreanDistricts.Match> match(String q) {
    return DATA.match(REGIONS, q);
  }

  @Test
  void 줄기_이름_해운대는_해운대구이고_label은_이름_그대로_region은_상위_이름이다() {
    assertThat(match("해운대"))
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.label()).isEqualTo("해운대구");
              assertThat(m.quality()).isEqualTo(KoreanDistricts.STEM);
              assertThat(m.named()).isTrue();
              PlaceCandidate c = m.candidate();
              assertThat(c.region()).isEqualTo("부산광역시");
              assertThat(c.country()).isEqualTo("대한민국");
            });
    assertThat(match("해운대구"))
        .singleElement()
        .extracting(KoreanDistricts.Match::quality)
        .isEqualTo(KoreanDistricts.EXACT);
    assertThat(match("부산광역시 해운대구")).hasSize(1);
  }

  @Test
  void 겹치는_이름은_시도별로_모두_나오고_label에_상위를_붙인다() {
    assertThat(match("중구"))
        .extracting(KoreanDistricts.Match::label)
        .containsExactly("서울 중구", "부산 중구");
    // 일반구는 시 이름을 붙인다 (포항 남구)
    assertThat(match("남구"))
        .extracting(KoreanDistricts.Match::label)
        .containsExactly("부산 남구", "포항 남구");
    assertThat(match("고성"))
        .extracting(KoreanDistricts.Match::label)
        .containsExactly("강원 고성군", "경남 고성군");
    // 겹치지 않는 이름은 그대로
    assertThat(match("강남")).extracting(KoreanDistricts.Match::label).containsExactly("강남구");
  }

  @Test
  void 앞_낱말로_시도나_시를_좁힌다() {
    assertThat(match("부산 중구")).extracting(KoreanDistricts.Match::label).containsExactly("부산 중구");
    assertThat(match("부산광역시 중구")).hasSize(1);
    assertThat(match("서울시 중구")).extracting(KoreanDistricts.Match::label).containsExactly("서울 중구");
    assertThat(match("경남 고성")).extracting(KoreanDistricts.Match::label).containsExactly("경남 고성군");
    assertThat(match("포항 남구")).extracting(KoreanDistricts.Match::label).containsExactly("포항 남구");
    assertThat(match("경북 남구")).extracting(KoreanDistricts.Match::label).containsExactly("포항 남구");
    // 앞 낱말이 맞지 않으면 없다
    assertThat(match("대구 해운대")).isEmpty();
    assertThat(match("부산 분당")).isEmpty();
  }

  @Test
  void 분당과_성남_분당은_분당구이고_성남은_성남시다() {
    for (String q : List.of("분당", "분당구", "성남 분당", "성남시 분당구", "경기도 성남시 분당구", "경기 성남 분당")) {
      assertThat(match(q))
          .as(q)
          .singleElement()
          .satisfies(
              m -> {
                assertThat(m.label()).isEqualTo("분당구");
                assertThat(m.candidate().region()).isEqualTo("경기도 성남시");
              });
    }
    assertThat(match("성남"))
        .extracting(m -> m.district().fullName(), KoreanDistricts.Match::quality)
        .containsExactly(tuple("경기도 성남시", KoreanDistricts.STEM));
  }

  @Test
  void 앞부분만_맞으면_PREFIX이고_맞춘_정도_순서로_준다() {
    assertThat(match("일산"))
        .extracting(KoreanDistricts.Match::label, KoreanDistricts.Match::named)
        .containsExactly(tuple("일산동구", false), tuple("일산서구", false));
    assertThat(match("해운")).extracting(KoreanDistricts.Match::named).containsExactly(false);
    // 한 글자는 맞추지 않는다
    assertThat(match("중")).isEmpty();
    assertThat(match("강")).isEmpty();
    assertThat(match(" ")).isEmpty();
    assertThat(match(null)).isEmpty();
  }

  @Test
  void 시도_이름과_글자까지_같으면_앞부분만_맞는_곳은_빼고_줄기_이름은_남긴다() {
    // 부산 = 시·도 이름 → 부산진구(앞부분)는 빼서 시·도가 먼저 보이게
    assertThat(match("부산")).isEmpty();
    assertThat(match("부산진")).extracting(KoreanDistricts.Match::label).containsExactly("부산진구");
    // 광주 = 시·도 이름(광주광역시)이지만 경기도 광주시는 줄기 이름으로 맞아 남는다
    assertThat(match("광주")).extracting(m -> m.district().fullName()).containsExactly("경기도 광주시");
  }

  @Test
  void 줄기_이름은_두_글자_이상_남을_때만() {
    assertThat(d("부산광역시 중구", null, "BUSAN").stem()).isNull();
    assertThat(d("부산광역시 해운대구", null, "BUSAN").stem()).isEqualTo("해운대");
    assertThat(d("경기도 성남시 분당구", "성남시", "GYEONGGI").cityStem()).isEqualTo("성남");
    assertThat(d("경기도 성남시 분당구", "성남시", "GYEONGGI").parentName()).isEqualTo("경기도 성남시");
  }
}
