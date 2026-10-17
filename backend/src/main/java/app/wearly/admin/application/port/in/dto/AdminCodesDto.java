package app.wearly.admin.application.port.in.dto;

import java.util.List;
import java.util.Map;

/** 관리자 코드 목록 (유스케이스 출력): /codes 모양 + 그룹 description, 코드별 usageCount */
public record AdminCodesDto(String version, List<GroupDto> groups) {

  public record GroupDto(String group, String name, String description, List<AdminCodeDto> codes) {}

  /** usageCount = 이 코드를 쓰는 아이템 수 */
  public record AdminCodeDto(
      String code,
      String label,
      String parent,
      int sortOrder,
      boolean active,
      Map<String, Object> attrs,
      long usageCount) {}
}
