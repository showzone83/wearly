package app.wearly.wardrobe.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.PathIds;
import app.wearly.wardrobe.adapter.in.web.request.ItemRequest;
import app.wearly.wardrobe.adapter.in.web.request.PatchItemRequest;
import app.wearly.wardrobe.adapter.in.web.request.RetireItemRequest;
import app.wearly.wardrobe.adapter.in.web.response.ItemListResponse;
import app.wearly.wardrobe.adapter.in.web.response.ItemResponse;
import app.wearly.wardrobe.application.port.in.OwnerWardrobeUseCase;
import app.wearly.wardrobe.application.port.in.dto.ItemSearchDto;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 옷장 도우미가 주인 옷장을 쓴다 (26 문서 4.6). 요청·응답 모양은 /wardrobe와 같다. 연결이 없으면 404, 보기 권한으로 바꾸려 하면 403. 완전 삭제·여러
 * 벌 등록·통계는 없다. 사진 분석은 ai 컨텍스트의 OwnerAiAnalyzeController가 받는다
 */
@RestController
@RequestMapping("/api/v1/owners/{ownerId}/wardrobe")
public class OwnerWardrobeController {

  private final OwnerWardrobeUseCase wardrobe;
  private final JsonMapper jsonMapper;
  private final Validator validator;

  public OwnerWardrobeController(
      OwnerWardrobeUseCase wardrobe, JsonMapper jsonMapper, Validator validator) {
    this.wardrobe = wardrobe;
    this.jsonMapper = jsonMapper;
    this.validator = validator;
  }

  @GetMapping("/items")
  public ItemListResponse list(
      @CurrentUser long userId,
      @PathVariable String ownerId,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String color,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String retired) {
    return ItemListResponse.from(
        wardrobe.list(
            userId,
            new ItemSearchDto(PathIds.parse(ownerId), category, status, color, q, sort, retired)));
  }

  @GetMapping("/items/{id}")
  public ItemResponse get(
      @CurrentUser long userId, @PathVariable String ownerId, @PathVariable String id) {
    return ItemResponse.from(wardrobe.get(userId, PathIds.parse(ownerId), PathIds.parse(id)));
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  public ItemResponse create(
      @CurrentUser long userId,
      @PathVariable String ownerId,
      @Valid @RequestBody ItemRequest input) {
    return ItemResponse.from(wardrobe.create(userId, PathIds.parse(ownerId), input.toDto()));
  }

  @PatchMapping("/items/{id}")
  public ItemResponse patch(
      @CurrentUser long userId,
      @PathVariable String ownerId,
      @PathVariable String id,
      @RequestBody JsonNode patch) {
    return ItemResponse.from(
        wardrobe.patch(
            userId,
            PathIds.parse(ownerId),
            PathIds.parse(id),
            PatchItemRequest.read(patch, jsonMapper, validator)));
  }

  /** 사용하지 않는 옷으로 옮기기. 본문이 없으면 reason 필수 오류 */
  @PostMapping("/items/{id}/retire")
  public ItemResponse retire(
      @CurrentUser long userId,
      @PathVariable String ownerId,
      @PathVariable String id,
      @RequestBody(required = false) RetireItemRequest request) {
    return ItemResponse.from(
        wardrobe.retire(
            userId,
            PathIds.parse(ownerId),
            PathIds.parse(id),
            request == null ? new RetireItemRequest(null, null).toDto() : request.toDto()));
  }

  /** 다시 사용하기. 사용 중이면 그대로 200 */
  @PostMapping("/items/{id}/restore")
  public ItemResponse restore(
      @CurrentUser long userId, @PathVariable String ownerId, @PathVariable String id) {
    return ItemResponse.from(wardrobe.restore(userId, PathIds.parse(ownerId), PathIds.parse(id)));
  }
}
