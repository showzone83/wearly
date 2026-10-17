package app.wearly.wardrobe.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.PathIds;
import app.wearly.wardrobe.adapter.in.web.request.BulkItemsRequest;
import app.wearly.wardrobe.adapter.in.web.request.ItemRequest;
import app.wearly.wardrobe.adapter.in.web.request.PatchItemRequest;
import app.wearly.wardrobe.adapter.in.web.request.RetireItemRequest;
import app.wearly.wardrobe.adapter.in.web.response.ItemListResponse;
import app.wearly.wardrobe.adapter.in.web.response.ItemResponse;
import app.wearly.wardrobe.adapter.in.web.response.WardrobeStatsResponse;
import app.wearly.wardrobe.application.port.in.WardrobeStatsUseCase;
import app.wearly.wardrobe.application.port.in.WardrobeUseCase;
import app.wearly.wardrobe.application.port.in.dto.ItemSearchDto;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** 옷장 API (05 문서 6장). 사진 분석(POST /items/analyze)은 ai 컨텍스트의 AiAnalyzeController가 받는다 */
@RestController
@RequestMapping("/api/v1/wardrobe")
public class WardrobeController {

  private final WardrobeUseCase wardrobe;
  private final WardrobeStatsUseCase stats;
  private final JsonMapper jsonMapper;
  private final Validator validator;

  public WardrobeController(
      WardrobeUseCase wardrobe,
      WardrobeStatsUseCase stats,
      JsonMapper jsonMapper,
      Validator validator) {
    this.wardrobe = wardrobe;
    this.stats = stats;
    this.jsonMapper = jsonMapper;
    this.validator = validator;
  }

  @GetMapping("/items")
  public ItemListResponse list(
      @CurrentUser long userId,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String color,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String retired) {
    return ItemListResponse.from(
        wardrobe.list(new ItemSearchDto(userId, category, status, color, q, sort, retired)));
  }

  @GetMapping("/items/{id}")
  public ItemResponse get(@CurrentUser long userId, @PathVariable String id) {
    return ItemResponse.from(wardrobe.get(userId, PathIds.parse(id)));
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  public ItemResponse create(@CurrentUser long userId, @Valid @RequestBody ItemRequest input) {
    return ItemResponse.from(wardrobe.create(userId, input.toDto()));
  }

  @PostMapping("/items/bulk")
  @ResponseStatus(HttpStatus.CREATED)
  public ItemListResponse bulk(
      @CurrentUser long userId, @Valid @RequestBody BulkItemsRequest request) {
    return ItemListResponse.from(wardrobe.bulkCreate(userId, request.toDto()));
  }

  @PatchMapping("/items/{id}")
  public ItemResponse patch(
      @CurrentUser long userId, @PathVariable String id, @RequestBody JsonNode patch) {
    return ItemResponse.from(
        wardrobe.patch(
            userId, PathIds.parse(id), PatchItemRequest.read(patch, jsonMapper, validator)));
  }

  /** 사용하지 않는 옷으로 옮기기 (13 문서 3장). 본문이 없으면 reason 필수 오류 */
  @PostMapping("/items/{id}/retire")
  public ItemResponse retire(
      @CurrentUser long userId,
      @PathVariable String id,
      @RequestBody(required = false) RetireItemRequest request) {
    return ItemResponse.from(
        wardrobe.retire(
            userId,
            PathIds.parse(id),
            request == null ? new RetireItemRequest(null, null).toDto() : request.toDto()));
  }

  /** 다시 사용하기. 사용 중이면 그대로 200 */
  @PostMapping("/items/{id}/restore")
  public ItemResponse restore(@CurrentUser long userId, @PathVariable String id) {
    return ItemResponse.from(wardrobe.restore(userId, PathIds.parse(id)));
  }

  /** 완전 삭제 (되돌릴 수 없다) */
  @DeleteMapping("/items/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@CurrentUser long userId, @PathVariable String id) {
    wardrobe.delete(userId, PathIds.parse(id));
  }

  @GetMapping("/stats")
  public WardrobeStatsResponse stats(@CurrentUser long userId) {
    return WardrobeStatsResponse.from(stats.stats(userId));
  }
}
