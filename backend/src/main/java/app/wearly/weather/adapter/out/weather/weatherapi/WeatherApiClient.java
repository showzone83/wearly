package app.wearly.weather.adapter.out.weather.weatherapi;

import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.CurrentWeather;
import app.wearly.weather.domain.DailyForecast;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.FetchResult;
import app.wearly.weather.domain.PlaceCandidate;
import app.wearly.weather.domain.SearchResult;
import app.wearly.weather.domain.WeatherCondition;
import app.wearly.weather.domain.WeatherRegion;
import app.wearly.weather.domain.WeatherReport;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * WeatherAPI.com forecast.json 호출 (17 문서 4장). 현재 날씨와 일별 예보를 호출 한 번으로 받는다. 장소 검색은 search.json (19
 * 문서).
 *
 * <pre>
 * GET {base}/forecast.json?key=…&amp;q={lat},{lon}&amp;days={n}
 *     &amp;lang=ko&amp;aqi=no&amp;alerts=no
 * </pre>
 *
 * <p>키가 쿼리 파라미터라서 오류 문구·호출 기록에서 반드시 지운다. 인증 오류(401·403)는 공급자 메시지를 쓰지 않는다.
 */
public final class WeatherApiClient {

  public static final String CODE = "WEATHERAPI";
  static final String LABEL = "WeatherAPI";
  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  static final int MAX_MESSAGE = 200;

  /** 구름·안개 (그 밖의 1063 이상은 비·눈·진눈깨비·뇌우로 본다) */
  static final Set<Integer> CLOUDY_CODES = Set.of(1003, 1006, 1009, 1030, 1135, 1147);

  /** 1006: 위치 없음, 2006: 키 틀림, 2007: 월 한도 초과, 2008: 키 막힘 (WeatherAPI 오류 코드) */
  static final int QUOTA_EXCEEDED = 2007;

