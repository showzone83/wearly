package app.wearly.image.application.port.in.dto;

/**
 * 업로드 URL 요청.
 *
 * @param purpose ITEM
 * @param contentType image/jpeg · image/png
 * @param size 올릴 바이트 수
 */
public record CreateUploadUrlDto(String purpose, String contentType, Long size) {}
