package app.wearly.ai.application.service;

import app.wearly.ai.application.port.in.AiModelListUseCase;
import app.wearly.ai.application.port.in.dto.AiModelListDto;
import app.wearly.ai.application.port.out.AiModelCatalogPort;
import app.wearly.ai.application.port.out.AiProviderPort;
import app.wearly.ai.domain.AiModelEntry;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.ApiKeyRules;
import app.wearly.ai.domain.BuiltInModels;
import app.wearly.ai.domain.ModelListRules;
import app.wearly.common.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 공급자 모델 목록 (07 문서 4장). 키가 있으면 제공사 목록(AiModelCatalogPort)을 받아 공급자 ID별로 10분 캐시한다. 캐시는 종류·API 주소·키
 * 지문(SHA-256 앞부분, 키 값 아님)이 같을 때만 쓰고, 키를 바꾸거나 지우면(AiProviderAdminService) 비운다. 키가 없거나 실패하면 내장
 * 목록(BuiltInModels)과 그 이유를 준다. 실패는 캐시하지 않고 예전 목록도 버린다(다음 요청에서 다시 시도).
 */
@Service
public class AiModelListService implements AiModelListUseCase {

  static final Duration TTL = Duration.ofMinutes(10);

  private static final Logger log = LoggerFactory.getLogger(AiModelListService.class);

  private final AiProviderPort settings;
  private final ApiKeyService keys;
  private final AiModelCatalogPort catalog;
  private final BuiltInModels builtIn;
  private final Clock clock;

  /** provider_id → 마지막으로 받은 제공사 목록 */
  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  public AiModelListService(
      AiProviderPort settings,
      ApiKeyService keys,
      AiModelCatalogPort catalog,
      BuiltInModels builtIn,
      Clock clock) {
    this.settings = settings;
    this.keys = keys;
    this.catalog = catalog;
    this.builtIn = builtIn;
    this.clock = clock;
  }

  /**
   * 받은 목록과 그때의 설정 범위.
   *
   * @param scope 종류 | API 주소 옵션 | 키 지문. 키 값은 들고 있지 않는다
   */
  private record Cached(String scope, Instant fetchedAt, List<AiModelEntry> models) {}

  @Override
  public AiModelListDto models(String providerId, boolean refresh) {
    AiProviderSetting s = settings.findById(providerId).orElseThrow(ApiException::notFound);
    List<AiModelEntry> fallback = builtIn.of(s.type());
    Optional<String> key = keys.find(s);
    if (key.isEmpty()) {
      return fromCatalog(fallback, AiModelListDto.NO_API_KEY, "이 공급자에 API 키가 없어 제공사 목록을 받지 않았어요.");
    }
    String scope = scope(s, key.get());
    Instant now = clock.instant();
    Cached hit = cache.get(providerId);
    if (!refresh
        && hit != null
        && hit.scope().equals(scope)
        && now.isBefore(hit.fetchedAt().plus(TTL))) {
      return live(hit, fallback);
    }
    List<AiModelEntry> fetched;
    try {
      fetched = catalog.listModels(s, key.get());
    } catch (AiProviderException e) {
      // 메시지는 어댑터가 키를 지웠지만 응답 원문이 섞일 수 있어 사유 코드만 남긴다
      log.warn("AI 공급자 {}의 모델 목록을 받지 못해 내장 목록을 줘요: {}", providerId, e.reason());
      // 예전에 받은 목록도 버린다 (실패한 뒤에 옛 목록이 다시 나오지 않게, 다음 요청에서 다시 받는다)
      cache.remove(providerId);
      return fromCatalog(fallback, AiModelListDto.MODEL_LIST_FAILED, failureMessage(e.reason()));
    }
    Cached fresh = new Cached(scope, now, fetched);
    cache.put(providerId, fresh);
    return live(fresh, fallback);
  }

  /** 키를 바꾸거나 지웠을 때, 공급자 설정을 바꾸거나 지웠을 때 */
  public void invalidate(String providerId) {
    cache.remove(providerId);
  }

  private static String scope(AiProviderSetting s, String key) {
    return String.join(
        "|",
        s.type().name(),
        Objects.toString(s.options().get("baseUrl"), ""),
        ApiKeyRules.fingerprint(key));
  }

  private AiModelListDto live(Cached c, List<AiModelEntry> fallback) {
    return new AiModelListDto(
        AiModelListDto.LIVE,
        c.fetchedAt(),
        ModelListRules.withCatalog(c.models(), fallback).stream()
            .map(AiModelListService::model)
            .toList(),
        null);
  }

  private AiModelListDto fromCatalog(List<AiModelEntry> fallback, String code, String message) {
    return new AiModelListDto(
        AiModelListDto.CATALOG,
        clock.instant(),
        ModelListRules.sorted(fallback).stream().map(AiModelListService::model).toList(),
        new AiModelListDto.ErrorInfo(code, message));
  }

  private static AiModelListDto.Model model(AiModelEntry m) {
    return new AiModelListDto.Model(m.id(), m.displayName(), m.createdAt(), m.recommended());
  }

  /** 어댑터의 사유 코드 → 화면 문구. 제공사 응답 원문·키는 넣지 않는다 */
  static String failureMessage(String reason) {
    String r = reason == null ? "" : reason;
    if (r.equals("API_401") || r.equals("API_403")) {
      return "제공사가 API 키를 받아들이지 않았어요 (HTTP " + r.substring(4) + ").";
    }
    if (r.matches("API_\\d{3}")) {
      return "제공사 모델 목록 조회가 실패했어요 (HTTP " + r.substring(4) + ").";
    }
    return switch (r) {
      case "TIMEOUT" -> "제공사 응답이 늦어 모델 목록을 받지 못했어요.";
      case "API_CLIENT" -> "제공사에 연결하지 못해 모델 목록을 받지 못했어요.";
      case "BAD_RESPONSE" -> "제공사 모델 목록 응답을 읽지 못했어요.";
      default -> "제공사 모델 목록을 받지 못했어요.";
    };
  }
}
