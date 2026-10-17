package app.wearly.user.adapter.out.persistence.jpa;

import app.wearly.user.application.port.out.UserPort;
import app.wearly.user.domain.User;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** UserPort 구현 (JPA). 엔티티 ↔ 도메인 모델 변환 */
@Component
class UserJpaAdapter implements UserPort {

  private final UserJpaRepository users;

  UserJpaAdapter(UserJpaRepository users) {
    this.users = users;
  }

  @Override
  public Optional<User> findById(long userId) {
    return users.findById(userId).map(UserJpaAdapter::toDomain);
  }

  @Override
  public User save(User user) {
    UserEntity entity =
        user.id() == null
            ? new UserEntity(user.email(), user.name())
            : users
                .findById(user.id())
                .orElseThrow(() -> new IllegalStateException("회원이 없어요: " + user.id()));
    entity.rename(user.name());
    entity.changeStatus(user.status());
    entity.changeRole(user.role());
    return toDomain(users.saveAndFlush(entity));
  }

  @Override
  public void deleteById(long userId) {
    users.findById(userId).ifPresent(users::delete);
    users.flush();
  }

  static User toDomain(UserEntity e) {
    return new User(
        e.getId(), e.getEmail(), e.getName(), e.getStatus(), e.getRole(), e.getCreatedAt());
  }
}
