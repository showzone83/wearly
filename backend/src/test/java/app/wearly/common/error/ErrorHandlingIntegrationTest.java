package app.wearly.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import jakarta.servlet.Filter;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 오류 처리 규칙: 응답에는 카탈로그 코드·params·traceId만 나가고 예외 메시지·SQL·클래스 이름은 나가지 않는다. 자세한 내용은 같은 traceId로 로그에서
 * 찾는다. X-Request-Id는 형식이 맞으면 그대로, 아니면 새로 만든다.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import({
  ErrorHandlingIntegrationTest.BoomController.class,
  ErrorHandlingIntegrationTest.Boom.class
})
class ErrorHandlingIntegrationTest extends IntegrationTestBase {

  static final String SECRET = "password=s3cr3t SELECT * FROM tb_user";
  static final Pattern GENERATED = Pattern.compile("^[0-9a-f]{32}$");

  /** 일부러 예외를 던지는 테스트 전용 API (테스트 클래스 안이라 다른 테스트의 컴포넌트 스캔에는 잡히지 않는다) */
  @RestController
  static class BoomController {

    @GetMapping("/api/v1/test-errors/unexpected")
    String unexpected() {
      throw new IllegalStateException("예상 못 한 오류 " + SECRET);
    }

    @GetMapping("/api/v1/test-errors/db-down")
    String dbDown() {
      throw new DataAccessResourceFailureException("Connection to localhost:5433 refused");
    }

    @GetMapping("/api/v1/test-errors/unique")
    String unique() {
      throw new DataIntegrityViolationException(
          "could not execute statement",
          new SQLException("duplicate key value violates unique constraint uk_secret", "23505"));
    }

    @GetMapping("/api/v1/test-errors/not-null")
    String notNull() {
      throw new DataIntegrityViolationException(
          "could not execute statement",
          new SQLException("null value in column secret_col", "23502"));
    }
  }

  /** 컨트롤러 밖(서블릿 필터)에서 나는 예외 → /error */
  @TestConfiguration
  static class Boom {

    @Bean
    FilterRegistrationBean<Filter> boomFilter() {
      FilterRegistrationBean<Filter> bean =
          new FilterRegistrationBean<>(
              (req, res, chain) -> {
                throw new IllegalStateException("필터 오류 " + SECRET);
              });
      bean.addUrlPatterns("/api/v1/test-errors/filter");
      bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
      return bean;
    }
  }

  @Test
  void 예상치_못한_예외는_500_고정_코드와_traceId만_주고_로그에는_원문과_같은_traceId(CapturedOutput output) {
    Session s = signup();
    Resp r =
        api()
            .get("/api/v1/test-errors/unexpected", s.accessToken())
            .expectError(500, "INTERNAL_ERROR");
    assertOnlyContract(r);
    String traceId = r.body().get("traceId").asString();
    assertThat(traceId).matches(GENERATED);
    assertThat(output.getOut())
        .contains("traceId=" + traceId)
        .contains("예상 못 한 오류 " + SECRET)
        .contains("userId=" + s.userId());
    assertThat(lineWith(output.getOut(), "시스템 오류 code=INTERNAL_ERROR")).contains(traceId);
  }

  @Test
  void DB_연결_장애는_503이고_DB_주소는_응답에_없다(CapturedOutput output) {
    Session s = signup();
    Resp r =
        api()
            .get("/api/v1/test-errors/db-down", s.accessToken())
            .expectError(503, "SERVICE_UNAVAILABLE");
    assertOnlyContract(r);
    assertThat(r.body().toString()).doesNotContain("5433");
    assertThat(output.getOut())
        .contains("Connection to localhost:5433 refused")
        .contains("traceId=" + r.body().get("traceId").asString());
  }

  @Test
  void 고유_제약_위반은_409_CONFLICT_그_밖의_무결성_위반은_500() {
    Session s = signup();
    Resp unique =
        api().get("/api/v1/test-errors/unique", s.accessToken()).expectError(409, "CONFLICT");
    assertOnlyContract(unique);
    Resp notNull =
        api()
            .get("/api/v1/test-errors/not-null", s.accessToken())
            .expectError(500, "INTERNAL_ERROR");
    assertOnlyContract(notNull);
  }

