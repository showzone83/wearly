package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminCodesDto.AdminCodeDto;
import java.util.Map;

/** 관리자 코드 한 줄 (07 문서 4장). usageCount = 이 코드를 쓰는 아이템 수 */
public record AdminCodeResponse(
    String code,
    String label,
    String parent,
    int sortOrder,
    boolean active,
    Map<String, Object> attrs,
    long usageCount) {

  public static AdminCodeResponse from(AdminCodeDto c) {
    return new AdminCodeResponse(
        c.code(), c.label(), c.parent(), c.sortOrder(), c.active(), c.attrs(), c.usageCount());
  }
}
