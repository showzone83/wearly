package app.wearly.image.application.port.in.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 아이템 응답에 붙는 사진 (아이템 목록·상세·등록·수정·retire·restore).
 *
 * @param url original GET 서명 URL
 * @param thumbUrl thumb GET 서명 URL
 * @param expiresAt 두 URL의 만료 시각
 */
public record ItemImageRefDto(UUID imageId, String url, String thumbUrl, Instant expiresAt) {}
