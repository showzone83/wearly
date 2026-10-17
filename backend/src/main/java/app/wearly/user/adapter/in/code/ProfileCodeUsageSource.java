package app.wearly.user.adapter.in.code;

import app.wearly.code.application.port.in.CodeUsageSource;
import app.wearly.code.application.port.in.dto.CodeUsageCountDto;
import app.wearly.user.application.port.in.ProfileCodeUsageUseCase;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * code 컨텍스트가 정의한 코드 사용처(CodeUsageSource)를 프로필로 구현한다 (H1). code가 이 어댑터를 통해 user 유스케이스를 부르는 입력 어댑터라서,
 * 의존은 user → code 한 방향이다.
 */
@Component
class ProfileCodeUsageSource implements CodeUsageSource {

  private final ProfileCodeUsageUseCase usage;

  ProfileCodeUsageSource(ProfileCodeUsageUseCase usage) {
    this.usage = usage;
  }

  @Override
  public Kind kind() {
    return Kind.PROFILE;
  }

  @Override
  public List<CodeUsageCountDto> countByCode() {
    return usage.countByCode().stream()
        .map(c -> new CodeUsageCountDto(c.group(), c.code(), c.count()))
        .toList();
  }

  @Override
  public long countUsing(String group, String code) {
    return usage.countProfilesUsing(group, code);
  }
}
