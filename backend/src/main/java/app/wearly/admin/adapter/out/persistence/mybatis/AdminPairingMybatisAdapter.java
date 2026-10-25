package app.wearly.admin.adapter.out.persistence.mybatis;

import app.wearly.admin.adapter.out.persistence.mybatis.mapper.AdminPairingMapper;
import app.wearly.admin.application.port.out.AdminPairingQueryPort;
import app.wearly.admin.application.port.out.dto.AdminPairingActionRowDto;
import app.wearly.admin.application.port.out.dto.AdminPairingRowDto;
import java.util.List;
import org.springframework.stereotype.Component;

/** AdminPairingQueryPort 구현 (MyBatis) */
@Component
class AdminPairingMybatisAdapter implements AdminPairingQueryPort {

  private final AdminPairingMapper mapper;

  AdminPairingMybatisAdapter(AdminPairingMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<AdminPairingRowDto> findPairings(PairingFilter filter, int limit, long offset) {
    return mapper.findPairings(filter, limit, offset);
  }

  @Override
  public long countPairings(PairingFilter filter) {
    return mapper.countPairings(filter);
  }

  @Override
  public AdminPairingRowDto findPairing(long pairingId) {
    return mapper.findPairing(pairingId);
  }

  @Override
  public List<AdminPairingActionRowDto> findActions(long pairingId, int limit) {
    return mapper.findActions(pairingId, limit);
  }
}
