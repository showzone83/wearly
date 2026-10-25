package app.wearly.admin.adapter.out.persistence.mybatis;

import app.wearly.admin.adapter.out.persistence.mybatis.mapper.AdminInviteMapper;
import app.wearly.admin.application.port.out.AdminInviteQueryPort;
import app.wearly.admin.application.port.out.dto.InviteShareRowDto;
import app.wearly.admin.application.port.out.dto.InviteSignupRowDto;
import java.util.List;
import org.springframework.stereotype.Component;

/** AdminInviteQueryPort 구현 (MyBatis) */
@Component
class AdminInviteMybatisAdapter implements AdminInviteQueryPort {

  private final AdminInviteMapper mapper;

  AdminInviteMybatisAdapter(AdminInviteMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<InviteSignupRowDto> findSignups(SignupFilter filter, int limit, long offset) {
    return mapper.findSignups(filter, limit, offset);
  }

  @Override
  public long countSignups(SignupFilter filter) {
    return mapper.countSignups(filter);
  }

  @Override
  public List<InviteShareRowDto> findShares(ShareFilter filter, int limit, long offset) {
    return mapper.findShares(filter, limit, offset);
  }

  @Override
  public long countShares(ShareFilter filter) {
    return mapper.countShares(filter);
  }
}
