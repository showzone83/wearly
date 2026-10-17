package app.wearly.wardrobe.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.PathIds;
import app.wearly.wardrobe.adapter.in.web.WardrobeDtos.BulkRequest;
import app.wearly.wardrobe.adapter.in.web.WardrobeDtos.ItemList;
import app.wearly.wardrobe.adapter.in.web.WardrobeDtos.ItemRequest;
import app.wearly.wardrobe.application.port.in.WardrobeStatsUseCase;
import app.wearly.wardrobe.application.port.in.WardrobeUseCase;
import app.wearly.wardrobe.application.port.in.dto.ItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemSearchDto;
import app.wearly.wardrobe.application.port.in.dto.WardrobeStatsDto;
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
  public ItemList list(
      @CurrentUser long userId,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String color,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String sort) {
    return new ItemList(wardrobe.list(new ItemSearchDto(userId, category, status, color, q, sort)));
  }

  @GetMapping("/items/{id}")
  public ItemDto get(@CurrentUser long userId, @PathVariable String id) {
    return wardrobe.get(userId, PathIds.parse(id));
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  public ItemDto create(@CurrentUser long userId, @Valid @RequestBody ItemRequest input) {
    return wardrobe.create(userId, input.toInput());
  }

  @PostMapping("/items/bulk")
  @ResponseStatus(HttpStatus.CREATED)
  public ItemList bulk(@CurrentUser long userId, @Valid @RequestBody BulkRequest request) {
    return new ItemList(
        wardrobe.bulkCreate(userId, request.items().stream().map(ItemRequest::toInput).toList()));
  }

  @PatchMapping("/items/{id}")
  public ItemDto patch(
      @CurrentUser long userId, @PathVariable String id, @RequestBody JsonNode patch) {
    return wardrobe.patch(
        userId, PathIds.parse(id), new JsonItemPatch(patch, jsonMapper, validator));
  }

  @DeleteMapping("/items/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@CurrentUser long userId, @PathVariable String id) {
    wardrobe.delete(userId, PathIds.parse(id));
  }

  @GetMapping("/stats")
  public WardrobeStatsDto stats(@CurrentUser long userId) {
    return stats.stats(userId);
  }
}
