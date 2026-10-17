package app.wearly.auth.adapter.out.persistence.jpa;

import app.wearly.auth.application.port.out.RefreshTokenPort;
import app.wearly.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** RefreshTokenPort 구현 (JPA) */
@Component
class RefreshTokenJpaAdapter implements RefreshTokenPort {

  private final RefreshTokenJpaRepository tokens;

  RefreshTokenJpaAdapter(RefreshTokenJpaRepository tokens) {
    this.tokens = tokens;
  }

  @Override
  public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
    return tokens.findByTokenHashForUpdate(tokenHash).map(RefreshTokenJpaAdapter::toDomain);
  }

  @Override
  public RefreshToken save(RefreshToken t) {
    RefreshTokenEntity entity;
    if (t.id() == null) {
      entity = new RefreshTokenEntity(t.userId(), t.tokenHash(), t.familyId(), t.expiresAt());
    } else {
      entity =
          tokens
              .findById(t.id())
              .orElseThrow(() -> new IllegalStateException("토큰이 없어요: " + t.id()));
    }
    entity.applyState(t.revokedAt(), t.replacedById());
    return toDomain(tokens.saveAndFlush(entity));
  }

  @Override
  public void revokeFamily(UUID familyId, Instant at) {
    tokens.revokeFamily(familyId, at);
  }

  @Override
  public void revokeAllForUser(long userId, Instant at) {
    tokens.revokeAllForUser(userId, at);
  }

  private static RefreshToken toDomain(RefreshTokenEntity e) {
    return new RefreshToken(
        e.getId(),
        e.getUserId(),
        e.getTokenHash(),
        e.getFamilyId(),
        e.getExpiresAt(),
        e.getRevokedAt(),
        e.getReplacedById());
  }
}
