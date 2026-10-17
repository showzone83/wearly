package app.wearly.outfit.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.outfit.adapter.in.web.request.RecommendOutfitRequest;
import app.wearly.outfit.adapter.in.web.response.OutfitRecommendationResponse;
import app.wearly.outfit.application.port.in.RecommendOutfitUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 코디 추천 (09 문서 3장). 요청 형식 검증은 유스케이스가 한다 (오류를 모두 모아 400) */
@RestController
@RequestMapping("/api/v1/outfits")
public class OutfitController {

  private final RecommendOutfitUseCase recommend;

  public OutfitController(RecommendOutfitUseCase recommend) {
    this.recommend = recommend;
  }

  @PostMapping("/recommendations")
  public OutfitRecommendationResponse recommend(
      @CurrentUser long userId, @RequestBody RecommendOutfitRequest body) {
    return OutfitRecommendationResponse.from(recommend.recommend(userId, body.toDto()));
  }
}
