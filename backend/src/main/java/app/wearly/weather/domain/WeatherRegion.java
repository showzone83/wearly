package app.wearly.weather.domain;

import app.wearly.sharedkernel.code.CodeEntry;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.util.List;
import java.util.Optional;

/** 날씨를 받아 두는 지역 = 코드 그룹 REGION 한 줄 (attrs.lat·lon). 활성 지역만 주기적으로 받는다 */
public record WeatherRegion(String code, String label, double lat, double lon, boolean active) {

  /** 프로필·요청에 지역이 없을 때 */
  public static final String DEFAULT_CODE = "SEOUL";

  /** 위·경도가 있는 REGION 코드 전부 (sort_order 순서, 비활성 포함) */
  public static List<WeatherRegion> all(CodeSnapshot codes) {
    return codes.codes(CodeGroups.REGION).stream()
        .map(WeatherRegion::of)
        .flatMap(Optional::stream)
        .toList();
  }

  public static List<WeatherRegion> active(CodeSnapshot codes) {
    return all(codes).stream().filter(WeatherRegion::active).toList();
  }

  public static Optional<WeatherRegion> find(CodeSnapshot codes, String code) {
    return codes.find(CodeGroups.REGION, code).flatMap(WeatherRegion::of);
  }

  private static Optional<WeatherRegion> of(CodeEntry e) {
    Object lat = e.attrs() == null ? null : e.attrs().get("lat");
    Object lon = e.attrs() == null ? null : e.attrs().get("lon");
    if (!(lat instanceof Number la) || !(lon instanceof Number lo)) {
      return Optional.empty();
    }
    return Optional.of(
        new WeatherRegion(e.code(), e.label(), la.doubleValue(), lo.doubleValue(), e.active()));
  }
}