  @Test
  void 필터에서_난_예외도_error_경로에서_같은_형식과_traceId로_준다(CapturedOutput output) {
    HttpHeaders h = new HttpHeaders();
    h.set("X-Request-Id", "filter-trace-0001");
    Resp r = api().exchange(HttpMethod.GET, "/api/v1/test-errors/filter", null, null, h);
    r.expectError(500, "INTERNAL_ERROR");
    assertOnlyContract(r);
    assertThat(r.body().get("traceId").asString()).isEqualTo("filter-trace-0001");
    assertThat(output.getOut()).contains("traceId=filter-trace-0001").contains("필터 오류 " + SECRET);
  }

  @Test
  void 잘못된_JSON은_400_body_INVALID_FORMAT이고_Jackson_원문이_없다(CapturedOutput output) {
    Resp r =
        api()
            .post("/api/v1/auth/signup", null, "{\"email\": \"a@b.c\", \"password\": ")
            .expectError(400, "VALIDATION_FAILED");
    assertOnlyContract(r);
    assertThat(fieldCodes(r)).containsExactly("body=INVALID_FORMAT");
    assertThat(r.body().get("fieldErrors").get(0).get("params").get("format").asString())
        .isEqualTo("json");
    // 로그에는 예외 종류만 남는다 (원문에 요청 값이 섞일 수 있다)
    assertThat(lineWith(output.getOut(), "요청 오류 code=VALIDATION_FAILED"))
        .contains("HttpMessageNotReadableException")
        .contains("traceId=" + r.body().get("traceId").asString());
  }

  @Test
  void 입력_검증_실패_로그에_입력한_값이_남지_않는다(CapturedOutput output) {
    String password = "pw" + SECRET;
    Resp r =
        api()
            .post(
                "/api/v1/auth/signup",
                null,
                "{\"email\": \"not-an-email\", \"password\": \"" + password + "\", \"name\": \"\"}")
            .expectError(400, "VALIDATION_FAILED");
    String line = lineWith(output.getOut(), "입력 오류 code=VALIDATION_FAILED");
    assertThat(line).contains("traceId=" + r.body().get("traceId").asString()).contains("email:");
    assertThat(output.getOut()).doesNotContain(password).doesNotContain("not-an-email");
  }

  @Test
  void 빈_본문은_body_REQUIRED() {
    Resp r =
        api()
            .exchange(HttpMethod.POST, "/api/v1/auth/login", null, "", new HttpHeaders())
            .expectError(400, "VALIDATION_FAILED");
    assertOnlyContract(r);
    assertThat(fieldCodes(r)).containsExactly("body=REQUIRED");
  }

  @Test
  void 타입이_틀린_값은_400_INVALID_FORMAT이고_클래스_이름이_없다() {
    Session s = signup();
    Map<String, Object> item = new java.util.HashMap<>();
    item.put("name", "셔츠");
    item.put("category", "TOP");
    item.put("subType", "SHIRT");
    item.put("color", "white");
    item.put("pattern", "SOLID");
    item.put("seasons", List.of("SPRING"));
    item.put("styles", List.of("CASUAL"));
    item.put("formality", "격식");
    item.put("status", "OWNED");
    Resp body =
        api()
            .post("/api/v1/wardrobe/items", s.accessToken(), item)
            .expectError(400, "VALIDATION_FAILED");
    assertOnlyContract(body);
    assertThat(fieldCodes(body)).containsExactly("formality=INVALID_FORMAT");

    Session admin = admin();
    Resp query =
        api()
            .get("/api/v1/admin/users?page=abc", admin.accessToken())
            .expectError(400, "VALIDATION_FAILED");
    assertOnlyContract(query);
    assertThat(fieldCodes(query)).containsExactly("page=INVALID_FORMAT");
  }

  @Test
  void 검증_애너테이션은_코드와_기준값_params로_준다() {
    Resp r =
        api()
            .post(
                "/api/v1/auth/signup",
                null,
                Map.of("email", "not-an-email", "password", "short", "name", "가".repeat(21)))
            .expectError(400, "VALIDATION_FAILED");
    assertOnlyContract(r);
    assertThat(fieldCodes(r))
        .containsExactlyInAnyOrder(
            "email=INVALID_FORMAT", "password=INVALID_FORMAT", "name=TOO_LONG");
    JsonNode name = field(r, "name");
    assertThat(name.get("params").get("max").asInt()).isEqualTo(20);
  }

