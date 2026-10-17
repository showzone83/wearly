package app.wearly.support;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL로 띄우는 통합 테스트의 기반.
 * - 기본: Testcontainers로 pgvector/pgvector:pg17 컨테이너를 한 번 띄워 모든 테스트 클래스가 함께 쓴다.
 * - 환경변수 WEARLY_TEST_DB_URL이 있으면 컨테이너 대신 그 DB를 쓴다
 *   (WEARLY_TEST_DB_USERNAME / WEARLY_TEST_DB_PASSWORD, 기본 wearly/wearly). 테스트 데이터는 매번 새 이메일로 만든다.
 * 서버는 무작위 포트로 띄우고 실제 HTTP로 부른다 (Security 필터·multipart 한도까지 확인).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "wearly.ai.provider=mock",
        "wearly.ai.daily-limit=3",
        "wearly.admin.email=" + IntegrationTestBase.BOOT_ADMIN_EMAIL,
        "wearly.admin.password=" + IntegrationTestBase.BOOT_ADMIN_PASSWORD,
        "wearly.admin.name=테스트관리자",
        "spring.config.import=",
})
public abstract class IntegrationTestBase {

    static final String EXTERNAL_URL = System.getenv("WEARLY_TEST_DB_URL");

    /** 시작 시 만들어지는 관리자 계정 (WEARLY_ADMIN_*) */
    public static final String BOOT_ADMIN_EMAIL = "it-admin@wearly.test";
    public static final String BOOT_ADMIN_PASSWORD = "admin1234";

    static final PostgreSQLContainer POSTGRES;

    static {
        if (EXTERNAL_URL == null || EXTERNAL_URL.isBlank()) {
            POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
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

    @Autowired
    protected JdbcTemplate jdbcTemplate;

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

    /** 새 회원을 만들고 토큰이 든 세션을 돌려준다. */
    protected TestApi.Session signup() {
        return api().signup(uniqueEmail(), "pass1234", "테스터");
    }

    /** 새 회원을 만들고 DB에서 ADMIN으로 올린다 (권한은 요청마다 DB 역할로 판단하므로 토큰을 다시 받을 필요 없다) */
    protected TestApi.Session adminSession() {
        TestApi.Session s = signup();
        jdbcTemplate.update("update tb_user set role = 'ADMIN' where user_id = ?", Long.parseLong(s.userId()));
        return s;
    }

    /** 코드·공급자 ID 충돌을 피하는 짧은 무작위 접미사 (대문자) */
    protected static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private static String envOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
