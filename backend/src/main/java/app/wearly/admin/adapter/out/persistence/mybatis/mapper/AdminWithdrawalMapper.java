package app.wearly.admin.adapter.out.persistence.mybatis.mapper;

import app.wearly.admin.application.port.out.AdminWithdrawalQueryPort.WithdrawalFilter;
import app.wearly.admin.application.port.out.dto.AdminWithdrawalRowDto;
import app.wearly.admin.application.port.out.dto.WithdrawalReasonCountRowDto;
import app.wearly.admin.application.port.out.dto.WithdrawalStateCountRowDto;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 탈퇴 기록 읽기 전용 조회 (MyBatis). SQL은 resources/mapper/admin/AdminWithdrawalMapper.xml */
@Mapper
public interface AdminWithdrawalMapper {

  List<AdminWithdrawalRowDto> findWithdrawals(
      @Param("f") WithdrawalFilter filter,
      @Param("now") Instant now,
      @Param("limit") int limit,
      @Param("offset") long offset);

  long countWithdrawals(@Param("f") WithdrawalFilter filter, @Param("now") Instant now);

  WithdrawalStateCountRowDto countStates(@Param("from") Instant from);

  List<WithdrawalReasonCountRowDto> countReasons(@Param("from") Instant from);

  long countOverdue(@Param("now") Instant now);
}
