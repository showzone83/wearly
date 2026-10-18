package app.wearly.user.adapter.out.persistence.mybatis.mapper;

import app.wearly.user.application.port.out.dto.CodeCountDto;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 프로필 읽기 전용 집계 (MyBatis). SQL은 resources/mapper/user/ProfileMapper.xml */
@Mapper
public interface ProfileMapper {

  List<CodeCountDto> countByCode();

  long countUsing(@Param("group") String group, @Param("code") String code);

  /** 이 회원들 중 예정 전날 알림(notify.dMinus1)을 켠 활성 회원 ID. 프로필이 없으면 기본값(켬)으로 본다 */
  List<Long> findDayBeforeEnabled(@Param("userIds") Collection<Long> userIds);
}
