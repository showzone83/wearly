package app.wearly.notify.adapter.out.persistence.jpa;

import app.wearly.notify.application.port.out.PushTokenPort;
import app.wearly.notify.domain.PushPlatform;
import java.time.Instant;
import java.util.Collection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PushTokenPort 구현 (JPA) */
@Component
class PushTokenJpaAdapter implements PushTokenPort {

  private final PushTokenJpaRepository repository;

  PushTokenJpaAdapter(PushTokenJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public void upsert(
      long userId, String deviceId, PushPlatform platform, String token, Instant now) {
    // 같은 토큰을 다른 회원·기기가 갖고 있으면 먼저 지운다 (uq_push_token_token)
    repository.deleteOtherOwners(token, userId, deviceId);
    repository
        .findByUserIdAndDeviceId(userId, deviceId)
        .ifPresentOrElse(
            e -> e.change(platform.name(), token, now),
            () ->
                repository.save(
                    new PushTokenEntity(userId, deviceId, platform.name(), token, now)));
  }

  @Override
  @Transactional
  public void delete(long userId, String deviceId) {
    repository.deleteByOwner(userId, deviceId);
  }

  @Override
  @Transactional
  public int deleteTokens(Collection<String> tokens) {
    return tokens.isEmpty() ? 0 : repository.deleteByTokens(tokens);
  }
}
