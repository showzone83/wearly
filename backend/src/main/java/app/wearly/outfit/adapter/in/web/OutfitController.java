package app.wearly.outfit.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.outfit.application.port.in.RecommendOutfitUseCase;
import app.wearly.outfit.application.port.in.dto.OutfitRecommendationDto;
import app.wearly.outfit.application.port.in.dto.RecommendOutfitDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 코디 추천 (09 문서 3장). 요청 본문은 유스케이스 명령 모양 그대로 받는다 (검증은 유스케이스) */
@RestController
@RequestMapping("/api/v1/outfits")
public class OutfitController {

  private final RecommendOutfitUseCase recommend;

  public OutfitController(RecommendOutfitUseCase recommend) {
    this.recommend = recommend;
  }

  @PostMapping("/recommendations")
  public OutfitRecommendationDto recommend(
      @CurrentUser long userId, @RequestBody RecommendOutfitDto body) {
    return recommend.recommend(userId, body);
  }
}
