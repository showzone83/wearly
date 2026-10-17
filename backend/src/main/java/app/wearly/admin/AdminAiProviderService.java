package app.wearly.admin;

import app.wearly.admin.AdminDtos.ProviderCreateRequest;
import app.wearly.admin.AdminDtos.ProviderDto;
import app.wearly.admin.AdminDtos.ProviderTestResult;
import app.wearly.admin.AdminDtos.ProviderTypeInfo;
import app.wearly.admin.AdminDtos.TodayCount;
import app.wearly.admin.AdminRows.ProviderStat;
import app.wearly.ai.AiAnalysisService;
import app.wearly.ai.AiAnalysisService.Attempt;
import app.wearly.ai.AiProviderRegistry;
import app.wearly.ai.AiProviderSetting;
import app.wearly.ai.AiProviderSettingRepository;
import app.wearly.ai.AiProviderType;
import app.wearly.ai.ApiKeyResolver;
import app.wearly.ai.MockAiProvider;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse.FieldError;
import app.wearly.config.ClockConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 AI 공급자 관리 (doc/07_관리자_사이트.md 4장 AI 공급자).
 * 키 값은 다루지 않는다: 응답에는 키 환경변수 이름과 keyConfigured(true/false)만 준다.
 */
@Service
public class AdminAiProviderService {

    private static final Pattern PROVIDER_ID = Pattern.compile("^[a-z][a-z0-9_-]{0,29}$");
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final AiProviderSettingRepository settings;
    private final AiProviderRegistry registry;
    private final AiAnalysisService analysis;
    private final AdminQueryMapper queries;
    private final AuditService audit;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AdminAiProviderService(AiProviderSettingRepository settings, AiProviderRegistry registry,
            AiAnalysisService analysis, AdminQueryMapper queries, AuditService audit, JsonMapper jsonMapper,
            Clock clock) {
        this.settings = settings;
        this.registry = registry;
        this.analysis = analysis;
        this.queries = queries;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProviderDto> list() {
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
        Instant from = today.atStartOfDay(ClockConfig.SEOUL).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ClockConfig.SEOUL).toInstant();
        Map<String, ProviderStat> stats = new HashMap<>();
        queries.providerStats(from, to).forEach(s -> stats.put(s.providerId, s));
        return settings.findAllByOrderByPriorityAscProviderIdAsc().stream()
                .map(s -> toDto(s, stats.get(s.getProviderId())))
                .toList();
    }

