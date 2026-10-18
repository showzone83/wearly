package app.wearly.image.domain;

import java.util.UUID;

/**
 * 오브젝트 키 규칙 (17 문서 1장). 업로드 원본은 uploads/{userId}/{imageId}(처리 후 지운다), 저장본은
 * users/{userId}/items/{imageId}/original.jpg·thumb.jpg. 회원 탈퇴는 두 접두사를 통째로 지운다.
 */
public final class ImageKeys {

  private ImageKeys() {}

  public static String upload(long userId, UUID imageId) {
    return uploadPrefix(userId) + imageId;
  }

  public static String original(long userId, UUID imageId) {
    return itemPrefix(userId, imageId) + "original.jpg";
  }

  public static String thumb(long userId, UUID imageId) {
    return itemPrefix(userId, imageId) + "thumb.jpg";
  }

  public static String userPrefix(long userId) {
    return "users/" + userId + "/";
  }

  public static String uploadPrefix(long userId) {
    return "uploads/" + userId + "/";
  }

  private static String itemPrefix(long userId, UUID imageId) {
    return userPrefix(userId) + "items/" + imageId + "/";
  }
}
