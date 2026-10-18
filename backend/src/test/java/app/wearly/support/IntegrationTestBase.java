package app.wearly.support;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL·Valkey(TestValkey, 로그인 세션)·MinIO(TestMinio, 옷 사진)로 띄우는 통합 테스트의 기반. - 기본:
 * Testcontainers로 pgvector/pgvector:pg17 컨테이너를 한 번 띄워 모든 테스트 클래스가 함께 쓴다. - 환경변수 WEARLY_TEST_DB_URL이
 * 있으면 컨테이너 대신 그 DB를 쓴다 (WEARLY_TEST_DB_USERNAME / WEARLY_TEST_DB_PASSWORD, 기본 wearly/wearly). 테스트
 * 데이터는 매번 새 이메일로 만든다. 서버는 무작위 포트로 띄우고 실제 HTTP로 부른다 (Security 필터·multipart 한도까지 확인).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "wearly.ai.provider=mock",
      "wearly.ai.daily-limit=3",
      "wearly.ai.outfit-daily-limit=3",
      "wearly.ai.allow-base-url=true",
      "spring.config.import=",
      "wearly.admin.email=" + IntegrationTestBase.ADMIN_EMAIL,
      "wearly.admin.password=" + IntegrationTestBase.ADMIN_PASSWORD,
      "wearly.admin.name=테스트관리자",
      // 개발자 PC의 실제 키가 테스트에 섞이지 않게 비워 둔다. 테스트용 가짜 키는 TEST_*_API_KEY
      "ANTHROPIC_API_KEY=",
      "OPENAI_API_KEY=",
      "GEMINI_API_KEY=",
      "TEST_CRUD_API_KEY=test-crud-key-value",
      // 웹 키 마스터 키도 개발자 PC 값이 섞이지 않게 비워 둔다 (켜는 테스트는 따로 넣는다)
      "WEARLY_SECRET_KEY=",
    })
public abstract class IntegrationTestBase {

  public static final String ADMIN_EMAIL = "admin@wearly.test";
  public static final String ADMIN_PASSWORD = "admin1234";

  static final String EXTERNAL_URL = System.getenv("WEARLY_TEST_DB_URL");

  static final PostgreSQLContainer POSTGRES;

  static {
    if (EXTERNAL_URL == null || EXTERNAL_URL.isBlank()) {
      POSTGRES =
          new PostgreSQLContainer(
                  DockerImageName.parse("pgvector/pgvector:pg17")
                      .asCompatibleSubstituteFor("postgres"))
              .withDatabaseName("wearly_test")
              .withUsername("wearly")
              .withPassword("wearly");
      POSTGRES.start();
    } else {
      POSTGRES = null;
    }
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    TestValkey.register(registry, 0);
    TestMinio.register(registry);
    if (POSTGRES != null) {
      registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
      registry.add("spring.datasource.username", POSTGRES::getUsername);
      registry.add("spring.datasource.password", POSTGRES::getPassword);
    } else {
      registry.add("spring.datasource.url", () -> EXTERNAL_URL);
      registry.add("spring.datasource.username", () -> envOr("WEARLY_TEST_DB_USERNAME", "wearly"));
      registry.add("spring.datasource.password", () -> envOr("WEARLY_TEST_DB_PASSWORD", "wearly"));
    }
  }

  @Value("${local.server.port}")
  protected int port;

  private TestApi api;

  protected TestApi api() {
    if (api == null) {
      api = new TestApi("http://localhost:" + port);
    }
    return api;
  }

  protected static String uniqueEmail() {
    return "user-" + UUID.randomUUID().toString().substring(0, 12) + "@wearly.test";
  }

  /** 시작 시 만들어진 관리자(WEARLY_ADMIN_*)로 로그인한다. */
  protected TestApi.Session admin() {
    return TestApi.session(
        api()
            .post(
                "/api/v1/auth/login",
                null,
                java.util.Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
            .expect(200));
  }

  /** 새 회원을 만들고 토큰이 든 세션을 돌려준다. */
  protected TestApi.Session signup() {
    return api().signup(uniqueEmail(), "pass1234", "테스터");
  }

  private static String envOr(String key, String fallback) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? fallback : value;
  }
}
