package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.domain.AiProviderException;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * OpenAI·Gemini REST 호출 공통 부분 (Spring RestClient, SDK 없음). HTTP 오류도 예외 없이 상태와 본문을 돌려주고, 시간 초과·연결
 * 실패만 AiProviderException으로 바꾼다.
 */
public final class HttpCall {

  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  public record Result(int status, String body) {

    public boolean ok() {
      return status >= 200 && status < 300;
    }
  }

  private HttpCall() {}

  public static RestClient client(String baseUrl, Duration timeout) {
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .proxy(ProxySelector.getDefault())
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(timeout);
    return RestClient.builder().baseUrl(stripSlash(baseUrl)).requestFactory(factory).build();
  }

  public static Result postJson(
      RestClient client,
      String path,
      Map<String, String> headers,
      String body,
      String label,
      String apiKey) {
    try {
      return client
          .post()
          .uri(path)
          .headers(h -> headers.forEach(h::set))
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(body)
          .exchange(
              (req, res) ->
                  new Result(
                      res.getStatusCode().value(),
                      new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    } catch (RestClientException e) {
      if (isTimeout(e)) {
        throw new AiProviderException("TIMEOUT", label + " 응답 시간 초과");
      }
      throw new AiProviderException(
          "API_CLIENT", label + " 호출 실패: " + Secrets.scrub(rootMessage(e), apiKey));
    }
  }

  /** GET (모델 목록 조회 등). 오류 처리는 postJson과 같다 */
  public static Result get(
      RestClient client, String path, Map<String, String> headers, String label, String apiKey) {
    return get(client, path, Map.of(), headers, label, apiKey);
  }

  /**
   * GET. path는 URI 템플릿이고 {이름} 자리에 uriVariables 값을 인코딩해 넣는다 (페이지 토큰처럼 =·+·/가 섞일 수 있는 값). 오류 처리는
   * postJson과 같다
   */
  public static Result get(
      RestClient client,
      String path,
      Map<String, ?> uriVariables,
      Map<String, String> headers,
      String label,
      String apiKey) {
    try {
      return client
          .get()
          .uri(path, uriVariables)
          .headers(h -> headers.forEach(h::set))
          .accept(MediaType.APPLICATION_JSON)
          .exchange(
              (req, res) ->
                  new Result(
                      res.getStatusCode().value(),
                      new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    } catch (RestClientException e) {
      if (isTimeout(e)) {
        throw new AiProviderException("TIMEOUT", label + " 응답 시간 초과");
      }
      throw new AiProviderException(
          "API_CLIENT", label + " 호출 실패: " + Secrets.scrub(rootMessage(e), apiKey));
    }
  }

  /** 숫자 필드면 long, 없거나 숫자가 아니면 null */
  public static Long longOrNull(JsonNode node, String field) {
    JsonNode v = node.get(field);
    return v != null && v.isNumber() ? v.asLong() : null;
  }

  static String stripSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static boolean isTimeout(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof HttpTimeoutException || t instanceof java.net.SocketTimeoutException) {
        return true;
      }
    }
    return false;
  }

  private static String rootMessage(Throwable e) {
    Throwable t = e;
    while (t.getCause() != null && t.getCause() != t) {
      t = t.getCause();
    }
    return t instanceof IOException || t.getMessage() == null
        ? t.getClass().getSimpleName() + ": " + t.getMessage()
        : t.getMessage();
  }
}
