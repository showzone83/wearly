package app.wearly.support;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 Valkey (로그인 세션 저장소, 16 문서). 기본은 compose와 같은 이미지(valkey/valkey:9.1) 컨테이너를 한 번 띄워 모든 테스트가
 * 함께 쓴다. 환경변수 WEARLY_TEST_VALKEY_HOST(·WEARLY_TEST_VALKEY_PORT, 기본 6379)가 있으면 그 서버를 쓴다. 테스트마다 DB
 * 번호를 나눠 쓸 수 있다(스냅샷 테스트는 빈 DB가 필요하다).
 */
public final class TestValkey {

  /** docker-compose.yml의 valkey 서비스와 같은 이미지 */
  public static final String IMAGE = "valkey/valkey:9.1";

  static final String EXTERNAL_HOST = System.getenv("WEARLY_TEST_VALKEY_HOST");

  @SuppressWarnings("resource")
  static final GenericContainer<?> CONTAINER;

  static {
    if (EXTERNAL_HOST == null || EXTERNAL_HOST.isBlank()) {
      CONTAINER = new GenericContainer<>(DockerImageName.parse(IMAGE)).withExposedPorts(6379);
      CONTAINER.start();
    } else {
      CONTAINER = null;
    }
  }

  private TestValkey() {}

  public static String host() {
    return CONTAINER != null ? CONTAINER.getHost() : EXTERNAL_HOST;
  }

  public static int port() {
    if (CONTAINER != null) {
      return CONTAINER.getMappedPort(6379);
    }
    String p = System.getenv("WEARLY_TEST_VALKEY_PORT");
    return p == null || p.isBlank() ? 6379 : Integer.parseInt(p);
  }

  public static void register(DynamicPropertyRegistry registry, int database) {
    registry.add("spring.data.redis.host", TestValkey::host);
    registry.add("spring.data.redis.port", TestValkey::port);
    registry.add("spring.data.redis.database", () -> database);
  }

  /** 그 DB 번호만 비운다 */
  public static void flush(int database) {
    RedisClient client =
        RedisClient.create(RedisURI.builder().withHost(host()).withPort(port()).build());
    try (StatefulRedisConnection<String, String> conn = client.connect()) {
      conn.sync().select(database);
      conn.sync().flushdb();
    } finally {
      client.shutdown();
    }
  }
}
