package app.wearly.user.adapter.out.persistence.mybatis.mapper;

import app.wearly.user.application.port.out.dto.CodeCountDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 프로필 읽기 전용 집계 (MyBatis). SQL은 resources/mapper/user/ProfileMapper.xml */
@Mapper
public interface ProfileMapper {

  List<CodeCountDto> countByCode();

  long countUsing(@Param("group") String group, @Param("code") String code);
}
