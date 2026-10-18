package app.wearly.notify.adapter.out.persistence.mybatis;

import app.wearly.notify.adapter.out.persistence.mybatis.mapper.PushTokenMapper;
import app.wearly.notify.application.port.out.PushTokenQueryPort;
import app.wearly.notify.application.port.out.dto.PushTargetRowDto;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/** PushTokenQueryPort 구현 (MyBatis). IN 목록이 너무 길지 않게 500명씩 나눠 묻는다 */
@Component
class PushTokenMybatisAdapter implements PushTokenQueryPort {

  static final int CHUNK = 500;

  private final PushTokenMapper mapper;

  PushTokenMybatisAdapter(PushTokenMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<PushTargetRowDto> findByUsers(Collection<Long> userIds) {
    List<Long> ids = userIds.stream().distinct().toList();
    List<PushTargetRowDto> out = new ArrayList<>();
    for (int i = 0; i < ids.size(); i += CHUNK) {
      out.addAll(mapper.findByUsers(ids.subList(i, Math.min(ids.size(), i + CHUNK))));
    }
    return out;
  }
}
