package app.wearly.admin.adapter.out.persistence.mybatis;

import app.wearly.admin.adapter.out.persistence.mybatis.mapper.AdminWithdrawalMapper;
import app.wearly.admin.application.port.out.AdminWithdrawalQueryPort;
import app.wearly.admin.application.port.out.dto.AdminWithdrawalRowDto;
import app.wearly.admin.application.port.out.dto.WithdrawalReasonCountRowDto;
import app.wearly.admin.application.port.out.dto.WithdrawalStateCountRowDto;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** AdminWithdrawalQueryPort 구현 (MyBatis) */
@Component
class AdminWithdrawalMybatisAdapter implements AdminWithdrawalQueryPort {

  private final AdminWithdrawalMapper mapper;

  AdminWithdrawalMybatisAdapter(AdminWithdrawalMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<AdminWithdrawalRowDto> findWithdrawals(
      WithdrawalFilter filter, Instant now, int limit, long offset) {
    return mapper.findWithdrawals(filter, now, limit, offset);
  }

  @Override
  public long countWithdrawals(WithdrawalFilter filter, Instant now) {
    return mapper.countWithdrawals(filter, now);
  }

  @Override
  public WithdrawalStateCountRowDto countStates(Instant from) {
    return mapper.countStates(from);
  }

  @Override
  public List<WithdrawalReasonCountRowDto> countReasons(Instant from) {
    return mapper.countReasons(from);
  }

  @Override
  public long countOverdue(Instant now) {
    return mapper.countOverdue(now);
  }
}
