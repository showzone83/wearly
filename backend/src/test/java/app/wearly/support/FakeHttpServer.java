package app.wearly.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 실제 키 없이 공급자 HTTP 호출을 확인하는 로컬 가짜 서버 (JDK HttpServer).
 * 경로 접두사마다 응답(상태·본문·지연)을 정하고, 받은 요청을 모두 기록한다.
 */
public class FakeHttpServer implements AutoCloseable {

    public record Request(String method, String path, String query, Map<String, List<String>> headers, String body) {

        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .findFirst()
                    .map(e -> e.getValue().getFirst())
                    .orElse(null);
        }
    }

    public record Reply(int status, String body, long delayMs) {
    }

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public FakeHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getRawPath();
            requests.add(new Request(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
                    Map.copyOf(exchange.getRequestHeaders()), body));
            Reply reply = replies.entrySet().stream()
                    .filter(e -> path.startsWith(e.getKey()))
                    .max((a, b) -> Integer.compare(a.getKey().length(), b.getKey().length()))
                    .map(Map.Entry::getValue)
                    .orElse(new Reply(404, "{\"error\":{\"message\":\"no reply\"}}", 0));
            if (reply.delayMs() > 0) {
                try {
                    Thread.sleep(reply.delayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(reply.status(), out.length);
                exchange.getResponseBody().write(out);
            } catch (IOException ignored) {
                // 클라이언트가 시간 초과로 먼저 끊은 경우
            }
            exchange.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public FakeHttpServer reply(String pathPrefix, int status, String body) {
        return reply(pathPrefix, status, body, 0);
    }

    public FakeHttpServer reply(String pathPrefix, int status, String body, long delayMs) {
        replies.put(pathPrefix, new Reply(status, body, delayMs));
        return this;
    }

    public List<Request> requests() {
        return requests;
    }

    public Request last() {
        return requests.isEmpty() ? null : requests.getLast();
    }

    public void clear() {
        requests.clear();
        replies.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
