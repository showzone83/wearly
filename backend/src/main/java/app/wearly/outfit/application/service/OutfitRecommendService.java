package app.wearly.outfit.application.service;

import app.wearly.ai.application.port.in.AiFeatures;
import app.wearly.ai.application.port.in.GeneratedJson;
import app.wearly.ai.application.port.in.JsonGenerationCommand;
import app.wearly.ai.application.port.in.JsonGenerationUseCase;
import app.wearly.ai.application.port.in.JsonOutputHandler;
import app.wearly.ai.application.port.in.RejectedOutputException;
import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.code.CodeSnapshot;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.outfit.application.port.in.OutfitRecommendation;
import app.wearly.outfit.application.port.in.RecommendOutfitCommand;
import app.wearly.outfit.application.port.in.RecommendOutfitUseCase;
import app.wearly.outfit.domain.CandidateValidator;
import app.wearly.outfit.domain.MockOutfitPlanner;
import app.wearly.outfit.domain.OutfitCandidate;
import app.wearly.outfit.domain.OutfitItem;
import app.wearly.outfit.domain.OutfitPool;
import app.wearly.outfit.domain.OutfitPrompt;
import app.wearly.outfit.domain.OutfitRequest;
import app.wearly.outfit.domain.OutfitSettings;
import app.wearly.outfit.domain.OutfitTaste;
import app.wearly.user.application.port.in.ProfileTaste;
import app.wearly.user.application.port.in.ProfileUseCase;
import app.wearly.wardrobe.application.port.in.ListItemsQuery;
import app.wearly.wardrobe.application.port.in.WardrobeUseCase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * AI 코디 추천 (09 문서). 옷장·프로필·코드는 각 컨텍스트의 입력 포트로 읽고, 공급자 호출·기록은 ai 컨텍스트의 JsonGenerationUseCase에 맡긴다.
 * 프롬프트·스키마·후보 검증은 이 컨텍스트(공급자와 무관)에 있다.
 *
 * <p>순서: 요청 검증 400 → 고정·중심 아이템 확인 400 → 옷 부족 400 WARDROBE_INSUFFICIENT → 일일 한도 429 → 공급자 시도 → 모두 실패
 * 502. AI를 부르기 전 단계에서 끝나면 기록을 남기지 않는다.
 */
@Service
public class OutfitRecommendService implements RecommendOutfitUseCase {

  private final WardrobeUseCase wardrobe;
  private final ProfileUseCase profiles;
  private final CodeCatalog catalog;
  private final JsonGenerationUseCase generation;
  private final OutfitSettings settings;

  public OutfitRecommendService(
      WardrobeUseCase wardrobe,
      ProfileUseCase profiles,
      CodeCatalog catalog,
      JsonGenerationUseCase generation,
      OutfitSettings settings) {
    this.wardrobe = wardrobe;
    this.profiles = profiles;
    this.catalog = catalog;
    this.generation = generation;
    this.settings = settings;
  }

  @Override
  public OutfitRecommendation recommend(long userId, RecommendOutfitCommand command) {
    CodeSnapshot codes = catalog.current();
    OutfitRequest request = OutfitRequestValidator.validate(command, codes);
    List<OutfitItem> items =
        wardrobe.list(new ListItemsQuery(userId, null, null, null, null, null)).stream()
            .map(
                v ->
                    OutfitItem.of(
                        v.id(),
                        v.name(),
                        v.category(),
                        v.subType(),
                        v.color(),
                        v.pattern(),
                        v.seasons(),
                        v.styles(),
                        v.formality(),
                        v.status(),
                        v.wornCount(),
                        v.lastWornAt(),
                        codes))
            .toList();
    ApiException.throwIfAny(OutfitPool.checkPins(request, items));
    OutfitPool pool = OutfitPool.of(request, items);
    List<String> missing = pool.missing();
    if (!missing.isEmpty()) {
      throw new ApiException(
          ErrorCode.WARDROBE_INSUFFICIENT, "추천할 수 있는 옷이 모자라요: " + String.join(", ", missing));
    }

    int limit = settings.dailyLimit();
    long used = generation.successToday(userId, AiFeatures.OUTFIT_RECOMMEND);
    if (used >= limit) {
      throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED, "오늘 AI 코디 추천 횟수를 모두 썼어요.");
    }

    ProfileTaste taste = profiles.taste(userId);
    JsonGenerationCommand generate =
        new JsonGenerationCommand(
            userId,
            AiFeatures.OUTFIT_RECOMMEND,
            OutfitPrompt.SCHEMA_NAME,
            OutfitPrompt.instructions(),
            OutfitPrompt.prompt(
                pool, new OutfitTaste(taste.dressCodes(), taste.moods(), taste.colors()), codes),
            OutfitPrompt.schema(pool),
            MockOutfitPlanner.plan(pool));
    GeneratedJson<List<OutfitCandidate>> result;
    try {
      result = generation.generate(generate, json -> accept(json, pool));
    } catch (ApiException e) {
      if (e.code() == ErrorCode.AI_UNAVAILABLE) {
        throw new ApiException(ErrorCode.AI_UNAVAILABLE, "AI 코디 추천을 지금 받을 수 없어요. 잠시 후 다시 시도해 주세요.");
      }
      throw e;
    }
    int remaining = (int) Math.max(0, limit - (used + 1));
    return new OutfitRecommendation(result.value(), result.provider(), result.model(), remaining);
  }

  /** 후보 검증. 남은 후보가 0개면 거부해 다음 공급자로 넘어간다 */
  private static JsonOutputHandler.Accepted<List<OutfitCandidate>> accept(
      Map<String, Object> json, OutfitPool pool) {
    CandidateValidator.Result r = CandidateValidator.validate(json, pool);
    if (r.accepted().isEmpty()) {
      String why = r.rejections().isEmpty() ? "후보가 없어요." : String.join(" / ", r.rejections());
      throw new RejectedOutputException("남은 후보가 없어요: " + why);
    }
    Map<String, Object> log = new LinkedHashMap<>();
    log.put("candidates", r.accepted().stream().map(OutfitCandidate::toMap).toList());
    log.put("dropped", r.rejections());
    return new JsonOutputHandler.Accepted<>(r.accepted(), log);
  }
}
