package app.wearly.wardrobe.adapter.in.web.request;

import app.wearly.wardrobe.application.port.in.dto.RetireItemDto;

/**
 * POST /wardrobe/items/{id}/retire 본문 (13 문서 3장). reason은 RETIRE_REASON 코드, memo는 선택(200자 이하). 검증은
 * 유스케이스가 한다(아이템이 없으면 404가 먼저).
 */
public record RetireItemRequest(String reason, String memo) {

  public RetireItemDto toDto() {
    return new RetireItemDto(reason, memo);
  }
}
