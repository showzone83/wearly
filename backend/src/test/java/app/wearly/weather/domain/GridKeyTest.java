package app.wearly.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** 19 문서 1장: 좌표 격자 키 (소수 첫째 자리 반올림), 거리, 평년값 기간 */
class GridKeyTest {

  @Test
  void 소수_첫째_자리로_반올림해_키를_만든다() {
    GridKey seoul = GridKey.of(37.5665, 126.978);
    assertThat(seoul.key()).isEqualTo("37.6,127.0");
    assertThat(seoul.lat()).isEqualTo(37.6);
    assertThat(seoul.lon()).isEqualTo(127.0);

    // 가까운 좌표는 같은 격자로 모인다
    assertThat(GridKey.of(37.61, 126.96).key()).isEqualTo("37.6,127.0");
    assertThat(GridKey.of(35.6895, 139.6917).key()).isEqualTo("35.7,139.7");
    // 남반구·서경, -0.0은 0.0
    assertThat(GridKey.of(-33.8688, -151.2093).key()).isEqualTo("-33.9,-151.2");
    assertThat(GridKey.of(-0.04, 0.04).key()).isEqualTo("0.0,0.0");
  }

  @Test
  void 위도_경도_범위() {
    assertThat(GridKey.validLat(90)).isTrue();
    assertThat(GridKey.validLat(-90.1)).isFalse();
    assertThat(GridKey.validLon(180)).isTrue();
    assertThat(GridKey.validLon(180.5)).isFalse();
    assertThat(GridKey.validLat(Double.NaN)).isFalse();
  }

  @Test
  void 하버사인_거리() {
    // 서울시청 ↔ 부산시청 약 325km
    assertThat(GridKey.distanceKm(37.5665, 126.978, 35.1796, 129.0756))
        .isCloseTo(325, Offset.offset(5.0));
    assertThat(GridKey.distanceKm(1, 2, 1, 2)).isZero();
  }

  @Test
  void 평년값_기간은_지난_완결_연도다() {
    ClimateYears y = ClimateYears.of(LocalDate.of(2026, 10, 18), 10);
    assertThat(y.from()).isEqualTo(LocalDate.of(2016, 1, 1));
    assertThat(y.to()).isEqualTo(LocalDate.of(2025, 12, 31));
    assertThat(y.label()).isEqualTo("2016-2025");
    assertThat(ClimateYears.of(LocalDate.of(2026, 1, 1), 1).label()).isEqualTo("2025-2025");
  }
}
