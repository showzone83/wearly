package app.wearly.weather.adapter.out.weather.openmeteo;

import app.wearly.weather.application.port.out.GeocodingPort;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.GeoPlace;
import app.wearly.weather.domain.GeocodeResult;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Open-Meteo 지오코딩 API (19 문서, 장소 검색). 키 없이 부르는 무료 API는 비상업용이다 (평년값과 같은 조건). 상업 서비스에서는 유료 플랜
 * 주소(customer-geocoding-api.open-meteo.com)와 키를 설정(wearly.weather.geocoding.base-url·api-key)에 넣는다.
 *
 * <pre>
 * GET {base}/v1/search?name={검색어}&amp;language=ko&amp;count=10&amp;format=json[&amp;apikey=…]
 * </pre>
 *
 * <p>language=ko: 한글 검색어로 찾고 이름·시도·나라를 한글로 준다 (도쿄 → "도쿄 | 도쿄 도 | 일본"). 응답 results 항목은
 * name·latitude· longitude·country·country_code·admin1·feature_code·population 등이고, 결과가 없으면 results
 * 자체가 없다. 오류 응답은 {"error": true, "reason": "…"}.
 */
public final class OpenMeteoGeocodingClient {

  static final String LABEL = "Open-Meteo 지오코딩";

  /** 한 번에 받는 결과 수 */
  static final int COUNT = 10;

  private final RestClient client;
  private final String apiKey;
  private final JsonMapper json;
  private final Clock clock;

  /**
   * @param apiKey 유료 플랜 키. 비우면 무료 API로 부른다
   */
  public OpenMeteoGeocodingClient(
      String baseUrl, String apiKey, Duration timeout, JsonMapper json, Clock clock) {
    this.client = OpenMeteoClient.restClient(baseUrl, timeout);
    this.apiKey = OpenMeteoClient.blankToNull(apiKey);
    this.json = json;
    this.clock = clock;
  }

  public GeocodeResult search(String query) {
    Map<String, Object> vars = new LinkedHashMap<>();
    vars.put("name", query);
    vars.put("count", COUNT);
    String uri = "/v1/search?name={name}&language=ko&count={count}&format=json";
    if (apiKey != null) {
      uri += "&apikey={key}";
      vars.put("key", apiKey);
    }
    final Instant calledAt = clock.instant();
    final long started = System.nanoTime();
    OpenMeteoClient.Response res;
    try {
      res =
          client
              .get()
              .uri(uri, vars)
              .accept(MediaType.APPLICATION_JSON)
              .exchange(
                  (req, r) ->
                      new OpenMeteoClient.Response(
                          r.getStatusCode().value(),
                          new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    } catch (RestClientException e) {
      long ms = OpenMeteoClient.elapsed(started);
      boolean timeout = OpenMeteoClient.isTimeout(e);
      String code = timeout ? FetchErrors.TIMEOUT : FetchErrors.CLIENT;
      String message =
          timeout
              ? LABEL + " 응답 시간 초과"
              : LABEL + " 호출 실패: " + scrub(OpenMeteoClient.rootMessage(e));
      return GeocodeResult.failed(
          code, message, List.of(call(null, false, code, message, ms, null, calledAt)));
    }
    long ms = OpenMeteoClient.elapsed(started);
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
      return GeocodeResult.failed(
          code, message, List.of(call(res.status(), false, code, message, ms, bytes, calledAt)));
    }
    try {
      List<GeoPlace> places = parse(res.body());
      return GeocodeResult.success(
          places, List.of(call(res.status(), true, null, null, ms, bytes, calledAt)));
    } catch (RuntimeException e) {
      String message = LABEL + " 응답을 읽지 못했어요: " + e.getClass().getSimpleName();
      return GeocodeResult.failed(
          FetchErrors.PARSE,
          message,
          List.of(call(res.status(), false, FetchErrors.PARSE, message, ms, bytes, calledAt)));
    }
  }

  /** results 배열 → 장소 (results가 없으면 결과 없음). 좌표가 없는 항목은 뺀다 */
  List<GeoPlace> parse(String body) {
    JsonNode root = json.readTree(body);
    if (!root.isObject()) {
      throw new IllegalStateException("JSON 객체가 아님");
    }
    JsonNode results = root.path("results");
    List<GeoPlace> out = new ArrayList<>();
    if (!results.isArray()) {
      return out;
    }
    for (JsonNode n : results) {
      JsonNode lat = n.path("latitude");
      JsonNode lon = n.path("longitude");
      if (!lat.isNumber() || !lon.isNumber()) {
        continue;
      }
      JsonNode population = n.path("population");
      out.add(
          new GeoPlace(
              text(n, "name"),
              text(n, "admin1"),
              text(n, "country"),
              text(n, "country_code"),
              text(n, "feature_code"),
              population.isNumber() ? population.asLong() : null,
              lat.asDouble(),
              lon.asDouble()));
    }
    return out;
  }

  private static String text(JsonNode n, String field) {
    String v = n.path(field).asString("");
    return v.isBlank() ? null : v;
  }

  private static CallRecord call(
      Integer status,
      boolean success,
      String errorCode,
      String message,
      long ms,
      Integer bytes,
      Instant at) {
    return new CallRecord(
        null,
        GeocodingPort.PROVIDER_CODE,
        CallRecord.SEARCH,
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
    return OpenMeteoClient.scrub(apiKey, message);
  }
}
