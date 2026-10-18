package app.wearly.weather.adapter.out.data;

import app.wearly.weather.application.port.out.DistrictCatalogPort;
import app.wearly.weather.domain.KoreanDistrict;
import app.wearly.weather.domain.KoreanDistricts;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * DistrictCatalogPort 구현: 클래스패스 자료 파일 data/korea-districts.json을 시작할 때 한 번 읽는다 (만드는 법:
 * backend/scripts/build-districts.py). 파일이 없거나 깨졌으면 서버가 뜨지 않는다.
 */
@Component
public class DistrictFileAdapter implements DistrictCatalogPort {

  public static final String RESOURCE = "data/korea-districts.json";

  private final KoreanDistricts districts;

  public DistrictFileAdapter(JsonMapper json) {
    this.districts = new KoreanDistricts(load(json, RESOURCE).stream().map(Row::toDomain).toList());
  }

  @Override
  public KoreanDistricts districts() {
    return districts;
  }

  /** 자료 파일 모양: { version, source, districts: [...] } */
  public record FileData(String version, String source, List<Row> districts) {}

  /**
   * 자료 파일 한 줄.
   *
   * @param city 일반구의 상위 시, 그 밖은 null
   * @param approx 지오코딩으로 찾지 못해 상위 시·도 좌표를 쓴 곳
   * @param via 자료를 만들 때 좌표를 찾은 검색어와 결과 (사람이 확인하는 용도, 서버는 쓰지 않는다. 근사면 null)
   */
  public record Row(
      String name,
      String fullName,
      String city,
      String regionCode,
      double lat,
      double lon,
      boolean approx,
      String via) {

    public KoreanDistrict toDomain() {
      return new KoreanDistrict(name, fullName, city, regionCode, lat, lon, approx);
    }
  }

  /** 자료 파일의 줄들 (테스트가 형식을 확인할 때도 쓴다) */
  public static List<Row> load(JsonMapper json, String resource) {
    try (InputStream in = new ClassPathResource(resource).getInputStream()) {
      return json.readValue(in, FileData.class).districts();
    } catch (IOException e) {
      throw new UncheckedIOException("시·군·구 자료 파일을 읽지 못했어요: " + resource, e);
    }
  }
}
