package app.wearly.support;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 사진 저장소 (S3 호환 MinIO, 17 문서). docker-compose.yml의 minio 서비스와 같은 이미지 컨테이너를 한 번 띄워 모든 테스트가
 * 함께 쓴다. 버킷은 서버가 시작할 때 만든다(auto-create-bucket). 서명 URL은 요청 Host(localhost) + 매핑된 포트로 만들어지므로 테스트가 실제
 * HTTP로 PUT·GET할 수 있다.
 */
public final class TestMinio {

  /** docker-compose.yml의 minio 서비스와 같은 이미지 (MinIO 커뮤니티 포크, compose 주석 참고) */
  public static final String IMAGE = "pgsty/minio:RELEASE.2026-08-04T00-00-00Z";

  public static final String ACCESS_KEY = "wearly-test";
  public static final String SECRET_KEY = "wearly-test-secret";
  public static final String BUCKET = "wearly-images-test";

  @SuppressWarnings("resource")
  static final GenericContainer<?> CONTAINER =
      new GenericContainer<>(DockerImageName.parse(IMAGE))
          .withCommand("server", "/data")
          .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
          .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
          .withExposedPorts(9000)
          .waitingFor(
              Wait.forHttp("/minio/health/live")
                  .forPort(9000)
                  .withStartupTimeout(Duration.ofSeconds(60)));

  static {
    CONTAINER.start();
  }

  private TestMinio() {}

  /** 백엔드가 붙는 주소 (테스트 JVM → 컨테이너) */
  public static String endpoint() {
    return "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(9000);
  }

  public static void register(DynamicPropertyRegistry registry) {
    registry.add("wearly.storage.endpoint", TestMinio::endpoint);
    registry.add("wearly.storage.access-key", () -> ACCESS_KEY);
    registry.add("wearly.storage.secret-key", () -> SECRET_KEY);
    registry.add("wearly.storage.bucket", () -> BUCKET);
    registry.add("wearly.storage.auto-create-bucket", () -> true);
    registry.add("wearly.storage.public-endpoint", () -> "");
    // 정리 작업은 테스트가 유스케이스를 직접 부른다
    registry.add("wearly.storage.cleanup-enabled", () -> false);
  }
}
