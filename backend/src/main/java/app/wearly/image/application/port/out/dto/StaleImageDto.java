package app.wearly.image.application.port.out.dto;

/**
 * 정리 작업 대상 이미지 한 행.
 *
 * @param imageId UUID 문자열
 * @param userId 회원
 * @param status PENDING · READY · DELETED
 */
public record StaleImageDto(String imageId, long userId, String status) {}