  @Test
  void X_Request_Id는_형식이_맞으면_그대로_아니면_새로_만든다() {
    HttpHeaders ok = new HttpHeaders();
    ok.set("X-Request-Id", "app-req_1234.abcd");
    Resp given = api().get("/api/v1/users/me", null, ok).expectError(401, "UNAUTHORIZED");
    assertThat(given.headers().getFirst("X-Request-Id")).isEqualTo("app-req_1234.abcd");
    assertThat(given.body().get("traceId").asString()).isEqualTo("app-req_1234.abcd");

    for (String bad : List.of("short", "has space 123456", "x".repeat(65), "inject/%0d%0a/x")) {
      HttpHeaders h = new HttpHeaders();
      h.set("X-Request-Id", bad);
      Resp r = api().get("/api/v1/users/me", null, h).expectError(401, "UNAUTHORIZED");
      assertThat(r.body().get("traceId").asString()).matches(GENERATED);
    }

    // 헤더가 없으면 만든다. 성공 응답에도 붙는다
    Resp generated = api().get("/api/v1/codes", null);
    generated.expect(200);
    assertThat(generated.headers().getFirst("X-Request-Id")).matches(GENERATED);
  }

  @Test
  void 시큐리티_401_403에도_traceId가_있다(CapturedOutput output) {
    Resp noToken = api().get("/api/v1/users/me", null).expectError(401, "UNAUTHORIZED");
    assertOnlyContract(noToken);
    Resp badToken = api().get("/api/v1/users/me", "not-a-jwt").expectError(401, "TOKEN_INVALID");
    assertOnlyContract(badToken);

    Session s = signup();
    Resp forbidden =
        api().get("/api/v1/admin/users", s.accessToken()).expectError(403, "FORBIDDEN");
    assertOnlyContract(forbidden);
    assertThat(lineWith(output.getOut(), "인증·권한 오류 code=FORBIDDEN"))
        .contains("traceId=" + forbidden.body().get("traceId").asString())
        .contains("userId=" + s.userId());
  }

  @Test
  void 업무_오류는_카탈로그_코드와_params만_준다() {
    String email = uniqueEmail();
    api().signup(email, "pass1234", "테스터");
    Resp dup =
        api()
            .post(
                "/api/v1/auth/signup",
                null,
                Map.of("email", email, "password", "pass1234", "name", "테스터"))
            .expectError(409, "EMAIL_TAKEN");
    assertOnlyContract(dup);
    assertThat(dup.body().has("params")).isFalse();

    Resp notFound =
        api()
            .get("/api/v1/wardrobe/items/999999999", signup().accessToken())
            .expectError(404, "NOT_FOUND");
    assertOnlyContract(notFound);
    Resp noRoute = api().get("/api/v1/nothing-here", signup().accessToken());
    noRoute.expectError(404, "NOT_FOUND");
    assertOnlyContract(noRoute);
  }

  /** 오류 본문은 계약 필드만 있고, 서버 내부 정보로 보이는 문자열이 없다 */
  private static void assertOnlyContract(Resp r) {
    List<String> keys = new java.util.ArrayList<>();
    r.body().propertyNames().forEach(keys::add);
    assertThat(keys).isSubsetOf("status", "code", "params", "fieldErrors", "traceId");
    assertThat(keys).contains("status", "code", "fieldErrors", "traceId");
    String text = r.body().toString();
    assertThat(text)
        .doesNotContain(SECRET)
        .doesNotContain("Exception")
        .doesNotContain("jackson")
        .doesNotContain("java.")
        .doesNotContain("app.wearly")
        .doesNotContain("SQL")
        .doesNotContain("secret");
  }

  private static List<String> fieldCodes(Resp r) {
    List<String> out = new java.util.ArrayList<>();
    r.body()
        .get("fieldErrors")
        .forEach(f -> out.add(f.get("field").asString() + "=" + f.get("code").asString()));
    return out;
  }

  private static JsonNode field(Resp r, String name) {
    for (JsonNode f : r.body().get("fieldErrors")) {
      if (f.get("field").asString().equals(name)) {
        return f;
      }
    }
    throw new AssertionError("필드 오류 없음: " + name);
  }

  private static String lineWith(String out, String marker) {
    return out.lines().filter(l -> l.contains(marker)).reduce((a, b) -> b).orElse("");
  }
}
