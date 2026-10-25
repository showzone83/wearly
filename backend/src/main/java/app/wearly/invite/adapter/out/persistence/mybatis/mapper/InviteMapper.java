package app.wearly.invite.adapter.out.persistence.mybatis.mapper;

import app.wearly.invite.application.port.out.dto.InvitedFriendRowDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 초대 읽기 전용 조회 (MyBatis). SQL은 resources/mapper/invite/InviteMapper.xml */
@Mapper
public interface InviteMapper {

  long countInvited(@Param("referrerId") long referrerId);

  List<InvitedFriendRowDto> findInvitedFriends(
      @Param("referrerId") long referrerId, @Param("limit") int limit);
}
