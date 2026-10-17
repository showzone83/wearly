package app.wearly.appsetting.application.port.in.dto;

import app.wearly.appsetting.domain.ThemeSetting;
import java.time.Instant;

/** 테마 설정 (유스케이스 출력). 바꾼 적이 없으면 updatedAt·updatedBy는 null, updatedBy는 관리자 회원 ID */
public record ThemeSettingDto(String themeId, Instant updatedAt, Long updatedBy) {

  public static ThemeSettingDto from(ThemeSetting s) {
    return new ThemeSettingDto(s.themeId(), s.updatedAt(), s.updatedBy());
  }
}
