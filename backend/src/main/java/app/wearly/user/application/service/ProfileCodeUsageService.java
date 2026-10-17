package app.wearly.user.application.service;

import app.wearly.user.application.port.in.ProfileCodeUsageUseCase;
import app.wearly.user.application.port.in.dto.ProfileCodeCountDto;
import app.wearly.user.application.port.out.ProfileQueryPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로필의 코드 사용 수 (MyBatis). code 컨텍스트가 CodeUsageSource로 묻는다 (H1) */
@Service
public class ProfileCodeUsageService implements ProfileCodeUsageUseCase {

  private final ProfileQueryPort queries;

  public ProfileCodeUsageService(ProfileQueryPort queries) {
    this.queries = queries;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProfileCodeCountDto> countByCode() {
    return queries.countByCode().stream()
        .map(c -> new ProfileCodeCountDto(c.groupCode(), c.code(), c.count()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countProfilesUsing(String group, String code) {
    return queries.countUsing(group, code);
  }
}
