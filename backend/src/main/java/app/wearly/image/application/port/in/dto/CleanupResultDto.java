package app.wearly.image.application.port.in.dto;

/**
 * 정리 작업 결과 (지운 이미지 수).
 *
 * @param pending 올리다 만 PENDING
 * @param unattached 아이템에 안 붙은 READY
 * @param deleted 이미 DELETED였던 행
 */
public record CleanupResultDto(int pending, int unattached, int deleted) {

  public int total() {
    return pending + unattached + deleted;
  }
}
