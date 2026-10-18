package app.wearly.notify.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.notify.adapter.out.push.ExpoPushSenderAdapter;
import app.wearly.notify.adapter.out.push.LogPushSenderAdapter;
import app.wearly.notify.application.port.out.PushMessage;
import app.wearly.notify.application.port.out.PushResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Expo Push API 어댑터 (가짜 HTTP 서버, 실제 Expo는 부르지 않는다): 100개 묶음, 티켓 순서, DeviceNotRegistered, 요청 오류 */
class ExpoPushSenderAdapterTest {

  static final JsonMapper JSON = JsonMapper.builder().build();

  record Req(String path, String auth, String contentType, JsonNode body) {}

  final List<Req> requests = new CopyOnWriteArrayList<>();
  HttpServer server;

  /** 요청 본문(메시지 배열) → (상태, 응답 본문) */
  String start(Function<JsonNode, Map.Entry<Integer, String>> answer) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        ex -> {
          JsonNode body =
              JSON.readTree(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          requests.add(
              new Req(
                  ex.getRequestURI().getPath(),
                  ex.getRequestHeaders().getFirst("Authorization"),
                  ex.getRequestHeaders().getFirst("Content-Type"),
                  body));
          Map.Entry<Integer, String> out = answer.apply(body);
          byte[] bytes = out.getValue().getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "application/json");
          ex.sendResponseHeaders(out.getKey(), bytes.length);
          ex.getResponseBody().write(bytes);
          ex.close();
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  static List<PushMessage> messages(int n) {
    List<PushMessage> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(
          new PushMessage(
              "ExponentPushToken[t" + i + "]",
              "내일 예정 코디",
              "내일 ‘출근’ 예정이에요 · 예보 18°C",
              Map.of("type", "plan-d1", "serverId", String.valueOf(i))));
    }
    return out;
  }

  /** 받은 메시지마다 티켓. 토큰 이름에 gone이 있으면 DeviceNotRegistered */
  static Map.Entry<Integer, String> tickets(JsonNode body) {
    StringBuilder sb = new StringBuilder("{\"data\":[");
    for (int i = 0; i < body.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      String to = body.get(i).get("to").asString();
      if (to.contains("gone")) {
        sb.append(
            "{\"status\":\"error\",\"message\":\"not registered\","
                + "\"details\":{\"error\":\"DeviceNotRegistered\"}}");
      } else if (to.contains("slow")) {
        sb.append(
            "{\"status\":\"error\",\"message\":\"rate\","
                + "\"details\":{\"error\":\"MessageRateExceeded\"}}");
      } else {
        sb.append("{\"status\":\"ok\",\"id\":\"r-").append(i).append("\"}");
      }
    }
    return Map.entry(200, sb.append("]}").toString());
  }

  @Test
  void 백_개씩_나눠_보내고_결과는_보낸_순서와_같다() throws IOException {
    String base = start(ExpoPushSenderAdapterTest::tickets);
    ExpoPushSenderAdapter adapter =
        new ExpoPushSenderAdapter(base, "", Duration.ofSeconds(5), JSON);
    List<PushMessage> msgs = new ArrayList<>(messages(150));
    msgs.set(120, new PushMessage("ExponentPushToken[gone]", "t", "b", Map.of()));
    msgs.set(3, new PushMessage("ExponentPushToken[slow]", "t", "b", Map.of()));

    List<PushResult> results = adapter.send(msgs);

    assertThat(requests).hasSize(2);
    assertThat(requests.get(0).body().size()).isEqualTo(100);
    assertThat(requests.get(1).body().size()).isEqualTo(50);
    assertThat(requests.get(0).path()).isEqualTo("/--/api/v2/push/send");
    assertThat(requests.get(0).contentType()).startsWith("application/json");
    assertThat(requests.get(0).auth()).isNull(); // 액세스 토큰을 비우면 헤더를 보내지 않는다
    JsonNode first = requests.get(0).body().get(0);
    assertThat(first.get("to").asString()).isEqualTo("ExponentPushToken[t0]");
    assertThat(first.get("title").asString()).isEqualTo("내일 예정 코디");
    assertThat(first.get("data").get("type").asString()).isEqualTo("plan-d1");
    assertThat(first.get("channelId").asString()).isEqualTo("plan-d1");
    assertThat(first.get("sound").asString()).isEqualTo("default");

    assertThat(results).hasSize(150);
    assertThat(results.get(0).status()).isEqualTo(PushResult.Status.OK);
    assertThat(results.get(3)).isEqualTo(PushResult.failed("MessageRateExceeded"));
    assertThat(results.get(120).status()).isEqualTo(PushResult.Status.DEVICE_NOT_REGISTERED);
    assertThat(results.get(149).status()).isEqualTo(PushResult.Status.OK);
  }

  @Test
  void 액세스_토큰이_있으면_Bearer_헤더를_보낸다() throws IOException {
    String base = start(ExpoPushSenderAdapterTest::tickets);
    new ExpoPushSenderAdapter(base + "/", " expo-access ", Duration.ofSeconds(5), JSON)
        .send(messages(1));
    assertThat(requests.getFirst().auth()).isEqualTo("Bearer expo-access");
  }

  @Test
  void 요청_전체_오류면_묶음_전부_실패() throws IOException {
    String base =
        start(
            b ->
                Map.entry(
                    400,
                    "{\"errors\":[{\"code\":\"PUSH_TOO_MANY_EXPERIENCE_IDS\","
                        + "\"message\":\"x\"}]}"));
    List<PushResult> results =
        new ExpoPushSenderAdapter(base, null, Duration.ofSeconds(5), JSON).send(messages(3));
    assertThat(results).containsOnly(PushResult.failed("PUSH_TOO_MANY_EXPERIENCE_IDS"));
    assertThat(results).hasSize(3);
  }

  @Test
  void 서버_오류_형식_오류_연결_실패도_throw하지_않는다() throws IOException {
    String base = start(b -> Map.entry(503, "busy"));
    assertThat(new ExpoPushSenderAdapter(base, null, Duration.ofSeconds(5), JSON).send(messages(2)))
        .containsExactly(PushResult.failed("HTTP_503"), PushResult.failed("HTTP_503"));
    stop();
    requests.clear();

    String broken = start(b -> Map.entry(200, "{\"nope\":1}"));
    assertThat(
            new ExpoPushSenderAdapter(broken, null, Duration.ofSeconds(5), JSON).send(messages(1)))
        .containsExactly(PushResult.failed("PARSE"));
    stop();

    // 닫힌 포트
    assertThat(
            new ExpoPushSenderAdapter(broken, null, Duration.ofSeconds(2), JSON).send(messages(1)))
        .containsExactly(PushResult.failed("NETWORK"));
  }

  @Test
  void 티켓이_모자라면_나머지는_실패() throws IOException {
    String base = start(b -> Map.entry(200, "{\"data\":[{\"status\":\"ok\",\"id\":\"a\"}]}"));
    assertThat(new ExpoPushSenderAdapter(base, null, Duration.ofSeconds(5), JSON).send(messages(2)))
        .containsExactly(PushResult.ok(), PushResult.failed("NO_TICKET"));
  }

  @Test
  void log_공급자는_보내지_않고_모두_성공() {
    assertThat(new LogPushSenderAdapter().send(messages(3)))
        .containsOnly(PushResult.ok())
        .hasSize(3);
    assertThat(new LogPushSenderAdapter().send(List.of())).isEmpty();
  }
}
