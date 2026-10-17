package app.wearly.common.domain;

/**
 * 웹 어댑터가 받은 업로드 사진 (multipart 파트를 byte[]와 선언된 MIME으로 바꾼 값). 검사(비었는지·크기·형식)는 유스케이스가 정해진 순서대로 한다. 읽지
 * 못했으면 unreadable.
 *
 * @param bytes 파트 내용. 파트가 없거나 읽지 못했으면 빈 배열
 * @param contentType 요청이 선언한 형식 (없을 수 있다)
 * @param size 파트 크기 (바이트)
 */
public record ImageUpload(
    byte[] bytes, String contentType, long size, boolean present, boolean unreadable) {

  public static ImageUpload missing() {
    return new ImageUpload(new byte[0], null, 0, false, false);
  }

  public boolean isEmpty() {
    return !present || size == 0;
  }
}
