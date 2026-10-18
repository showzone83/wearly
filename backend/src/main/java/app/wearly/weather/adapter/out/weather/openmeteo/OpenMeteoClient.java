package app.wearly.weather.adapter.out.weather.openmeteo;

import app.wearly.weather.application.port.out.ClimateSourcePort;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.ClimateFetch;
import app.wearly.weather.domain.DailyObservation;
import app.wearly.weather.domain.FetchErrors;
import java.net.ProxySelector;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Open-Meteo 과거 기상 API (19 문서 4장). 키 없이 부르는 무료 API는 비상업용이다. 상업 서비스에서는 유료 플랜 주소(customer-…)와 키를
 * 설정(wearly.weather.climate.base-url·api-key)에 넣는다.
 *
 * <pre>
 * GET {base}/v1/archive?latitude=..&amp;longitude=..
 *     &amp;start_date=YYYY-MM-DD&amp;end_date=YYYY-MM-DD
 *     &amp;daily=temperature_2m_max,temperature_2m_min,precipitation_sum
 *     &amp;timezone=auto[&amp;apikey=…]
 * </pre>
 *
 * <p>timezone=auto: 일별 값을 그 장소의 현지 날짜로 묶는다 (해외 장소도 하루 경계가 맞게). 응답 daily는 time·temperature_2m_max·
 * temperature_2m_min·precipitation_sum 배열이고, 자료가 없는 날은 null이다. 오류 응답은 {"error": true, "reason":
 * "…"}.
 */
public final class OpenMeteoClient {

  static final String LABEL = "Open-Meteo";
  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  static final int MAX_MESSAGE = 200;

  private final RestClient client;
  private final String apiKey;
  private final JsonMapper json;
  private final Clock clock;

  /**
   * @param apiKey 유료 플랜 키. 비우면 무료 API로 부른다
   */
  public OpenMeteoClient(
      String baseUrl, String apiKey, Duration timeout, JsonMapper json, Clock clock) {
    this.client = restClient(baseUrl, timeout);
    this.apiKey = blankToNull(apiKey);
    this.json = json;
    this.clock = clock;
  }

  /** 지오코딩 클라이언트와 같이 쓰는 HTTP 설정 (HTTP/1.1, 연결 5초, 읽기 timeout) */
  static RestClient restClient(String baseUrl, Duration timeout) {
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .proxy(ProxySelector.getDefault())
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(timeout);
    String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return RestClient.builder().baseUrl(base).requestFactory(factory).build();
  }

  static String blankToNull(String apiKey) {
    return apiKey == null || apiKey.isBlank() ? null : apiKey.strip();
  }

  record Response(int status, String body) {}

  public ClimateFetch fetch(String target, double lat, double lon, LocalDate from, LocalDate to) {
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("lat", lat);
    vars.put("lon", lon);
    vars.put("from", from.toString());
    vars.put("to", to.toString());
    String uri =
        "/v1/archive?latitude={lat}&longitude={lon}&start_date={from}&end_date={to}"
            + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=auto";
    if (apiKey != null) {
      uri += "&apikey={key}";
      vars.put("key", apiKey);
    }
    final Instant calledAt = clock.instant();
    final long started = System.nanoTime();
    Response res;
    try {
      res =
          client
              .get()
              .uri(uri, vars)
              .accept(MediaType.APPLICATION_JSON)
              .exchange(
                  (req, r) ->
                      new Response(
                          r.getStatusCode().value(),
                          new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    } catch (RestClientException e) {
      long ms = elapsed(started);
      boolean timeout = isTimeout(e);
      String code = timeout ? FetchErrors.TIMEOUT : FetchErrors.CLIENT;
      String message = timeout ? LABEL + " 응답 시간 초과" : LABEL + " 호출 실패: " + scrub(rootMessage(e));
      return ClimateFetch.failed(
          code, message, List.of(call(target, null, false, code, message, ms, null, calledAt)));
    }
    long ms = elapsed(started);
    int bytes = res.body().getBytes(StandardCharsets.UTF_8).length;
    if (res.status() < 200 || res.status() >= 300) {
      String code = FetchErrors.ofStatus(res.status());
      String reason = "";
      try {
        reason = json.readTree(res.body()).path("reason").asString("");
      } catch (RuntimeException ignored) {
        // 본문이 JSON이 아니면 상태 코드만 쓴다
      }
      String message =
          LABEL
              + " API 오류 (HTTP "
              + res.status()
              + ")"
              + (reason.isBlank() ? "" : ": " + scrub(reason));
      return ClimateFetch.failed(
          code,
          message,
          List.of(call(target, res.status(), false, code, message, ms, bytes, calledAt)));
    }
    try {
      List<DailyObservation> days = parse(res.body());
      return ClimateFetch.success(
          days, List.of(call(target, res.status(), true, null, null, ms, bytes, calledAt)));
    } catch (RuntimeException e) {
      String message = LABEL + " 응답을 읽지 못했어요: " + e.getClass().getSimpleName();
      return ClimateFetch.failed(
          FetchErrors.PARSE,
          message,
          List.of(
              call(target, res.status(), false, FetchErrors.PARSE, message, ms, bytes, calledAt)));
    }
  }

  /** daily 배열 → 하루 관측값 */
  List<DailyObservation> parse(String body) {
    JsonNode daily = json.readTree(body).required("daily");
    JsonNode time = daily.required("time");
    JsonNode max = daily.required("temperature_2m_max");
    JsonNode min = daily.required("temperature_2m_min");
    JsonNode rain = daily.required("precipitation_sum");
    List<DailyObservation> out = new ArrayList<>();
    for (int i = 0; i < time.size(); i++) {
      out.add(
          new DailyObservation(
              LocalDate.parse(time.get(i).asString()),
              number(max.get(i)),
              number(min.get(i)),
              number(rain.get(i))));
    }
    if (out.isEmpty()) {
      throw new IllegalStateException("daily.time 없음");
    }
    return out;
  }

  private static Double number(JsonNode n) {
    return n != null && n.isNumber() ? n.asDouble() : null;
  }

  private static CallRecord call(
      String target,
      Integer status,
      boolean success,
      String errorCode,
      String message,
      long ms,
      Integer bytes,
      Instant at) {
    return new CallRecord(
        target,
        ClimateSourcePort.PROVIDER_CODE,
        CallRecord.CLIMATE,
        false,
        status,
        success,
        errorCode,
        message,
        ms,
        bytes,
        at);
  }

  /** 유료 키가 쿼리 파라미터라서 오류 문구에서 지운다 */
  String scrub(String message) {
    return scrub(apiKey, message);
  }

  static String scrub(String apiKey, String message) {
    if (message == null) {
      return "";
    }
    String out = message;
    if (apiKey != null) {
      out = out.replace(apiKey, "***");
    }
    out = out.replaceAll("(?i)(apikey=)[^&\\s\"']+", "$1***");
    return out.length() > MAX_MESSAGE ? out.substring(0, MAX_MESSAGE) + "…" : out;
  }

  static long elapsed(long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
  }

  static boolean isTimeout(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
        return true;
      }
    }
    return false;
  }

  static String rootMessage(Throwable e) {
    Throwable t = e;
    while (t.getCause() != null && t.getCause() != t) {
      t = t.getCause();
    }
    return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
  }
}
