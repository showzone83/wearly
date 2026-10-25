package app.wearly.pairing.adapter.out.persistence.mybatis;

import app.wearly.pairing.adapter.out.persistence.mybatis.mapper.PairingMapper;
import app.wearly.pairing.application.port.out.ProxyDigestPort;
import app.wearly.pairing.application.port.out.dto.ClaimedActionRowDto;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** ProxyDigestPort 구현 (MyBatis). 가져오기는 UPDATE ... RETURNING 한 번이라 동시에 와도 한쪽만 받는다 */
@Component
class ProxyDigestMybatisAdapter implements ProxyDigestPort {

  private final PairingMapper mapper;

  ProxyDigestMybatisAdapter(PairingMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<Long> findDue(Instant quietBefore, Instant waitedBefore) {
    return mapper.findDueDigests(quietBefore, waitedBefore);
  }

  @Override
  public List<ClaimedActionRowDto> claim(long pairingId, Instant upTo, Instant now) {
    return mapper.claimUnnotified(pairingId, upTo, now);
  }
}
