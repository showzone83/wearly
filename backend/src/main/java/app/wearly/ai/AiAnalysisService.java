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
 * 사진 분석: 형식·크기 검사 → 일일 한도 확인(MyBatis 집계) → 공급자를 우선순위 순으로 시도 → 호출마다 기록(JPA).
 * 앞 공급자가 실패하면(시간 초과, API 오류, 거절, 응답 검증 실패) 다음 공급자로 넘어가고, 실패도 각각 기록한다.
 * 성공한 회원 분석(ITEM_ANALYZE)만 한도에 센다. 날짜는 Asia/Seoul 기준.
 */
@Service
public class AiAnalysisService {

    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final AiProviderRegistry registry;
    private final CodeCatalog codes;
    private final AiUsageMapper usage;
    private final AiAnalysisLogRepository logs;
    private final WearlyProperties.Ai props;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AiAnalysisService(AiProviderRegistry registry, CodeCatalog codes, AiUsageMapper usage,
            AiAnalysisLogRepository logs, WearlyProperties properties, JsonMapper jsonMapper, Clock clock) {
        this.registry = registry;
        this.codes = codes;
        this.usage = usage;
        this.logs = logs;
        this.props = properties.ai();
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** 한 공급자 호출 결과 */
    public record Attempt(String providerId, String model, boolean ok, AiResult result, String errorCode,
            String errorMessage, int latencyMs) {
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
            log.warn("AI 분석 불가: 쓸 수 있는 공급자가 없습니다 (사용·모델·키를 갖춘 tb_ai_provider 행이 없음)");
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
        CodeSnapshot snapshot = codes.snapshot();
        for (AiProvider provider : candidates) {
            Attempt attempt = attempt(userId, AiAnalysisLog.ITEM_ANALYZE, provider, image, snapshot);
            if (attempt.ok()) {
                int remaining = (int) Math.max(0, limit - (used + 1));
                return new AnalyzeResponse(attempt.result().suggestion(), provider.id(), attempt.result().model(),
                        remaining);
            }
        }
        throw new ApiException(ErrorCode.AI_UNAVAILABLE);
    }

    /** 관리자 시험 분석: 사용 여부와 관계없이 그 공급자로 한 번 호출한다. 기록은 ADMIN_TEST(한도에 안 셈). */
    public Attempt adminTest(long adminUserId, AiProvider provider, MultipartFile file) {
        return attempt(adminUserId, AiAnalysisLog.ADMIN_TEST, provider, readImage(file), codes.snapshot());
    }

    private Attempt attempt(long userId, String feature, AiProvider provider, AiImage image, CodeSnapshot snapshot) {
        long started = System.nanoTime();
        try {
            AiResult result = provider.analyze(image, snapshot);
            Map<String, Object> resultJson = jsonMapper.convertValue(result.suggestion(), new TypeReference<>() {
            });
            int latency = elapsedMs(started);
            logs.save(AiAnalysisLog.success(userId, feature, provider.id(), result, resultJson, latency, image));
            return new Attempt(provider.id(), result.model(), true, result, null, null, latency);
        } catch (AiProviderException e) {
            int latency = elapsedMs(started);
            logs.save(AiAnalysisLog.failure(userId, feature, provider.id(), provider.model(), e.reason(), latency, image));
            log.warn("AI 분석 실패 provider={} reason={} message={}", provider.id(), e.reason(), e.getMessage());
            return new Attempt(provider.id(), provider.model(), false, null, e.reason(), e.getMessage(), latency);
        } catch (RuntimeException e) {
            int latency = elapsedMs(started);
            logs.save(AiAnalysisLog.failure(userId, feature, provider.id(), provider.model(), "UNEXPECTED", latency, image));
            log.error("AI 분석 중 예상하지 못한 오류 provider={} type={}", provider.id(), e.getClass().getName());
            return new Attempt(provider.id(), provider.model(), false, null, "UNEXPECTED", "예상하지 못한 오류", latency);
        }
    }

    /** 오늘(한국 시간) 성공한 회원 분석 수 */
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
