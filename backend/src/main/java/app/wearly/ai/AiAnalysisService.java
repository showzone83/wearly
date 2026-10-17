package app.wearly.ai;

import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.config.ClockConfig;
import app.wearly.config.WearlyProperties;
import app.wearly.user.UserRepository;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사진 분석: 형식·크기 검사 → 일일 한도 확인(MyBatis 집계) + PENDING 기록 → 공급자 호출 → 결과 기록(JPA).
 * 성공한 호출만 한도에 센다. 날짜는 Asia/Seoul 기준.
 *
 * 동시 요청이 한도를 넘지 않도록, 회원 행을 잠근 트랜잭션 안에서 개수를 세고 PENDING 기록으로 자리를 먼저 잡는다.
 * 진행 중인 호출도 세므로 같은 회원의 다음 요청은 그 자리를 본다. 공급자 호출은 트랜잭션 밖에서 해서
 * Claude 응답을 기다리는 동안 DB 연결·잠금을 잡고 있지 않는다. 실패하면 FAILED로 바꿔 한도에서 빠진다.
 */
@Service
public class AiAnalysisService {

    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    /** 이보다 오래된 PENDING은 서버가 중간에 죽은 것으로 보고 한도에서 뺀다 */
    static final Duration PENDING_GRACE = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final AiProvider provider;
    private final AiUsageMapper usage;
    private final AiAnalysisLogRepository logs;
    private final UserRepository users;
    private final TransactionTemplate tx;
    private final WearlyProperties.Ai props;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AiAnalysisService(AiProvider provider, AiUsageMapper usage, AiAnalysisLogRepository logs,
            UserRepository users, PlatformTransactionManager transactionManager, WearlyProperties properties,
            JsonMapper jsonMapper, Clock clock) {
        this.provider = provider;
        this.usage = usage;
        this.logs = logs;
        this.users = users;
        this.tx = new TransactionTemplate(transactionManager);
        this.props = properties.ai();
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public AnalyzeResponse analyze(long userId, MultipartFile file) {
        AiImage image = readImage(file);
        int limit = props.dailyLimit();
        long logId = reserve(userId, image, limit);

        long started = System.nanoTime();
        AiResult result;
        try {
            result = provider.analyze(image);
        } catch (AiProviderException e) {
            finish(logId, l -> l.fail(e.reason(), elapsedMs(started), clock.instant()));
            log.warn("AI 분석 실패 provider={} reason={} message={}", provider.name(), e.reason(), e.getMessage());
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        } catch (RuntimeException e) {
            finish(logId, l -> l.fail("UNEXPECTED", elapsedMs(started), clock.instant()));
            log.error("AI 분석 중 예상하지 못한 오류 provider={}", provider.name(), e);
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }

        Map<String, Object> resultJson = jsonMapper.convertValue(result.suggestion(), new TypeReference<>() {
        });
        finish(logId, l -> l.succeed(result, resultJson, elapsedMs(started), clock.instant()));
        int remaining = (int) Math.max(0, limit - usedToday(userId));
        return new AnalyzeResponse(result.suggestion(), provider.name(), result.model(), remaining);
    }

    /** 회원 행을 잠근 채 한도를 확인하고 PENDING 기록을 남긴다. 한도를 넘었으면 429. */
    private long reserve(long userId, AiImage image, int limit) {
        Long id = tx.execute(status -> {
            users.findForUpdate(userId).orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
            if (usedToday(userId) >= limit) {
                throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED);
            }
            return logs.save(AiAnalysisLog.pending(userId, provider.name(), provider.model(), image, clock.instant()))
                    .getId();
        });
        return Objects.requireNonNull(id);
    }

    private void finish(long logId, Consumer<AiAnalysisLog> update) {
        tx.executeWithoutResult(status -> logs.findById(logId).ifPresent(update));
    }

    /** 오늘(한국 시간) 한도로 센 분석 수: 성공 + 진행 중 */
    public long usedToday(long userId) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
        Instant from = today.atStartOfDay(ClockConfig.SEOUL).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ClockConfig.SEOUL).toInstant();
        return usage.countUsed(userId, from, to, now.minus(PENDING_GRACE));
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
