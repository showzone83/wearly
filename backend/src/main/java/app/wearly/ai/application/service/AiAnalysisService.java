package app.wearly.ai.application.service;

import app.wearly.ai.application.port.in.AnalyzeImageUseCase;
import app.wearly.ai.application.port.in.JsonOutputHandler;
import app.wearly.ai.application.port.in.ProviderTestUseCase;
import app.wearly.ai.application.port.in.dto.AiSuggestionDto;
import app.wearly.ai.application.port.in.dto.AnalyzeResultDto;
import app.wearly.ai.application.port.in.dto.ProviderTestResultDto;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.application.port.out.AiProviderPort;
import app.wearly.ai.domain.AiCallRecord;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiImages;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiSettings;
import app.wearly.ai.domain.AiSuggestion;
import app.wearly.ai.domain.AiSuggestionValidator;
import app.wearly.ai.domain.AnalysisPrompt;
import app.wearly.ai.domain.ImageTypes;
import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.application.ImageUpload;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.image.application.port.in.ImageContentUseCase;
import app.wearly.image.application.port.in.dto.ImageContentDto;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 사진 분석: 형식·크기 검사 → 일일 한도 확인(MyBatis 집계) → 공급자를 우선순위 순으로 시도 → 기록(JPA). 앞 공급자가 실패하면 다음 공급자로 넘어가고,
 * 실패도 각각 기록한다. 성공한 ITEM_ANALYZE만 한도에 센다 (Asia/Seoul 날짜).
 */
@Service
public class AiAnalysisService implements AnalyzeImageUseCase, ProviderTestUseCase {

  public static final long MAX_IMAGE_BYTES = AiImages.MAX_IMAGE_BYTES;

  private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

  /** 한도·장애 응답의 params: 어느 기능인지 (코디 추천과 문구를 나눌 수 있게) */
  private static final Map<String, Object> FEATURE_PARAMS =
      Map.of("feature", AiCallRecord.FEATURE_ITEM_ANALYZE);

  private final AiProviderRegistry registry;
  private final AiProviderPort providers;
  private final CodeCatalog catalog;
  private final AiQuotaService quota;
  private final AiCallRunner runner;
  private final AiSettings settings;
  private final ImageContentUseCase storedImages;

  public AiAnalysisService(
      AiProviderRegistry registry,
      AiProviderPort providers,
      CodeCatalog catalog,
      AiQuotaService quota,
      AiCallRunner runner,
      AiSettings settings,
      ImageContentUseCase storedImages) {
    this.registry = registry;
    this.providers = providers;
    this.catalog = catalog;
    this.quota = quota;
    this.runner = runner;
    this.settings = settings;
    this.storedImages = storedImages;
  }

  @Override
  public AnalyzeResultDto analyze(long userId, ImageUpload upload) {
    AiImage image = readImage(upload);

    int limit = settings.dailyLimit();
    AiQuotaService.Reservation reservation =
        quota
            .reserve(userId, AiCallRecord.FEATURE_ITEM_ANALYZE, limit)
            .orElseThrow(() -> new BusinessException(ErrorCode.AI_LIMIT_EXCEEDED, FEATURE_PARAMS));
    try {
      List<AiModelPort> candidates = registry.candidates();
      if (candidates.isEmpty()) {
        log.warn("AI 분석 불가: 쓸 수 있는 AI 공급자가 없음 (관리자 화면에서 공급자·모델·키 환경변수를 확인)");
        throw new BusinessException(ErrorCode.AI_UNAVAILABLE, FEATURE_PARAMS);
      }
      CodeSnapshot codes = catalog.current();
      AiCallRunner.Call call =
          new AiCallRunner.Call(
              userId,
              AiCallRecord.FEATURE_ITEM_ANALYZE,
              AnalysisPrompt.request(image, codes),
              image);
      AiCallRunner.Attempt<AiSuggestion> ok =
          runner
              .first(candidates, call, json -> suggestion(json, codes))
              .orElseThrow(() -> new BusinessException(ErrorCode.AI_UNAVAILABLE, FEATURE_PARAMS));
      int remaining = (int) Math.max(0, limit - reservation.used());
      return new AnalyzeResultDto(
          AiSuggestionDto.from(ok.value()), ok.provider(), ok.model(), remaining);
    } finally {
      quota.release(reservation);
    }
  }

