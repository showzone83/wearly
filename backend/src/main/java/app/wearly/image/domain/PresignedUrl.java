package app.wearly.image.domain;

import java.time.Instant;
import java.util.Map;

/**
 * 서명 URL 하나.
 *
 * @param url 서명 URL
 * @param headers 요청에 그대로 붙여야 하는 헤더 (서명에 들어간 것. 예: Content-Type)
 * @param expiresAt 만료 시각
 */
public record PresignedUrl(String url, Map<String, String> headers, Instant expiresAt) {}