    @Transactional
    public ProviderDto create(long adminId, ProviderCreateRequest req) {
        List<FieldError> errors = new ArrayList<>();
        String id = req.providerId() == null ? null : req.providerId().strip();
        if (id == null || !PROVIDER_ID.matcher(id).matches()) {
            errors.add(new FieldError("providerId", "영문 소문자로 시작하는 소문자·숫자·밑줄·하이픈 1~30자여야 해요."));
        } else if (MockAiProvider.ID.equals(id)) {
            errors.add(new FieldError("providerId", "mock은 목업 실행용으로 예약된 이름이에요."));
        }
        AiProviderType type = parseType(req.type(), errors);
        AiProviderSetting s = new AiProviderSetting(id);
        s.setDisplayName(checkDisplayName(req.displayName(), errors));
        s.setModel(checkModel(req.model(), errors));
        s.setApiKeyEnv(checkApiKeyEnv(req.apiKeyEnv(), errors));
        s.setEnabled(Boolean.TRUE.equals(req.enabled()));
        s.setPriority(req.priority() != null ? checkPriority(req.priority(), errors) : settings.maxPriority() + 1);
        if (type != null) {
            s.setType(type.name());
            s.setOptions(type.normalizeOptions(req.options(), errors));
        }
        if (s.isEnabled() && !s.hasModel()) {
            errors.add(new FieldError("model", "사용하려면 모델을 넣어 주세요."));
        }
        throwIfAny(errors);
        if (settings.existsById(id)) {
            throw new ApiException(ErrorCode.PROVIDER_EXISTS);
        }
        try {
            settings.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.PROVIDER_EXISTS);
        }
        audit.record(adminId, "AI_PROVIDER_CREATE", AuditService.AI_PROVIDER, id, null, s.snapshot());
        evictAfterCommit(id);
        return toDto(s, null);
    }

    /** 보낸 필드만 바꾼다. providerId는 못 바꾼다. enabled를 true로 두려면 모델이 있어야 한다 */
    @Transactional
    public ProviderDto patch(long adminId, String providerId, JsonNode body) {
        AiProviderSetting s = settings.findById(providerId).orElseThrow(ApiException::notFound);
        if (body == null || !body.isObject()) {
            throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
        }
        Map<String, Object> before = s.snapshot();
        List<FieldError> errors = new ArrayList<>();
        if (body.has("providerId") && !providerId.equals(body.get("providerId").asString(null))) {
            errors.add(new FieldError("providerId", "공급자 ID는 바꿀 수 없어요."));
        }
        AiProviderType type = s.typeEnum();
        if (body.has("type")) {
            AiProviderType next = parseType(body.get("type").isString() ? body.get("type").asString() : null, errors);
            if (next != null) {
                type = next;
                s.setType(next.name());
            }
        }
        if (body.has("displayName")) {
            s.setDisplayName(checkDisplayName(text(body.get("displayName")), errors));
        }
        if (body.has("model")) {
            JsonNode n = body.get("model");
            if (!n.isNull() && !n.isString()) {
                errors.add(new FieldError("model", "문자열이어야 해요."));
            } else {
                s.setModel(checkModel(text(n), errors));
            }
        }
        if (body.has("apiKeyEnv")) {
            s.setApiKeyEnv(checkApiKeyEnv(text(body.get("apiKeyEnv")), errors));
        }
        if (body.has("enabled")) {
            JsonNode n = body.get("enabled");
            if (!n.isBoolean()) {
                errors.add(new FieldError("enabled", "true 또는 false여야 해요."));
            } else {
                s.setEnabled(n.asBoolean());
            }
        }
        if (body.has("priority")) {
            JsonNode n = body.get("priority");
            if (!n.isIntegralNumber() || !n.canConvertToInt()) {
                errors.add(new FieldError("priority", "정수여야 해요."));
            } else {
                s.setPriority(checkPriority(n.asInt(), errors));
            }
        }
        if (body.has("options")) {
            JsonNode n = body.get("options");
            if (!n.isObject() && !n.isNull()) {
                errors.add(new FieldError("options", "객체여야 해요."));
            } else {
                s.setOptions(type.normalizeOptions(n.isNull() ? Map.of() : jsonMapper.convertValue(n, MAP), errors));
            }
        } else if (body.has("type")) {
            // 종류를 바꾸면 기존 옵션도 새 종류 기준으로 다시 검사한다
            s.setOptions(type.normalizeOptions(s.getOptions(), errors));
        }
        if (s.isEnabled() && !s.hasModel()) {
            errors.add(new FieldError("model", "사용하려면 모델을 넣어 주세요."));
        }
        throwIfAny(errors);
        settings.saveAndFlush(s);
        Map<String, Object> after = s.snapshot();
        if (!after.equals(before)) {
            audit.record(adminId, "AI_PROVIDER_UPDATE", AuditService.AI_PROVIDER, providerId, before, after);
        }
        evictAfterCommit(providerId);
        return toDto(s, null);
    }

    /** 행만 지운다. 분석 기록(provider 문자열)은 남는다 */
    @Transactional
    public void delete(long adminId, String providerId) {
        AiProviderSetting s = settings.findById(providerId).orElseThrow(ApiException::notFound);
        Map<String, Object> before = s.snapshot();
        settings.delete(s);
        settings.flush();
        audit.record(adminId, "AI_PROVIDER_DELETE", AuditService.AI_PROVIDER, providerId, before, null);
        evictAfterCommit(providerId);
    }

    /**
     * 시험 분석: 사용 여부와 관계없이 그 공급자로 한 번 호출한다. 일일 한도에 세지 않고 ADMIN_TEST로 기록한다.
     * 모델이나 키가 없으면 호출하지 않고 ok=false(MODEL_MISSING·KEY_MISSING)로 답한다.
     */
    public ProviderTestResult test(long adminId, String providerId, MultipartFile image) {
        AiProviderSetting s = settings.findById(providerId).orElseThrow(ApiException::notFound);
        if (!s.hasModel()) {
            return new ProviderTestResult(false, null, "MODEL_MISSING", "모델이 비어 있어요.", 0, null, null, null);
        }
        if (!registry.keyConfigured(s)) {
            return new ProviderTestResult(false, null, "KEY_MISSING",
                    "키 환경변수 " + s.getApiKeyEnv() + "에 값이 없어요.", 0, null, null, s.getModel());
        }
        Attempt a = analysis.adminTest(adminId, registry.provider(s), image);
        if (a.ok()) {
            return new ProviderTestResult(true, a.result().suggestion(), null, null, a.latencyMs(),
                    a.result().inputTokens(), a.result().outputTokens(), a.result().model());
        }
        return new ProviderTestResult(false, null, a.errorCode(), a.errorMessage(), a.latencyMs(), null, null,
                s.getModel());
    }

    public List<ProviderTypeInfo> types() {
        return AiProviderType.CREATABLE.stream()
                .map(t -> new ProviderTypeInfo(t.name(), t.displayName(), t.defaultApiKeyEnv(), t.options()))
                .toList();
    }

    private ProviderDto toDto(AiProviderSetting s, ProviderStat stat) {
        return new ProviderDto(s.getProviderId(), s.getType(), s.getDisplayName(), s.getModel(), s.getApiKeyEnv(),
                registry.keyConfigured(s), s.isEnabled(), s.getPriority(), s.getOptions(), registry.usable(s),
                new TodayCount(stat == null ? 0 : stat.success, stat == null ? 0 : stat.failed));
    }

    private void evictAfterCommit(String providerId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    registry.evict(providerId);
                }
            });
        } else {
            registry.evict(providerId);
        }
    }

    private static AiProviderType parseType(String raw, List<FieldError> errors) {
        for (AiProviderType t : AiProviderType.CREATABLE) {
            if (t.name().equals(raw)) {
                return t;
            }
        }
        errors.add(new FieldError("type", "CLAUDE, OPENAI, GEMINI 중 하나여야 해요."));
        return null;
    }

    private static String checkDisplayName(String v, List<FieldError> errors) {
        if (v == null || v.isBlank() || v.strip().length() > 50) {
            errors.add(new FieldError("displayName", "1~50자로 입력해 주세요."));
            return null;
        }
        return v.strip();
    }

    private static String checkModel(String v, List<FieldError> errors) {
        if (v == null || v.isBlank()) {
            return null;
        }
        if (v.strip().length() > 100) {
            errors.add(new FieldError("model", "100자까지 쓸 수 있어요."));
            return null;
        }
        return v.strip();
    }

    private static String checkApiKeyEnv(String v, List<FieldError> errors) {
        if (v == null || !ApiKeyResolver.NAME.matcher(v.strip()).matches()) {
            errors.add(new FieldError("apiKeyEnv", "영문 대문자·숫자·밑줄로 된 환경변수 이름이고 _API_KEY로 끝나야 해요 (예: OPENAI_API_KEY)."));
            return null;
        }
        return v.strip();
    }

    private static int checkPriority(int v, List<FieldError> errors) {
        if (v < 0 || v > 10000) {
            errors.add(new FieldError("priority", "0~10000이어야 해요."));
        }
        return v;
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() || !n.isString() ? null : n.asString();
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
    }
}