  @Override
  public AnalyzeResultDto analyzeStored(long userId, String imageId) {
    ImageContentDto stored = storedImages.readOriginal(userId, imageId);
    return analyze(
        userId,
        new ImageUpload(stored.bytes(), stored.contentType(), stored.bytes().length, true, false));
  }

  @Override
  public ProviderTestResultDto test(long adminUserId, String providerId, ImageUpload upload) {
    AiProviderSetting setting =
        providers.findById(providerId).orElseThrow(BusinessException::notFound);
    AiImage image = readImage(upload);
    AiModelPort provider;
    try {
      provider = registry.forTest(setting);
    } catch (AiProviderException e) {
      // 모델·키가 없으면 호출하지 않는다 (기록도 남기지 않음)
      return new ProviderTestResultDto(
          false, null, e.reason(), e.getMessage(), 0, null, null, setting.model());
    }
    CodeSnapshot codes = catalog.current();
    AiCallRunner.Attempt<AiSuggestion> a =
        runner.attempt(
            provider,
            new AiCallRunner.Call(
                adminUserId,
                AiCallRecord.FEATURE_ADMIN_TEST,
                AnalysisPrompt.request(image, codes),
                image),
            json -> suggestion(json, codes));
    if (a.ok()) {
      return new ProviderTestResultDto(
          true,
          AiSuggestionDto.from(a.value()),
          null,
          null,
          a.latencyMs(),
          a.inputTokens(),
          a.outputTokens(),
          a.model());
    }
    return new ProviderTestResultDto(
        false, null, a.errorCode(), a.errorMessage(), a.latencyMs(), null, null, provider.model());
  }

  /** 사진 분석 응답 검증 (도메인 규칙). 규칙 위반은 INVALID_OUTPUT으로 기록하고 다음 공급자로 */
  private static JsonOutputHandler.Accepted<AiSuggestion> suggestion(
      Map<String, Object> json, CodeSnapshot codes) {
    AiSuggestion s = AiSuggestionValidator.validate(json, codes);
    return new JsonOutputHandler.Accepted<>(s, s, summary(s));
  }

  /** 관리자 AI 기록 목록의 요약: "네이비 울 니트 · TOP/KNIT · navy" (4차까지 관리자 서비스가 만들던 문구와 같다) */
  static String summary(AiSuggestion s) {
    return Objects.toString(s.name(), "")
        + " · "
        + s.category()
        + "/"
        + s.subType()
        + " · "
        + s.color();
  }

  /** 오늘(한국 시간) 성공한 사진 분석 수 (관리자 시험 분석 제외) */
  public long usedToday(long userId) {
    return quota.successToday(userId, AiCallRecord.FEATURE_ITEM_ANALYZE);
  }

  /** 검사 순서: 파트 없음·빈 파일 → 10MB 초과 → 읽기 실패 → 형식 */
  private static AiImage readImage(ImageUpload file) {
    if (file == null || file.isEmpty()) {
      throw BusinessException.validation(FieldError.required("image"));
    }
    if (file.size() > MAX_IMAGE_BYTES) {
      throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
    }
    if (file.unreadable()) {
      throw BusinessException.validation(FieldError.invalidFormat("image"));
    }
    byte[] bytes = file.bytes();
    String mediaType =
        ImageTypes.resolve(file.contentType(), bytes)
            .orElseThrow(
                () ->
                    BusinessException.validation(FieldError.notAllowed("image", ImageTypes.NAMES)));
    return new AiImage(bytes, mediaType, sha256(bytes));
  }

  public static String sha256(byte[] bytes) {
    return AiImages.sha256(bytes);
  }
}
