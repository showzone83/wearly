package app.wearly.ai;

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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사진 분석: 형식·크기 검사 → 일일 한도 확인(MyBatis 집계) → 공급자 호출 → 기록(JPA).
 * 성공한 호출만 한도에 센다. 날짜는 Asia/Seoul 기준.
 */
@Service
public class AiAnalysisService {

    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final AiProvider provider;
    private final AiUsageMapper usage;
    private final AiAnalysisLogRepository logs;
    private final WearlyProperties.Ai props;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AiAnalysisService(AiProvider provider, AiUsageMapper usage, AiAnalysisLogRepository logs,
            WearlyProperties properties, JsonMapper jsonMapper, Clock clock) {
        this.provider = provider;
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

        long started = System.nanoTime();
        AiResult result;
        try {
            result = provider.analyze(image);
        } catch (AiProviderException e) {
            logs.save(AiAnalysisLog.failure(userId, provider.name(), provider.model(), e.reason(), elapsedMs(started), image));
            log.warn("AI 분석 실패 provider={} reason={} message={}", provider.name(), e.reason(), e.getMessage());
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        } catch (RuntimeException e) {
            logs.save(AiAnalysisLog.failure(userId, provider.name(), provider.model(), "UNEXPECTED", elapsedMs(started), image));
            log.error("AI 분석 중 예상하지 못한 오류 provider={}", provider.name(), e);
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }

        Map<String, Object> resultJson = jsonMapper.convertValue(result.suggestion(), new TypeReference<>() {
        });
        logs.save(AiAnalysisLog.success(userId, provider.name(), result, resultJson, elapsedMs(started), image));
        int remaining = (int) Math.max(0, limit - (used + 1));
        return new AnalyzeResponse(result.suggestion(), provider.name(), result.model(), remaining);
    }

    /** 오늘(한국 시간) 성공한 분석 수 */
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
