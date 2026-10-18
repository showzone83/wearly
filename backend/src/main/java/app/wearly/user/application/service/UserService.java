package app.wearly.user.application.service;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.BusinessException;
import app.wearly.user.application.port.in.UserDeletedListener;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import app.wearly.user.application.port.out.UserPort;
import app.wearly.user.domain.User;
import app.wearly.user.domain.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserUseCase {

  private final UserPort users;
  private final List<UserDeletedListener> deletedListeners;

  public UserService(UserPort users, List<UserDeletedListener> deletedListeners) {
    this.users = users;
    this.deletedListeners = List.copyOf(deletedListeners);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAccountDto> find(long userId) {
    return users.findById(userId).map(UserService::toAccount);
  }

  @Override
  @Transactional(readOnly = true)
  public UserAccountDto get(long userId) {
    return find(userId).orElseThrow(BusinessException::notFound);
  }

  @Override
  @Transactional
  public UserAccountDto create(String email, String name, String role) {
    UserRole r =
        UserRole.parse(role)
            .orElseThrow(
                () ->
                    new DomainValidationException(
                        List.of(
                            FieldError.notAllowed(
                                "role",
                                java.util.Arrays.stream(UserRole.values())
                                    .map(Enum::name)
                                    .toList()))));
    return toAccount(users.save(User.newUser(email, name, r)));
  }

  @Override
  @Transactional
  public UserAccountDto changeAccess(long userId, String status, String role) {
    User user = users.findById(userId).orElseThrow(BusinessException::notFound);
    user.changeAccess(status, role);
    return toAccount(users.save(user));
  }

  @Override
  @Transactional
  public void delete(long userId) {
    users.deleteById(userId);
    deletedListeners.forEach(l -> l.userDeleted(userId));
  }

  static UserAccountDto toAccount(User u) {
    return new UserAccountDto(
        u.id(), u.email(), u.name(), u.role().name(), u.status().name(), u.createdAt());
  }
}
