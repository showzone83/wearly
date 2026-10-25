package app.wearly.pairing.adapter.out.persistence.mybatis;

import app.wearly.pairing.adapter.out.persistence.mybatis.mapper.PairingMapper;
import app.wearly.pairing.application.port.out.PairingQueryPort;
import app.wearly.pairing.application.port.out.dto.PairingRowDto;
import app.wearly.pairing.application.port.out.dto.ProxyActionRowDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** PairingQueryPort 구현 (MyBatis) */
@Component
class PairingMybatisAdapter implements PairingQueryPort {

  private final PairingMapper mapper;

  PairingMybatisAdapter(PairingMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long countOpenAsOwner(long ownerId, Instant pendingSince) {
    return mapper.countOpenAsOwner(ownerId, pendingSince);
  }

  @Override
  public long countOpenAsHelper(long helperId, Instant pendingSince) {
    return mapper.countOpenAsHelper(helperId, pendingSince);
  }

  @Override
  public List<PairingRowDto> findOpen(long userId, Instant pendingSince) {
    return mapper.findOpen(userId, pendingSince);
  }

  @Override
  public Optional<String> findUserName(long userId) {
    return Optional.ofNullable(mapper.findUserName(userId));
  }

  @Override
  public List<ProxyActionRowDto> findActions(long pairingId, int limit) {
    return mapper.findActions(pairingId, limit);
  }
}
