package app.wearly.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 통합 테스트용 HTTP 도우미. 오류 응답도 예외 없이 상태 코드와 JSON으로 돌려준다. */
public class TestApi {

  public static final JsonMapper JSON = JsonMapper.builder().build();

  private final RestClient client;

  public TestApi(String baseUrl) {
    HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    this.client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(new JdkClientHttpRequestFactory(http))
            .defaultStatusHandler(status -> true, (req, res) -> {})
            .build();
  }

  public record Resp(int status, JsonNode body, HttpHeaders headers) {

    public String code() {
      return body == null ? null : body.path("code").asString(null);
    }

    public Resp expect(int expected) {
      assertThat(status).as("HTTP 상태 (본문: %s)", body).isEqualTo(expected);
      return this;
    }

    public Resp expectError(int expectedStatus, String expectedCode) {
      expect(expectedStatus);
      assertThat(code()).isEqualTo(expectedCode);
      assertThat(body.path("status").asInt()).isEqualTo(expectedStatus);
      assertThat(body.path("message").asString()).isNotBlank();
      assertThat(body.path("fieldErrors").isArray()).isTrue();
      return this;
    }
  }

  /** 가입한 회원의 토큰 묶음 */
  public record Session(String accessToken, String refreshToken, String userId, JsonNode auth) {}

  public Session signup(String email, String password, String name) {
    Resp r =
        post(
                "/api/v1/auth/signup",
                null,
                Map.of("email", email, "password", password, "name", name))
            .expect(201);
    return session(r);
  }

  public static Session session(Resp r) {
    return new Session(
        r.body().get("accessToken").asString(),
        r.body().get("refreshToken").asString(),
        r.body().get("user").get("id").asString(),
        r.body());
  }

  public Resp get(String path, String token) {
    return exchange(HttpMethod.GET, path, token, null);
  }

  /** URI 변수 값은 그대로 인코딩된다 (예: "%" → %25) */
  public Resp get(String pathTemplate, String token, Map<String, ?> uriVariables) {
    RestClient.RequestHeadersSpec<?> spec =
        client.get().uri(pathTemplate, uriVariables).accept(MediaType.APPLICATION_JSON);
    if (token != null) {
      spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return toResp(spec.retrieve().toEntity(String.class));
  }

  /** 헤더를 더해 GET (If-None-Match 등) */
  public Resp get(String path, String token, HttpHeaders headers) {
    RestClient.RequestHeadersSpec<?> spec =
        client.get().uri(path).accept(MediaType.APPLICATION_JSON).headers(h -> h.addAll(headers));
    if (token != null) {
      spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return toResp(spec.retrieve().toEntity(String.class));
  }

  public Resp post(String path, String token, Object body) {
    return exchange(HttpMethod.POST, path, token, body);
  }

  public Resp put(String path, String token, Object body) {
    return exchange(HttpMethod.PUT, path, token, body);
  }

  public Resp patch(String path, String token, Object body) {
    return exchange(HttpMethod.PATCH, path, token, body);
  }

  public Resp delete(String path, String token) {
    return exchange(HttpMethod.DELETE, path, token, null);
  }

  public Resp upload(
      String path,
      String token,
      String partName,
      byte[] bytes,
      String filename,
      String contentType) {
    HttpHeaders partHeaders = new HttpHeaders();
    if (contentType != null) {
      partHeaders.setContentType(MediaType.parseMediaType(contentType));
    }
    ByteArrayResource resource =
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return filename;
          }
        };
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add(partName, new HttpEntity<>(resource, partHeaders));
    RestClient.RequestBodySpec spec =
        client.post().uri(path).contentType(MediaType.MULTIPART_FORM_DATA);
    if (token != null) {
      spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return toResp(spec.body(form).retrieve().toEntity(String.class));
  }

  public Resp exchange(HttpMethod method, String path, String token, Object body) {
    RestClient.RequestBodySpec spec =
        client.method(method).uri(path).accept(MediaType.APPLICATION_JSON);
    if (token != null) {
      spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    if (body != null) {
      spec.contentType(MediaType.APPLICATION_JSON)
          .body(body instanceof String s ? s : JSON.writeValueAsString(body));
    }
    return toResp(spec.retrieve().toEntity(String.class));
  }

  public Resp options(String path, String origin, String requestMethod) {
    ResponseEntity<String> res =
        client
            .options()
            .uri(path)
            .header(HttpHeaders.ORIGIN, origin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, requestMethod)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
            .retrieve()
            .toEntity(String.class);
    return toResp(res);
  }

  private static Resp toResp(ResponseEntity<String> res) {
    String text = res.getBody();
    JsonNode node = text == null || text.isBlank() ? null : parse(text);
    return new Resp(res.getStatusCode().value(), node, res.getHeaders());
  }

  private static JsonNode parse(String text) {
    try {
      return JSON.readTree(text);
    } catch (RuntimeException e) {
      // JSON이 아닌 응답(예: CORS 거부)은 문자열 노드로 담는다
      return JSON.getNodeFactory().stringNode(text);
    }
  }
}
