package app.wearly.user.application.port.in;

import app.wearly.user.application.port.in.dto.ProfileCodeCountDto;
import java.util.List;

/**
 * 프로필의 코드 사용 수 (모든 회원). code 컨텍스트의 삭제 가드가 입력 어댑터(adapter.in.code)를 거쳐 부른다 (H1). 프로필의 어느 칸이 어느 코드
 * 그룹을 담는지는 user 컨텍스트만 안다 (colors = COLOR, moods = STYLE).
 */
public interface ProfileCodeUsageUseCase {

  List<ProfileCodeCountDto> countByCode();

  long countProfilesUsing(String group, String code);
}
