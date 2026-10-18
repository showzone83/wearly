package app.wearly.image.domain;

/**
 * 검증하고 다시 인코딩한 사진 (메타데이터 없음, 방향 적용됨, JPEG).
 *
 * @param original 긴 변 2048 이하로 줄인 JPEG
 * @param width original 가로
 * @param height original 세로
 * @param thumb 긴 변 400 이하로 줄인 JPEG
 */
public record ProcessedImage(byte[] original, int width, int height, byte[] thumb) {}
