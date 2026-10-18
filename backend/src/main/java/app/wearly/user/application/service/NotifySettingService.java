package app.wearly.user.application.service;

import app.wearly.user.application.port.in.NotifySettingUseCase;
import app.wearly.user.application.port.out.ProfileQueryPort;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 알림 설정 조회 (MyBatis). 한 번에 많이 묻지 않도록 500명씩 나눠 묻는다 */
@Service
public class NotifySettingService implements NotifySettingUseCase {

  static final int CHUNK = 500;

  private final ProfileQueryPort queries;

  public NotifySettingService(ProfileQueryPort queries) {
    this.queries = queries;
  }

  @Override
  @Transactional(readOnly = true)
  public Set<Long> dayBeforeEnabled(Collection<Long> userIds) {
    Set<Long> out = new LinkedHashSet<>();
    var ids = userIds.stream().distinct().toList();
    for (int i = 0; i < ids.size(); i += CHUNK) {
      out.addAll(queries.findDayBeforeEnabled(ids.subList(i, Math.min(ids.size(), i + CHUNK))));
    }
    return out;
  }
}
