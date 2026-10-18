package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.PlaceCandidate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** 장소 검색 결과 캐시 (Valkey). 키는 정리한 검색어 (소문자, 공백 하나). 결과가 없던 검색어도 빈 목록으로 둔다 */
public interface PlaceSearchCachePort {

  Optional<List<PlaceCandidate>> find(String query);

  void save(String query, List<PlaceCandidate> items, Duration ttl);
}
