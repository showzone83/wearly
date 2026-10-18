package app.wearly.notify.adapter.out.push;

import app.wearly.notify.application.port.out.PushMessage;
import app.wearly.notify.application.port.out.PushResult;
import app.wearly.notify.application.port.out.PushSenderPort;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Expo Push API 어댑터 (https://docs.expo.dev/push-notifications/sending-notifications/).
 *
 * <pre>
 * POST {base}/--/api/v2/push/send   (기본 base = https://exp.host)
 * Content-Type: application/json, Accept: application/json
 * Authorization: Bearer {access-token}   (Expo "enhanced push security"를 켰을 때만. 비우면 보내지 않는다)
 * 본문: 메시지 배열 (한 요청에 100개까지)
 * 응답: { "data": [ 티켓, … ] }  티켓 = { "status": "ok", "id": "…" }
 *       또는 { "status": "error", "message": "…", "details": { "error": "DeviceNotRegistered" } }
 *       data는 보낸 순서와 같다. 요청 전체가 틀리면 4xx·5xx와 { "errors": [ { "code": "…" } ] }
 * </pre>
 *
 * <p>"ok"는 Expo가 받았다는 뜻이고 기기 도착은 영수증(getReceipts)으로 알 수 있다. 영수증 확인은 아직 하지 않는다. 티켓의
 * DeviceNotRegistered만 토큰 삭제로 처리한다. 로그에는 토큰·문구를 남기지 않는다.
 */
public final class ExpoPushSenderAdapter implements PushSenderPort {

  private static final Logger log = LoggerFactory.getLogger(ExpoPushSenderAdapter.class);

  public static final String SEND_PATH = "/--/api/v2/push/send";

  /** Expo가 한 요청에 받는 메시지 수 */
  public static final int BATCH = 100;

  /** 안드로이드 알림 채널 (앱 lib/planNotify.ts가 같은 ID로 만든다. 기기에 없으면 안드로이드가 보이지 않는다) */
  public static final String CHANNEL_ID = "plan-d1";

  /** D-1 알림은 다음 날이 지나면 의미가 없으므로 12시간만 다시 전달을 시도한다 */
  static final int TTL_SECONDS = 12 * 60 * 60;

  static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

  private final RestClient client;
  private final String accessToken;
  private final JsonMapper json;

  /**
   * @param accessToken Expo 액세스 토큰. 비우면 Authorization 헤더를 보내지 않는다
   */
  public ExpoPushSenderAdapter(
      String baseUrl, String accessToken, Duration timeout, JsonMapper json) {
    this.client = restClient(baseUrl, timeout);
    this.accessToken = accessToken == null || accessToken.isBlank() ? null : accessToken.strip();
    this.json = json;
  }

  private static RestClient restClient(String baseUrl, Duration timeout) {
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .proxy(ProxySelector.getDefault())
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(timeout);
    String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return RestClient.builder().baseUrl(base).requestFactory(factory).build();
  }

  @Override
  public List<PushResult> send(List<PushMessage> messages) {
    List<PushResult> out = new ArrayList<>(messages.size());
    for (int i = 0; i < messages.size(); i += BATCH) {
      out.addAll(sendBatch(messages.subList(i, Math.min(messages.size(), i + BATCH))));
    }
    return out;
  }

  record Response(int status, String body) {}

  private List<PushResult> sendBatch(List<PushMessage> batch) {
    List<Map<String, Object>> body = batch.stream().map(ExpoPushSenderAdapter::toJson).toList();
    Response res;
    try {
      RestClient.RequestBodySpec req =
          client
              .post()
              .uri(SEND_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON);
      if (accessToken != null) {
        req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
      }
      res =
          req.body(json.writeValueAsString(body))
              .exchange(
                  (rq, r) ->
                      new Response(
                          r.getStatusCode().value(),
                          new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    } catch (RestClientException e) {
      logFailure("NETWORK", batch.size(), e.getClass().getSimpleName());
      return all(batch.size(), PushResult.failed("NETWORK"));
    }
    if (res.status() < 200 || res.status() >= 300) {
      String code = requestErrorCode(res.body());
      String error = code != null ? code : "HTTP_" + res.status();
      logFailure(error, batch.size(), "HTTP " + res.status());
      return all(batch.size(), PushResult.failed(error));
    }
    try {
      return parseTickets(res.body(), batch.size());
    } catch (RuntimeException e) {
      logFailure("PARSE", batch.size(), e.getClass().getSimpleName());
      return all(batch.size(), PushResult.failed("PARSE"));
    }
  }

  /** 티켓 배열 → 결과 (보낸 순서). 개수가 모자라면 나머지는 실패로 본다 */
  List<PushResult> parseTickets(String body, int count) {
    JsonNode data = json.readTree(body).required("data");
    List<PushResult> out = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      JsonNode t = data.isArray() ? data.get(i) : (i == 0 ? data : null);
      out.add(ticket(t));
    }
    return out;
  }

  private static PushResult ticket(JsonNode t) {
    if (t == null || t.isNull()) {
      return PushResult.failed("NO_TICKET");
    }
    if ("ok".equals(t.path("status").asString(""))) {
      return PushResult.ok();
    }
    String error = t.path("details").path("error").asString("");
    if (DEVICE_NOT_REGISTERED.equals(error)) {
      return new PushResult(PushResult.Status.DEVICE_NOT_REGISTERED, DEVICE_NOT_REGISTERED);
    }
    return PushResult.failed(error.isBlank() ? "ERROR" : error);
  }

  private String requestErrorCode(String body) {
    try {
      JsonNode errors = json.readTree(body).path("errors");
      if (errors.isArray() && !errors.isEmpty()) {
        String code = errors.get(0).path("code").asString("");
        return code.isBlank() ? null : code;
      }
    } catch (RuntimeException ignored) {
      // 본문이 JSON이 아니면 상태 코드만 쓴다
    }
    return null;
  }

  static Map<String, Object> toJson(PushMessage m) {
    Map<String, Object> o = new LinkedHashMap<>();
    o.put("to", m.token());
    o.put("title", m.title());
    o.put("body", m.body());
    o.put("data", m.data() == null ? Map.of() : m.data());
    o.put("sound", "default");
    o.put("channelId", CHANNEL_ID);
    o.put("priority", "high");
    o.put("ttl", TTL_SECONDS);
    return o;
  }

  private static List<PushResult> all(int n, PushResult r) {
    return new ArrayList<>(Collections.nCopies(n, r));
  }

  private static void logFailure(String code, int count, String detail) {
    log.warn("Expo 푸시 보내기 실패: {} ({}건, {})", code, count, detail);
  }
}
