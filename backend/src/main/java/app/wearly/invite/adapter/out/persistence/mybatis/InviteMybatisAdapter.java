package app.wearly.invite.adapter.out.persistence.mybatis;

import app.wearly.invite.adapter.out.persistence.mybatis.mapper.InviteMapper;
import app.wearly.invite.application.port.out.InviteQueryPort;
import app.wearly.invite.application.port.out.dto.InvitedFriendRowDto;
import java.util.List;
import org.springframework.stereotype.Component;

/** InviteQueryPort 구현 (MyBatis) */
@Component
class InviteMybatisAdapter implements InviteQueryPort {

  private final InviteMapper mapper;

  InviteMybatisAdapter(InviteMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long countInvited(long referrerId) {
    return mapper.countInvited(referrerId);
  }

  @Override
  public List<InvitedFriendRowDto> findInvitedFriends(long referrerId, int limit) {
    return mapper.findInvitedFriends(referrerId, limit);
  }
}
