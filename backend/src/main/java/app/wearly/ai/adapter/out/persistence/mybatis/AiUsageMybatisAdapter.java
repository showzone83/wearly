package app.wearly.ai.adapter.out.persistence.mybatis;

import app.wearly.ai.adapter.out.persistence.mybatis.mapper.AiQuotaMapper;
import app.wearly.ai.application.port.out.AiUsagePort;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** AiUsagePort 구현 (MyBatis) */
@Component
class AiUsageMybatisAdapter implements AiUsagePort {

  private final AiQuotaMapper mapper;

  AiUsageMybatisAdapter(AiQuotaMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long countSuccess(long userId, String feature, Instant from, Instant to) {
    return mapper.countSuccess(userId, feature, from, to);
  }

  @Override
  public long countReservations(long userId, String feature, Instant since) {
    return mapper.countReservations(userId, feature, since);
  }

  @Override
  public void lockQuota(long userId, String feature) {
    mapper.lockQuota(userId, feature);
  }
}
