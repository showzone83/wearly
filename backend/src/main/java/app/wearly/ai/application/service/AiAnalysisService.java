package app.wearly.ai.application.service;

import app.wearly.ai.application.port.in.AnalyzeImageUseCase;
import app.wearly.ai.application.port.in.AnalyzeResult;
import app.wearly.ai.application.port.in.ProviderTestResult;
import app.wearly.ai.application.port.in.ProviderTestUseCase;
import app.wearly.ai.application.port.out.AiCallLogPort;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.application.port.out.AiProviderPort;
import app.wearly.ai.application.port.out.AiUsagePort;
import app.wearly.ai.domain.AiCallRecord;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiImages;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiResult;
import app.wearly.ai.domain.AiSettings;
import app.wearly.ai.domain.ImageTypes;
import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.ImageUpload;
import app.wearly.common.domain.Zones;
import app.wearly.common.domain.code.CodeSnapshot;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 사진 분석: 형식·크기 검사 → 일일 한도 확인(MyBatis 집계) → 공급자를 우선순위 순으로 시도 → 기록(JPA).
 * 앞 공급자가 실패하면 다음 공급자로 넘어가고, 실패도 각각 기록한다. 성공한 ITEM_ANALYZE만 한도에 센다 (Asia/Seoul 날짜).
 */
@Service
public class AiAnalysisService implements AnalyzeImageUseCase, ProviderTestUseCase {

    public static final long MAX_IMAGE_BYTES = AiImages.MAX_IMAGE_BYTES;

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final AiProviderRegistry registry;
    private final AiProviderPort providers;
    private final CodeCatalog catalog;
    private final AiUsagePort usage;
    private final AiCallLogPort logs;
    private final AiSettings settings;
    private final Clock clock;

    public AiAnalysisService(AiProviderRegistry registry, AiProviderPort providers, CodeCatalog catalog, AiUsagePort usage,
            AiCallLogPort logs, AiSettings settings, Clock clock) {
        this.registry = registry;
        this.providers = providers;
        this.catalog = catalog;
        this.usage = usage;
        this.logs = logs;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public AnalyzeResult analyze(long userId, ImageUpload upload) {
        AiImage image = readImage(upload);

        int limit = settings.dailyLimit();
        long used = usedToday(userId);
        if (used >= limit) {
            throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED);
        }

        List<AiModelPort> candidates = registry.candidates();
        if (candidates.isEmpty()) {
            log.warn("AI 분석 불가: 쓸 수 있는 AI 공급자가 없음 (관리자 화면에서 공급자·모델·키 환경변수를 확인)");
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
        CodeSnapshot codes = catalog.current();
        for (AiModelPort provider : candidates) {
            Attempt attempt = call(provider, image, codes);
            if (attempt.result() != null) {
                logs.save(AiCallRecord.success(userId, AiCallRecord.FEATURE_ITEM_ANALYZE, provider.name(), attempt.result(),
                        attempt.latencyMs(), image));
                int remaining = (int) Math.max(0, limit - (used + 1));
                return new AnalyzeResult(attempt.result().suggestion(), provider.name(), attempt.result().model(),
                        remaining);
            }
            logs.save(AiCallRecord.failure(userId, AiCallRecord.FEATURE_ITEM_ANALYZE, provider.name(), provider.model(),
                    attempt.errorCode(), attempt.latencyMs(), image));
        }
        throw new ApiException(ErrorCode.AI_UNAVAILABLE);
    }

    @Override
    public ProviderTestResult test(long adminUserId, String providerId, ImageUpload upload) {
        AiProviderSetting setting = providers.findById(providerId).orElseThrow(ApiException::notFound);
        AiImage image = readImage(upload);
        AiModelPort provider;
        try {
            provider = registry.forTest(setting);
        } catch (AiProviderException e) {
            // 모델·키가 없으면 호출하지 않는다 (기록도 남기지 않음)
            return new ProviderTestResult(false, null, e.reason(), e.getMessage(), 0, null, null, setting.model());
        }
        Attempt attempt = call(provider, image, catalog.current());
        if (attempt.result() != null) {
            AiResult r = attempt.result();
            logs.save(AiCallRecord.success(adminUserId, AiCallRecord.FEATURE_ADMIN_TEST, provider.name(), r,
                    attempt.latencyMs(), image));
            return new ProviderTestResult(true, r.suggestion(), null, null, attempt.latencyMs(), r.inputTokens(),
                    r.outputTokens(), r.model());
        }
        logs.save(AiCallRecord.failure(adminUserId, AiCallRecord.FEATURE_ADMIN_TEST, provider.name(), provider.model(),
                attempt.errorCode(), attempt.latencyMs(), image));
        return new ProviderTestResult(false, null, attempt.errorCode(), attempt.errorMessage(), attempt.latencyMs(), null,
                null, provider.model());
    }

    private record Attempt(AiResult result, String errorCode, String errorMessage, int latencyMs) {
    }

    private static Attempt call(AiModelPort provider, AiImage image, CodeSnapshot codes) {
        long started = System.nanoTime();
        try {
            AiResult result = provider.analyze(image, codes);
            return new Attempt(result, null, null, elapsedMs(started));
        } catch (AiProviderException e) {
            log.warn("AI 분석 실패 provider={} reason={} message={}", provider.name(), e.reason(), e.getMessage());
            return new Attempt(null, e.reason(), e.getMessage(), elapsedMs(started));
        } catch (RuntimeException e) {
            log.error("AI 분석 중 예상하지 못한 오류 provider={} type={}", provider.name(), e.getClass().getName());
            return new Attempt(null, "UNEXPECTED", "예상하지 못한 오류: " + e.getClass().getSimpleName(), elapsedMs(started));
        }
    }

    /** 오늘(한국 시간) 성공한 사진 분석 수 (관리자 시험 분석 제외) */
    public long usedToday(long userId) {
        LocalDate today = Zones.today(clock);
        return usage.countSuccess(userId, Zones.startOf(today), Zones.startOf(today.plusDays(1)));
    }

    /** 검사 순서: 파트 없음·빈 파일 → 10MB 초과 → 읽기 실패 → 형식 */
    private static AiImage readImage(ImageUpload file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("image", "사진을 올려 주세요.");
        }
        if (file.size() > MAX_IMAGE_BYTES) {
            throw new ApiException(ErrorCode.IMAGE_TOO_LARGE);
        }
        if (file.unreadable()) {
            throw ApiException.validation("image", "사진을 읽을 수 없어요.");
        }
        byte[] bytes = file.bytes();
        String mediaType = ImageTypes.resolve(file.contentType(), bytes)
                .orElseThrow(() -> ApiException.validation("image", "jpeg, png, webp, heic 사진만 올릴 수 있어요."));
        return new AiImage(bytes, mediaType, sha256(bytes));
    }

    private static int elapsedMs(long startedNanos) {
        return (int) ((System.nanoTime() - startedNanos) / 1_000_000);
    }

    public static String sha256(byte[] bytes) {
        return AiImages.sha256(bytes);
    }
}