  private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};

  private final RestClient client;
  private final JsonMapper json;
  private final Clock clock;

  public WeatherApiClient(String baseUrl, Duration timeout, JsonMapper json, Clock clock) {
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .proxy(ProxySelector.getDefault())
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(timeout);
    String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.client = RestClient.builder().baseUrl(base).requestFactory(factory).build();
    this.json = json;
    this.clock = clock;
  }

  private record Response(int status, String body) {}

  public FetchResult fetch(String apiKey, WeatherRegion region, int days) {
    Instant calledAt = clock.instant();
    long started = System.nanoTime();
    Response res;
    try {
      res =
          get(
              "/forecast.json?key={key}&q={q}&days={days}&lang=ko&aqi=no&alerts=no",
              Map.of(
                  "key",
                  apiKey,
                  "q",
                  String.format(Locale.ROOT, "%.4f,%.4f", region.lat(), region.lon()),
                  "days",
                  days));
    } catch (RestClientException e) {
      long ms = elapsed(started);
      String[] err = clientError(e, apiKey);
      return FetchResult.failed(
          err[0], err[1], List.of(call(region, null, false, err[0], err[1], ms, null, calledAt)));
    }
    long ms = elapsed(started);
    int bytes = res.body().getBytes(StandardCharsets.UTF_8).length;
    if (res.status() < 200 || res.status() >= 300) {
      String[] err = upstreamError(res, apiKey);
      return FetchResult.failed(
          err[0],
          err[1],
          List.of(call(region, res.status(), false, err[0], err[1], ms, bytes, calledAt)));
    }
    try {
      WeatherReport report = parse(res.body());
      return FetchResult.success(
          report, List.of(call(region, res.status(), true, null, null, ms, bytes, calledAt)));
    } catch (RuntimeException e) {
      String message = LABEL + " 응답을 읽지 못했어요: " + e.getClass().getSimpleName();
      return FetchResult.failed(
          FetchErrors.PARSE,
          message,
          List.of(
              call(region, res.status(), false, FetchErrors.PARSE, message, ms, bytes, calledAt)));
    }
  }

  /**
   * 장소 검색 (19 문서). {@code GET {base}/search.json?key=…&q=…} 응답은 [{id, name, region, country, lat,
   * lon, url}]. 결과가 없으면 빈 배열이다. 호출 기록의 regionCode는 null이다.
   */
  public SearchResult search(String apiKey, String query) {
    Instant calledAt = clock.instant();
    long started = System.nanoTime();
    Response res;
    try {
      res = get("/search.json?key={key}&q={q}", Map.of("key", apiKey, "q", query));
    } catch (RestClientException e) {
      long ms = elapsed(started);
      String[] err = clientError(e, apiKey);
      return SearchResult.failed(
          err[0],
          err[1],
          List.of(call(null, CallRecord.SEARCH, null, false, err[0], err[1], ms, null, calledAt)));
    }
    long ms = elapsed(started);
    int bytes = res.body().getBytes(StandardCharsets.UTF_8).length;
    if (res.status() < 200 || res.status() >= 300) {
      String[] err = upstreamError(res, apiKey);
      return SearchResult.failed(
          err[0],
          err[1],
          List.of(
              call(
                  null,
                  CallRecord.SEARCH,
                  res.status(),
                  false,
                  err[0],
                  err[1],
                  ms,
                  bytes,
                  calledAt)));
    }
    try {
      List<PlaceCandidate> items = parseSearch(res.body());
      return SearchResult.success(
          items,
          List.of(
              call(null, CallRecord.SEARCH, res.status(), true, null, null, ms, bytes, calledAt)));
    } catch (RuntimeException e) {
      String message = LABEL + " 응답을 읽지 못했어요: " + e.getClass().getSimpleName();
      return SearchResult.failed(
          FetchErrors.PARSE,
          message,
          List.of(
              call(
                  null,
                  CallRecord.SEARCH,
                  res.status(),
                  false,
                  FetchErrors.PARSE,
                  message,
                  ms,
                  bytes,
                  calledAt)));
    }
  }

  /** search.json 응답 → 검색 결과. 좌표가 없는 항목은 뺀다 */
  List<PlaceCandidate> parseSearch(String body) {
    JsonNode root = json.readTree(body);
    if (!root.isArray()) {
      throw new IllegalStateException("배열이 아님");
    }
    List<PlaceCandidate> out = new ArrayList<>();
    for (JsonNode n : root) {
      if (!n.path("lat").isNumber() || !n.path("lon").isNumber()) {
        continue;
      }
      out.add(
          new PlaceCandidate(
              n.path("name").asString("").strip(),
              n.path("region").asString("").strip(),
              n.path("country").asString("").strip(),
              n.path("lat").asDouble(),
              n.path("lon").asDouble()));
    }
    return out;
  }

  /** forecast.json 응답 → 공통 모델 */
  WeatherReport parse(String body) {
    JsonNode root = json.readTree(body);
    JsonNode cur = root.required("current");
    JsonNode curCond = cur.path("condition");
    CurrentWeather current =
        new CurrentWeather(
            cur.required("temp_c").asDouble(),
            cur.path("feelslike_c").isNumber() ? cur.path("feelslike_c").asDouble() : null,
            condition(curCond.path("code").asInt(0)),
            text(curCond),
            cur.path("last_updated_epoch").isNumber()
                ? Instant.ofEpochSecond(cur.path("last_updated_epoch").asLong())
                : clock.instant());
    List<DailyForecast> days = new ArrayList<>();
    for (JsonNode fd : root.path("forecast").path("forecastday")) {
      JsonNode day = fd.required("day");
      JsonNode cond = day.path("condition");
      int rain =
          Math.max(
              day.path("daily_chance_of_rain").asInt(0), day.path("daily_chance_of_snow").asInt(0));
      days.add(
          new DailyForecast(
              LocalDate.parse(fd.required("date").asString()),
              day.required("avgtemp_c").asDouble(),
              day.required("mintemp_c").asDouble(),
              day.required("maxtemp_c").asDouble(),
              Math.clamp(rain, 0, 100),
              condition(cond.path("code").asInt(0)),
              text(cond)));
    }
    if (days.isEmpty()) {
      throw new IllegalStateException("forecastday 없음");
    }
    String name = root.path("location").path("name").asString(null);
    return new WeatherReport(name, current, days, raw(root));
  }

  /** WeatherAPI 날씨 코드 → 앱 상태. 1000 맑음, 구름·안개는 CLOUDY, 나머지(비·눈·뇌우 등)는 RAIN */
  static WeatherCondition condition(int code) {
    if (code == 1000) {
      return WeatherCondition.SUNNY;
    }
    if (CLOUDY_CODES.contains(code) || code == 0) {
      return WeatherCondition.CLOUDY;
    }
    return WeatherCondition.RAIN;
  }

  /** 원본 응답에서 시간별 예보(forecastday[].hour)를 빼고 저장한다 (지역마다 수십 KB라) */
  private Map<String, Object> raw(JsonNode root) {
    JsonNode copy = root.deepCopy();
    for (JsonNode fd : copy.path("forecast").path("forecastday")) {
      if (fd instanceof ObjectNode o) {
        o.remove("hour");
      }
    }
    return json.convertValue(copy, MAP);
  }

  /** [오류 코드, 문구] */
  private String[] upstreamError(Response res, String apiKey) {
    int status = res.status();
    String code = FetchErrors.ofStatus(status);
    int apiCode = 0;
    String upstream = "";
    try {
      JsonNode err = json.readTree(res.body()).path("error");
      apiCode = err.path("code").asInt(0);
      upstream = err.path("message").asString("");
    } catch (RuntimeException ignored) {
      // 본문이 JSON이 아니면 상태 코드만 쓴다
    }
    if (apiCode == QUOTA_EXCEEDED) {
      return new String[] {FetchErrors.QUOTA, LABEL + " 월 호출 한도를 넘었어요 (HTTP " + status + ")."};
    }
    if (FetchErrors.AUTH.equals(code)) {
      return new String[] {code, LABEL + " 인증 실패 (HTTP " + status + "). 서버 설정의 키를 확인해 주세요."};
    }
    String detail = upstream.isBlank() ? "" : ": " + scrub(upstream, apiKey);
    return new String[] {code, LABEL + " API 오류 (HTTP " + status + ")" + detail};
  }

  private Response get(String uri, Map<String, ?> vars) {
    return client
        .get()
        .uri(uri, vars)
        .accept(MediaType.APPLICATION_JSON)
        // exchange는 4xx·5xx에도 예외 없이 본문을 준다. 상태 코드는 부르는 쪽에서 직접 나눈다
        .exchange(
            (req, r) ->
                new Response(
                    r.getStatusCode().value(),
                    new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8)));
  }

  /** 응답을 받지 못한 호출: [오류 코드, 문구] */
  private static String[] clientError(RestClientException e, String apiKey) {
    if (isTimeout(e)) {
      return new String[] {FetchErrors.TIMEOUT, LABEL + " 응답 시간 초과"};
    }
    return new String[] {FetchErrors.CLIENT, LABEL + " 호출 실패: " + scrub(rootMessage(e), apiKey)};
  }

  private static CallRecord call(
      WeatherRegion region,
      Integer status,
      boolean success,
      String errorCode,
      String message,
      long ms,
      Integer bytes,
      Instant at) {
    return call(
        region.code(), CallRecord.FORECAST, status, success, errorCode, message, ms, bytes, at);
  }

  private static CallRecord call(
      String target,
      String kind,
      Integer status,
      boolean success,
      String errorCode,
      String message,
      long ms,
      Integer bytes,
      Instant at) {
    return new CallRecord(
        target, CODE, kind, false, status, success, errorCode, message, ms, bytes, at);
  }

  private static String text(JsonNode condition) {
    String t = condition.path("text").asString("");
    return t.isBlank() ? null : t.strip();
  }

  static String scrub(String message, String apiKey) {
    if (message == null) {
      return "";
    }
    String out = message;
    if (apiKey != null && !apiKey.isBlank()) {
      out = out.replace(apiKey, "***");
    }
    out = out.replaceAll("(?i)(key=)[^&\\s\"']+", "$1***");
    return out.length() > MAX_MESSAGE ? out.substring(0, MAX_MESSAGE) + "…" : out;
  }

  private static long elapsed(long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
  }

  private static boolean isTimeout(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
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
    return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
  }
}
