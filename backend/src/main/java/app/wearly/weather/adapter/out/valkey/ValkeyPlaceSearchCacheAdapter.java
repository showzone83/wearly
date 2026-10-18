package app.wearly.weather.adapter.out.valkey;

import app.wearly.weather.application.port.out.PlaceSearchCachePort;
import app.wearly.weather.domain.PlaceCandidate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PlaceSearchCachePort 구현 (Valkey). 키 wearly:weather:search:&lt;정리한 검색어&gt; = [{label, region,
 * country, lat, lon}] JSON, 만료는 search-cache-ttl.
 */
@Component
class ValkeyPlaceSearchCacheAdapter implements PlaceSearchCachePort {

  private static final Logger log = LoggerFactory.getLogger(ValkeyPlaceSearchCacheAdapter.class);

  static final String PREFIX = "wearly:weather:search:";

  private final StringRedisTemplate redis;
  private final JsonMapper json;

  ValkeyPlaceSearchCacheAdapter(StringRedisTemplate redis, JsonMapper json) {
    this.redis = redis;
    this.json = json;
  }

  @Override
  public Optional<List<PlaceCandidate>> find(String query) {
    String v = redis.opsForValue().get(PREFIX + query);
    if (v == null) {
      return Optional.empty();
    }
    try {
      List<PlaceCandidate> out = new ArrayList<>();
      for (JsonNode n : json.readTree(v)) {
        out.add(
            new PlaceCandidate(
                n.path("label").asString(""),
                n.path("region").asString(""),
                n.path("country").asString(""),
                n.path("lat").asDouble(),
                n.path("lon").asDouble()));
      }
      return Optional.of(out);
    } catch (RuntimeException e) {
      // 모양이 다르면 없는 것으로 보고 다시 검색한다
      log.warn("장소 검색 캐시를 읽지 못했어요 ({})", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  @Override
  public void save(String query, List<PlaceCandidate> items, Duration ttl) {
    List<Map<String, Object>> rows =
        items.stream()
            .map(
                c -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("label", c.label());
                  m.put("region", c.region());
                  m.put("country", c.country());
                  m.put("lat", c.lat());
                  m.put("lon", c.lon());
                  return m;
                })
            .toList();
    redis.opsForValue().set(PREFIX + query, json.writeValueAsString(rows), ttl);
  }
}
