package app.wearly.admin.adapter.in.web.request;

import app.wearly.code.application.port.in.dto.CreateCodeDto;
import java.util.Map;

/**
 * POST /admin/codes/{group} 본문 (07 문서 4장). 관리자 API의 JSON 모양은 여기서 정하고 code 컨텍스트의 입력 DTO로 바꾼다.
 * sortOrder가 없으면 그룹 맨 뒤, active가 없으면 true. 검증은 유스케이스가 한다
 */
public record CreateCodeRequest(
    String code,
    String label,
    String parent,
    Integer sortOrder,
    Boolean active,
    Map<String, Object> attrs) {

  public CreateCodeDto toDto() {
    return new CreateCodeDto(code, label, parent, sortOrder, active, attrs);
  }
}
