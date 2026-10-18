package app.wearly.image.adapter.in.user;

import app.wearly.image.application.port.in.ImageCleanupUseCase;
import app.wearly.user.application.port.in.UserDeletedListener;
import org.springframework.stereotype.Component;

/**
 * user 컨텍스트의 탈퇴 알림을 받아 그 회원의 사진 객체(users/{userId}/·uploads/{userId}/)를 커밋 뒤에 지운다. 행(tb_item_image)은
 * FK CASCADE로 지워진다. 의존은 image → user 한 방향이다.
 */
@Component
class ImageCleanupOnUserDeleted implements UserDeletedListener {

  private final ImageCleanupUseCase cleanup;

  ImageCleanupOnUserDeleted(ImageCleanupUseCase cleanup) {
    this.cleanup = cleanup;
  }

  @Override
  public void userDeleted(long userId) {
    cleanup.deleteAllOf(userId);
  }
}
