package app.wearly.ai;

import app.wearly.code.CodeCatalog;
import app.wearly.code.CodeSnapshot;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.config.ClockConfig;
import app.wearly.config.WearlyProperties;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사진 분석: 형식·크기 검사 → 일일 한도 확인(MyBatis 집계) → 공급자를 우선순위 순으로 시도 → 기록(JPA).
 * 앞 공급자가 실패하면 다음 공급자로 넘어가고, 실패도 각각 기록한다. 성공한 ITEM_ANALYZE만 한도에 센다 (Asia/Seoul 날짜).
 */
@Service
public class AiAnalysisService {

    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final AiProviderRegistry registry;
    private final CodeCatalog catalog;
    private final AiUsageMapper usage;
    private final AiAnalysisLogRepository logs;
    private final WearlyProperties.Ai props;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AiAnalysisService(AiProviderRegistry registry, CodeCatalog catalog, AiUsageMapper usage,
            AiAnalysisLogRepository logs, WearlyProperties properties, JsonMapper jsonMapper, Clock clock) {
        this.registry = registry;
        this.catalog = catalog;
        this.usage = usage;
        this.logs = logs;
        this.props = properties.ai();
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public AnalyzeResponse analyze(long userId, MultipartFile file) {
        AiImage image = readImage(file);

        int limit = props.dailyLimit();
        long used = usedToday(userId);
        if (used >= limit) {
            throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED);
        }

        List<AiProvider> candidates = registry.candidates();
        if (candidates.isEmpty()) {
            log.warn("AI 분석 불가: 쓸 수 있는 AI 공급자가 없음 (관리자 화면에서 공급자·모델·키 환경변수를 확인)");
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
        CodeSnapshot codes = catalog.current();
        for (AiProvider provider : candidates) {
            Attempt attempt = call(provider, image, codes);
            if (attempt.result() != null) {
                logs.save(AiAnalysisLog.success(userId, AiAnalysisLog.FEATURE_ITEM_ANALYZE, provider.name(),
                        attempt.result(), toJson(attempt.result().suggestion()), attempt.latencyMs(), image));
                int remaining = (int) Math.max(0, limit - (used + 1));
                return new AnalyzeResponse(attempt.result().suggestion(), provider.name(), attempt.result().model(),
                        remaining);
            }
            logs.save(AiAnalysisLog.failure(userId, AiAnalysisLog.FEATURE_ITEM_ANALYZE, provider.name(), provider.model(),
                    attempt.errorCode(), attempt.latencyMs(), image));
        }
        throw new ApiException(ErrorCode.AI_UNAVAILABLE);
    }

    /** 관리자 시험 분석 결과 (07 문서 4장) */
    public record TestResult(boolean ok, AiSuggestion suggestion, String errorCode, String errorMessage, int latencyMs,
            Long inputTokens, Long outputTokens, String model) {
    }

    /**
     * 사용 여부와 관계없이 그 공급자로 한 번 호출한다. 일일 한도에 세지 않고 feature='ADMIN_TEST'로 기록한다.
     * 목업 실행이어도 DB 행의 실제 공급자를 부른다.
     */
    public TestResult testProvider(long adminUserId, AiProviderSetting setting, MultipartFile file) {
        AiImage image = readImage(file);
        AiProvider provider;
        try {
            provider = registry.forTest(setting);
        } catch (AiProviderException e) {
            // 모델·키가 없으면 호출하지 않는다 (기록도 남기지 않음)
            return new TestResult(false, null, e.reason(), e.getMessage(), 0, null, null, setting.getModel());
        }
        Attempt attempt = call(provider, image, catalog.current());
        if (attempt.result() != null) {
            AiResult r = attempt.result();
            logs.save(AiAnalysisLog.success(adminUserId, AiAnalysisLog.FEATURE_ADMIN_TEST, provider.name(), r,
                    toJson(r.suggestion()), attempt.latencyMs(), image));
            return new TestResult(true, r.suggestion(), null, null, attempt.latencyMs(), r.inputTokens(), r.outputTokens(),
                    r.model());
        }
        logs.save(AiAnalysisLog.failure(adminUserId, AiAnalysisLog.FEATURE_ADMIN_TEST, provider.name(), provider.model(),
                attempt.errorCode(), attempt.latencyMs(), image));
        return new TestResult(false, null, attempt.errorCode(), attempt.errorMessage(), attempt.latencyMs(), null, null,
                provider.model());
    }

    private record Attempt(AiResult result, String errorCode, String errorMessage, int latencyMs) {
    }

    private static Attempt call(AiProvider provider, AiImage image, CodeSnapshot codes) {
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

    private Map<String, Object> toJson(AiSuggestion suggestion) {
        return jsonMapper.convertValue(suggestion, new TypeReference<>() {
        });
    }

    /** 오늘(한국 시간) 성공한 사진 분석 수 (관리자 시험 분석 제외) */
    public long usedToday(long userId) {
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
        Instant from = today.atStartOfDay(ClockConfig.SEOUL).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ClockConfig.SEOUL).toInstant();
        return usage.countSuccess(userId, from, to);
    }

    private static AiImage readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("image", "사진을 올려 주세요.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ApiException(ErrorCode.IMAGE_TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("image", "사진을 읽을 수 없어요.");
        }
        String mediaType = ImageTypes.resolve(file.getContentType(), bytes)
                .orElseThrow(() -> ApiException.validation("image", "jpeg, png, webp, heic 사진만 올릴 수 있어요."));
        return new AiImage(bytes, mediaType, sha256(bytes));
    }

    private static int elapsedMs(long startedNanos) {
        return (int) ((System.nanoTime() - startedNanos) / 1_000_000);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
