package app.wearly.user.adapter.out.persistence.jpa;

import app.wearly.user.application.port.out.UserPort;
import app.wearly.user.domain.User;
import app.wearly.user.domain.UserRole;
import app.wearly.user.domain.UserStatus;
import java.util.List;
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
    entity.changeStatus(user.status().name());
    entity.changeRole(user.role().name());
    return toDomain(users.saveAndFlush(entity));
  }

  @Override
  public void deleteById(long userId) {
    users.findById(userId).ifPresent(users::delete);
    users.flush();
  }

  @Override
  public long countAdmins() {
    return users.countAdmins();
  }

  @Override
  public List<Long> lockActiveAdminIds() {
    return users.lockActiveAdminIds();
  }

  static User toDomain(UserEntity e) {
    return new User(
        e.getId(),
        e.getEmail(),
        e.getName(),
        UserStatus.valueOf(e.getStatus()),
        UserRole.valueOf(e.getRole()),
        e.getCreatedAt());
  }
}
