package app.wearly.invite.adapter.out.persistence.jpa;

import app.wearly.invite.application.port.out.InviteCodePort;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** InviteCodePort 구현 (JPA, 넣기는 네이티브 SQL) */
@Component
class InviteCodeJpaAdapter implements InviteCodePort {

  private final InviteCodeJpaRepository repository;

  InviteCodeJpaAdapter(InviteCodeJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<String> findCode(long userId) {
    return repository.findById(userId).map(InviteCodeEntity::getCode);
  }

  @Override
  public Optional<Long> findUserId(String code) {
    return repository.findByCode(code).map(InviteCodeEntity::getUserId);
  }

  @Override
  public boolean insertIfAbsent(long userId, String code, Instant now) {
    return repository.insertIfAbsent(userId, code, now) > 0;
  }
}
