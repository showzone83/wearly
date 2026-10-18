package app.wearly.support;

import app.wearly.common.error.BusinessException;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 서비스 단위 테스트용 user 컨텍스트 입력 포트 (메모리) */
public class FakeUsers implements UserUseCase {

  public final Map<Long, UserAccountDto> rows = new LinkedHashMap<>();
  private long nextId = 1;

  public UserAccountDto add(String email, String role, String status) {
    UserAccountDto u = new UserAccountDto(nextId++, email, "테스터", role, status, Instant.EPOCH);
    rows.put(u.id(), u);
    return u;
  }

  @Override
  public Optional<UserAccountDto> find(long userId) {
    return Optional.ofNullable(rows.get(userId));
  }

  @Override
  public UserAccountDto get(long userId) {
    return find(userId).orElseThrow(BusinessException::notFound);
  }

  @Override
  public UserAccountDto create(String email, String name, String role) {
    UserAccountDto u =
        new UserAccountDto(nextId++, email, name, role, UserAccountDto.ACTIVE, Instant.EPOCH);
    rows.put(u.id(), u);
    return u;
  }

  @Override
  public UserAccountDto changeAccess(long userId, String status, String role) {
    UserAccountDto u = get(userId);
    UserAccountDto changed =
        new UserAccountDto(u.id(), u.email(), u.name(), role, status, u.createdAt());
    rows.put(userId, changed);
    return changed;
  }

  @Override
  public void delete(long userId) {
    rows.remove(userId);
  }

  @Override
  public long countAdmins() {
    return rows.values().stream().filter(UserAccountDto::isAdmin).count();
  }

  @Override
  public List<Long> lockActiveAdmins() {
    return rows.values().stream()
        .filter(u -> u.isAdmin() && u.isActive())
        .map(UserAccountDto::id)
        .toList();
  }
}
