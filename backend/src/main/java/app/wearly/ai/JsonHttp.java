package app.wearly.ai;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI·Gemini 공급자가 함께 쓰는 JSON POST (Spring RestClient + JDK HttpClient, SDK 없이 공식 REST API를 부른다).
 * 재시도는 하지 않는다: 실패하면 분석 서비스가 다음 공급자로 넘어간다.
 * 오류 메시지는 Redactor로 키 모양 문자열을 지운 뒤에만 예외에 담는다.
 */
final class JsonHttp {

    private final RestClient client;
    private final JsonMapper jsonMapper;
    private final String providerName;
    private final String apiKey;

    JsonHttp(JsonMapper jsonMapper, Duration timeout, String providerName, String apiKey) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .proxy(ProxySelector.getDefault())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().requestFactory(factory).build();
        this.jsonMapper = jsonMapper;
        this.providerName = providerName;
        this.apiKey = apiKey;
    }

    /**
     * @param errorMessagePath 오류 본문에서 메시지를 읽을 경로 (둘 다 {"error":{"message":…}} 모양)
     * @return 2xx 응답 본문
     * @throws AiProviderException API_{상태}, TIMEOUT, API_CLIENT, INVALID_RESPONSE
     */
    JsonNode post(String url, Map<String, String> headers, Object body) {
        String requestJson = jsonMapper.writeValueAsString(body);
        Raw raw;
        try {
            raw = client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> headers.forEach(h::set))
                    .body(requestJson)
                    .exchange((req, res) -> new Raw(res.getStatusCode().value(),
                            new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw new AiProviderException("TIMEOUT", providerName + " 호출 시간 초과", e);
            }
            throw new AiProviderException("API_CLIENT", providerName + " 호출 실패(네트워크)", e);
        }
        if (raw == null) {
            throw new AiProviderException("INVALID_RESPONSE", providerName + " 응답이 없어요.");
        }
        JsonNode node = parse(raw.body());
        if (raw.status() < 200 || raw.status() >= 300) {
            String upstream = node == null ? null : node.path("error").path("message").asString(null);
            throw new AiProviderException("API_" + raw.status(),
                    providerName + " API 오류 " + raw.status() + (upstream == null ? "" : ": " + Redactor.redact(upstream, apiKey)));
        }
        if (node == null || !node.isObject()) {
            throw new AiProviderException("INVALID_RESPONSE", providerName + " 응답이 JSON 객체가 아니에요.");
        }
        return node;
    }

    private JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readTree(text);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private record Raw(int status, String body) {
    }
}
