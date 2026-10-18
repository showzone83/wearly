package app.wearly.notify.application.port.out;

import app.wearly.notify.application.port.out.dto.PushTargetRowDto;
import java.util.Collection;
import java.util.List;

/** 푸시 토큰 조회 (MyBatis) */
public interface PushTokenQueryPort {

  /** 이 회원들의 토큰 (회원 ID → 기기 ID 순) */
  List<PushTargetRowDto> findByUsers(Collection<Long> userIds);
}
