package app.wearly.user.adapter.out.persistence.mybatis;

import app.wearly.user.adapter.out.persistence.mybatis.mapper.ProfileMapper;
import app.wearly.user.application.port.out.ProfileQueryPort;
import app.wearly.user.application.port.out.dto.CodeCountDto;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/** ProfileQueryPort 구현 (MyBatis). 매퍼가 port.out.dto를 바로 돌려준다 */
@Component
class ProfileMybatisAdapter implements ProfileQueryPort {

  private final ProfileMapper mapper;

  ProfileMybatisAdapter(ProfileMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<CodeCountDto> countByCode() {
    return mapper.countByCode();
  }

  @Override
  public long countUsing(String group, String code) {
    return mapper.countUsing(group, code);
  }

  @Override
  public List<Long> findDayBeforeEnabled(Collection<Long> userIds) {
    return userIds.isEmpty() ? List.of() : mapper.findDayBeforeEnabled(userIds);
  }
}
