package app.wearly.notify.adapter.out.persistence.mybatis.mapper;

import app.wearly.notify.application.port.out.dto.PushTargetRowDto;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 푸시 토큰 읽기 전용 조회 (MyBatis). SQL은 resources/mapper/notify/PushTokenMapper.xml */
@Mapper
public interface PushTokenMapper {

  List<PushTargetRowDto> findByUsers(@Param("userIds") Collection<Long> userIds);
}
