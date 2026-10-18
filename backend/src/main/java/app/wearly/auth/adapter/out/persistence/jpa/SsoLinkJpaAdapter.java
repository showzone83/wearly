package app.wearly.auth.adapter.out.persistence.jpa;

import app.wearly.auth.application.port.out.SsoLinkPort;
import app.wearly.auth.domain.SsoLink;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** SsoLinkPort 구현 (JPA) */
@Component
class SsoLinkJpaAdapter implements SsoLinkPort {

  private final UserSsoJpaRepository links;

  SsoLinkJpaAdapter(UserSsoJpaRepository links) {
    this.links = links;
  }

  @Override
  public Optional<SsoLink> find(String issuer, String subject) {
    return links.findByIssuerAndSubject(issuer, subject).map(SsoLinkJpaAdapter::toDomain);
  }

  @Override
  public SsoLink create(SsoLink l, Instant at) {
    return toDomain(
        links.saveAndFlush(
            new UserSsoEntity(l.userId(), l.providerId(), l.issuer(), l.subject(), l.email(), at)));
  }

  @Override
  public void touch(long linkId, String email, String providerId, Instant at) {
    links.findById(linkId).ifPresent(e -> e.touch(email, providerId, at));
    links.flush();
  }

  private static SsoLink toDomain(UserSsoEntity e) {
    return new SsoLink(
        e.getId(), e.getUserId(), e.getProvider(), e.getIssuer(), e.getSubject(), e.getEmail());
  }
}
