package app.wearly.image.application.port.in.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 업로드 URL 발급 결과.
 *
 * @param imageId 새 이미지
 * @param uploadUrl PUT 서명 URL
 * @param method 언제나 PUT
 * @param headers PUT에 그대로 붙일 헤더 (Content-Type)
 * @param expiresAt uploadUrl 만료 시각
 */
public record UploadUrlDto(
    UUID imageId,
    String uploadUrl,
    String method,
    Map<String, String> headers,
    Instant expiresAt) {}
