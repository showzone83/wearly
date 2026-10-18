package app.wearly.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 프롬프트 캐싱용 검사: JSON 스키마의 모든 객체가 넣은 순서를 지키는 맵인지 본다. Map.of는 JVM을 다시 띄우면 순서가 달라질 수 있어 (키가 두 개 이상이면)
 * 직렬화한 바이트가 서버마다 달라지고 캐시가 깨진다.
 */
public final class OrderedMaps {

  private OrderedMaps() {}

  public static void assertOrdered(Object node, String path) {
    if (node instanceof Map<?, ?> map) {
      if (map.size() > 1) {
        assertThat(map).as("순서가 고정되지 않은 맵: " + path).isInstanceOf(LinkedHashMap.class);
      }
      map.forEach((k, v) -> assertOrdered(v, path + "." + k));
    } else if (node instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        assertOrdered(list.get(i), path + "[" + i + "]");
      }
    }
  }
}
