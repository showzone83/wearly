package app.wearly.image.application.port.in.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * complete 결과 (READY 이미지).
 *
 * @param url original GET 서명 URL
 * @param thumbUrl thumb GET 서명 URL
 * @param width original 가로
 * @param height original 세로
 * @param expiresAt 두 URL의 만료 시각
 */
public record ImageDto(
    UUID imageId, String url, String thumbUrl, int width, int height, Instant expiresAt) {}
