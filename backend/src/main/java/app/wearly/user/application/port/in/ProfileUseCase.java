package app.wearly.user.application.port.in;

import app.wearly.user.application.port.in.dto.ProfileDto;
import app.wearly.user.application.port.in.dto.ProfileTasteDto;
import app.wearly.user.application.port.in.dto.UpdateProfileDto;

public interface ProfileUseCase {

  /** 저장된 프로필이 없으면 기본값 (이름은 회원 이름) */
  ProfileDto get(long userId);

  /** PUT: 프로필 전체를 바꾸고 회원 이름도 함께 바꾼다. 색·취향은 코드 테이블로 검증한다 */
  ProfileDto put(long userId, UpdateProfileDto profile);

  /** 취향 (드레스 코드·선호 스타일·선호 색). 저장된 프로필이 없으면 기본값 */
  ProfileTasteDto taste(long userId);
}
