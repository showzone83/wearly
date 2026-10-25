package app.wearly.pairing.adapter.out.persistence.jpa;

import app.wearly.pairing.application.port.out.PairingPort;
import app.wearly.pairing.domain.Pairing;
import app.wearly.pairing.domain.PairingEndReason;
import app.wearly.pairing.domain.PairingPermission;
import app.wearly.pairing.domain.PairingRole;
import app.wearly.pairing.domain.PairingStatus;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** PairingPort 구현 (JPA). 엔티티 ↔ 도메인 모델 변환 */
@Component
class PairingJpaAdapter implements PairingPort {

  private final PairingJpaRepository repository;

  PairingJpaAdapter(PairingJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<Pairing> find(long pairingId) {
    return repository.findById(pairingId).map(PairingJpaAdapter::toDomain);
  }

  @Override
  public Optional<Pairing> findUnended(long ownerId, long helperId) {
    return repository.findUnended(ownerId, helperId).map(PairingJpaAdapter::toDomain);
  }

  @Override
  public Pairing save(Pairing p) {
    PairingEntity e =
        p.id() == null
            ? new PairingEntity(p.ownerId(), p.helperId(), p.issuedBy().name(), p.createdAt())
            : repository
                .findById(p.id())
                .orElseThrow(() -> new IllegalStateException("연결이 없어요: " + p.id()));
    e.apply(
        p.permission() == null ? null : p.permission().name(),
        p.status().name(),
        p.notifyOwner(),
        p.approvedAt(),
        p.endedAt(),
        p.endedBy() == null ? null : p.endedBy().name());
    return toDomain(repository.saveAndFlush(e));
  }

  static Pairing toDomain(PairingEntity e) {
    return new Pairing(
        e.getId(),
        e.getOwnerId(),
        e.getHelperId(),
        e.getPermission() == null ? null : PairingPermission.valueOf(e.getPermission()),
        PairingStatus.valueOf(e.getStatus()),
        PairingRole.valueOf(e.getIssuedBy()),
        e.isNotifyOwner(),
        e.getCreatedAt(),
        e.getApprovedAt(),
        e.getEndedAt(),
        e.getEndedBy() == null ? null : PairingEndReason.valueOf(e.getEndedBy()));
  }
}
