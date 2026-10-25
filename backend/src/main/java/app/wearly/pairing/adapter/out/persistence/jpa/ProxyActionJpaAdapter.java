package app.wearly.pairing.adapter.out.persistence.jpa;

import app.wearly.pairing.application.port.out.ProxyActionPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** ProxyActionPort 구현 (JPA) */
@Component
class ProxyActionJpaAdapter implements ProxyActionPort {

  private final ProxyActionJpaRepository repository;

  ProxyActionJpaAdapter(ProxyActionJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public void record(
      long pairingId,
      long ownerId,
      long helperId,
      String action,
      long itemId,
      String itemName,
      Instant now) {
    repository.save(
        new ProxyActionEntity(pairingId, ownerId, helperId, action, itemId, itemName, now));
  }
}
