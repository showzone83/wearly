package app.wearly.user.application.service;

import app.wearly.common.error.ApiException;
import app.wearly.user.application.port.in.UserAccount;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.out.UserPort;
import app.wearly.user.domain.User;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserUseCase {

  private final UserPort users;

  public UserService(UserPort users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAccount> find(long userId) {
    return users.findById(userId).map(UserService::toAccount);
  }

  @Override
  @Transactional(readOnly = true)
  public UserAccount get(long userId) {
    return find(userId).orElseThrow(ApiException::notFound);
  }

  @Override
  @Transactional
  public UserAccount create(String email, String name, String role) {
    return toAccount(users.save(User.newUser(email, name, role)));
  }

  @Override
  @Transactional
  public UserAccount changeAccess(long userId, String status, String role) {
    User user = users.findById(userId).orElseThrow(ApiException::notFound);
    user.changeStatus(status);
    user.changeRole(role);
    return toAccount(users.save(user));
  }

  @Override
  @Transactional
  public void delete(long userId) {
    users.deleteById(userId);
  }

  static UserAccount toAccount(User u) {
    return new UserAccount(u.id(), u.email(), u.name(), u.role(), u.status(), u.createdAt());
  }
}
