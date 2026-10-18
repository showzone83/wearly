package app.wearly.appsetting.application.port.in.dto;

import app.wearly.appsetting.domain.ThemeSetting;
import java.time.Instant;

/**
 * 테마 설정 (유스케이스 출력). 바꾼 적이 없으면 updatedAt·updatedBy는 null, updatedBy는 관리자 회원 ID. seasonMode는 계절 테마 모드
 * 이름(OFF · AUTO · SPRING · SUMMER · FALL · WINTER)
 */
public record ThemeSettingDto(
    String themeId, String seasonMode, Instant updatedAt, Long updatedBy) {

  public static ThemeSettingDto from(ThemeSetting s) {
    return new ThemeSettingDto(s.themeId(), s.seasonMode().name(), s.updatedAt(), s.updatedBy());
  }
}
