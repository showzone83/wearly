package app.wearly.admin.adapter.out.persistence.mybatis.mapper;

import app.wearly.admin.application.port.out.AdminInviteQueryPort.ShareFilter;
import app.wearly.admin.application.port.out.AdminInviteQueryPort.SignupFilter;
import app.wearly.admin.application.port.out.dto.InviteShareRowDto;
import app.wearly.admin.application.port.out.dto.InviteSignupRowDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 친구 초대 이력 읽기 전용 조회 (MyBatis). SQL은 resources/mapper/admin/AdminInviteMapper.xml */
@Mapper
public interface AdminInviteMapper {

  List<InviteSignupRowDto> findSignups(
      @Param("f") SignupFilter filter, @Param("limit") int limit, @Param("offset") long offset);

  long countSignups(@Param("f") SignupFilter filter);

  List<InviteShareRowDto> findShares(
      @Param("f") ShareFilter filter, @Param("limit") int limit, @Param("offset") long offset);

  long countShares(@Param("f") ShareFilter filter);
}
