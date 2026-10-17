package app.wearly.user.application.port.out;

import app.wearly.user.domain.User;
import java.util.Optional;

/** 회원 저장·단건 로드 (JPA) */
public interface UserPort {

  Optional<User> findById(long userId);

  /** 새 회원이면 만들고(id·createdAt 채움), 있으면 이름·상태·역할을 바꾼다. 바로 flush한다 */
  User save(User user);

  void deleteById(long userId);
}
