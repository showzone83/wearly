package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.KoreanDistricts;

/**
 * 전국 시·군·구 자료 (19 문서, 장소 검색). 서버에 함께 넣은 자료 파일(resources/data/korea-districts.json)을 읽는다. 실행 중에 외부를
 * 부르지 않는다.
 */
public interface DistrictCatalogPort {

  KoreanDistricts districts();
}
