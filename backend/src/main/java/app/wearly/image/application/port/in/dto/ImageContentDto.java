package app.wearly.image.application.port.in.dto;

/**
 * 저장한 사진 바이트.
 *
 * @param bytes original.jpg
 * @param contentType image/jpeg
 */
public record ImageContentDto(byte[] bytes, String contentType) {}
