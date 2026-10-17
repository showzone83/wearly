package app.wearly.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 공급자 API를 흉내 내는 로컬 HTTP 서버 (JDK HttpServer). 어떤 경로든 받아서 요청을 기록하고 정해 둔 응답을 준다. 실제 키 없이 요청 JSON
 * 구성·응답 파싱·오류 처리를 확인하는 데 쓴다.
 */
public class FakeHttpServer implements AutoCloseable {

  public record Request(
      String method, String path, String query, Map<String, List<String>> headers, String body) {

    public String header(String name) {
      return headers.entrySet().stream()
          .filter(e -> e.getKey().equalsIgnoreCase(name))
          .map(e -> e.getValue().getFirst())
          .findFirst()
          .orElse(null);
    }
  }

  private final HttpServer server;
  private final List<Request> requests = new CopyOnWriteArrayList<>();
  private volatile int status = 200;
  private volatile String body = "{}";
  private volatile long delayMs = 0;

  public FakeHttpServer() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    server.createContext(
        "/",
        exchange -> {
          String requestBody =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          requests.add(
              new Request(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().getPath(),
                  exchange.getRequestURI().getRawQuery(),
                  Map.copyOf(exchange.getRequestHeaders()),
                  requestBody));
          if (delayMs > 0) {
            try {
              Thread.sleep(delayMs);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
          byte[] out = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, out.length);
          exchange.getResponseBody().write(out);
          exchange.close();
        });
    server.start();
  }

  public FakeHttpServer respond(int status, String body) {
    this.status = status;
    this.body = body;
    return this;
  }

  public FakeHttpServer delay(long ms) {
    this.delayMs = ms;
    return this;
  }

  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public List<Request> requests() {
    return requests;
  }

  public Request last() {
    return requests.getLast();
  }

  public void clear() {
    requests.clear();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
