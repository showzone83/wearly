package app.wearly.admin;

import app.wearly.admin.AdminAiDtos.CreateProviderRequest;
import app.wearly.admin.AdminAiDtos.ProviderDto;
import app.wearly.admin.AdminAiDtos.Today;
import app.wearly.admin.AdminAiDtos.TypeDto;
import app.wearly.admin.AdminAiDtos.TypesResponse;
import app.wearly.admin.AdminRows.ProviderStat;
import app.wearly.ai.AiAnalysisService;
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
import app.wearly.config.WearlyProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 관리자 AI 공급자 관리 (07 문서 3·4장). 쓰기는 JPA, 오늘 호출 집계는 MyBatis */
@Service
public class AdminAiService {

    static final Pattern PROVIDER_ID = Pattern.compile("^[a-z][a-z0-9_-]{0,29}$");
    static final Pattern MODEL = Pattern.compile("^[A-Za-z0-9._:/@-]{1,100}$");

    private final AiProviderSettingRepository settings;
    private final AiProviderRegistry registry;
    private final ApiKeyResolver keys;
    private final AiAnalysisService analysis;
    private final AdminQueryMapper queries;
    private final AuditService audit;
    private final WearlyProperties.Ai props;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AdminAiService(AiProviderSettingRepository settings, AiProviderRegistry registry, ApiKeyResolver keys,
            AiAnalysisService analysis, AdminQueryMapper queries, AuditService audit, WearlyProperties properties,
            JsonMapper jsonMapper, Clock clock) {
        this.settings = settings;
        this.registry = registry;
        this.keys = keys;
        this.analysis = analysis;
        this.queries = queries;
        this.audit = audit;
        this.props = properties.ai();
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProviderDto> list() {
        Map<String, Today> today = today();
        return settings.findAllByOrderByPriorityAscProviderIdAsc().stream()
                .map(s -> dto(s, today.getOrDefault(s.getProviderId(), new Today(0, 0))))
                .toList();
    }

    public TypesResponse types() {
        return new TypesResponse(Arrays.stream(AiProviderType.values())
                .map(t -> new TypeDto(t.name(), t.description(), t.options()))
                .toList(), props.allowBaseUrl(), registry.isMockMode());
    }

    @Transactional
    public ProviderDto create(long adminId, CreateProviderRequest req) {
        if (req == null) {
            throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
        }
        List<FieldError> errors = new ArrayList<>();
        String id = req.providerId() == null ? null : req.providerId().strip();
        if (id == null || !PROVIDER_ID.matcher(id).matches()) {
            errors.add(new FieldError("providerId", "영문 소문자로 시작하는 소문자·숫자·밑줄·하이픈 1~30자여야 해요."));
        } else if (MockAiProvider.ID.equals(id)) {
            errors.add(new FieldError("providerId", "mock은 목업 실행용 이름이라 쓸 수 없어요."));
        }
        AiProviderType type = type(req.type(), errors);
        String displayName = displayName(req.displayName(), errors);
        String model = model(req.model(), errors);
        String apiKeyEnv = apiKeyEnv(req.apiKeyEnv(), errors);
        boolean enabled = req.enabled() != null && req.enabled();
        Integer priority = req.priority();
        if (priority != null && (priority < 0 || priority > 10000)) {
            errors.add(new FieldError("priority", "0~10000이어야 해요."));
        }
        Map<String, Object> options = req.options() == null ? Map.of() : req.options();
        if (type != null) {
            errors.addAll(type.validate(options, props.allowBaseUrl()));
        }
        if (enabled && model == null) {
            errors.add(new FieldError("model", "사용하려면 모델이 필요해요."));
        }
        throwIfAny(errors);
        if (settings.existsById(id)) {
            throw new ApiException(ErrorCode.PROVIDER_EXISTS);
        }
        AiProviderSetting s = new AiProviderSetting(id);
        s.update(type, displayName, model, apiKeyEnv, enabled, priority == null ? settings.maxPriority() + 1 : priority,
                withoutNulls(options));
        settings.saveAndFlush(s);
        ProviderDto after = dto(s, new Today(0, 0));
        audit.record(adminId, "AI_PROVIDER_CREATE", AuditService.AI_PROVIDER, id, null, after);
        return after;
    }

    /** 보낸 필드만 바꾼다. options를 보내면 options 전체를 바꾼다. enabled=true로 두려면 모델이 있어야 한다 */
    @Transactional
    public ProviderDto patch(long adminId, String providerId, JsonNode body) {
        AiProviderSetting s = settings.findById(providerId).orElseThrow(ApiException::notFound);
        if (body == null || !body.isObject()) {
            throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
        }
        Map<String, Today> today = today();
        ProviderDto before = dto(s, today.getOrDefault(providerId, new Today(0, 0)));
        List<FieldError> errors = new ArrayList<>();
        if (body.has("providerId") && !providerId.equals(body.get("providerId").asString(null))) {
            errors.add(new FieldError("providerId", "공급자 ID는 바꿀 수 없어요."));
        }
        AiProviderType type = body.has("type") ? type(text(body, "type"), errors) : s.getType();
        String displayName = body.has("displayName") ? displayName(text(body, "displayName"), errors) : s.getDisplayName();
        String model = body.has("model") ? model(text(body, "model"), errors) : s.getModel();
        String apiKeyEnv = body.has("apiKeyEnv") ? apiKeyEnv(text(body, "apiKeyEnv"), errors) : s.getApiKeyEnv();
        boolean enabled = s.isEnabled();
        if (body.has("enabled")) {
            if (body.get("enabled").isBoolean()) {
                enabled = body.get("enabled").asBoolean();
            } else {
                errors.add(new FieldError("enabled", "true 또는 false여야 해요."));
            }
        }
        int priority = s.getPriority();
        if (body.has("priority")) {
            JsonNode p = body.get("priority");
            if (p.isIntegralNumber() && p.canConvertToInt() && p.asInt() >= 0 && p.asInt() <= 10000) {
                priority = p.asInt();
            } else {
                errors.add(new FieldError("priority", "0~10000 정수여야 해요."));
            }
        }
        Map<String, Object> options = s.getOptions();
        if (body.has("options")) {
            JsonNode o = body.get("options");
            if (o.isNull()) {
                options = Map.of();
            } else if (!o.isObject()) {
                errors.add(new FieldError("options", "JSON 객체여야 해요."));
            } else {
                options = jsonMapper.convertValue(o, new TypeReference<LinkedHashMap<String, Object>>() {
                });
            }
        }
        if (type != null) {
            errors.addAll(type.validate(options, props.allowBaseUrl()));
        }
        if (enabled && (model == null || model.isBlank())) {
            errors.add(new FieldError("model", "사용하려면 모델이 필요해요."));
        }
        throwIfAny(errors);
        s.update(type, displayName, model, apiKeyEnv, enabled, priority, withoutNulls(options));
        settings.saveAndFlush(s);
        ProviderDto after = dto(s, today.getOrDefault(providerId, new Today(0, 0)));
        audit.record(adminId, "AI_PROVIDER_UPDATE", AuditService.AI_PROVIDER, providerId, before, after);
        return after;
    }

    /** 행만 지운다. 분석 기록(tb_ai_analysis_log.provider)은 남는다 */
    @Transactional
    public void delete(long adminId, String providerId) {
        AiProviderSetting s = settings.findById(providerId).orElseThrow(ApiException::notFound);
        ProviderDto before = dto(s, new Today(0, 0));
        settings.delete(s);
        settings.flush();
        audit.record(adminId, "AI_PROVIDER_DELETE", AuditService.AI_PROVIDER, providerId, before, null);
    }

    public AiAnalysisService.TestResult test(long adminId, String providerId, MultipartFile image) {
        AiProviderSetting s = settings.findById(providerId).orElseThrow(ApiException::notFound);
        return analysis.testProvider(adminId, s, image);
    }

    private ProviderDto dto(AiProviderSetting s, Today today) {
        return new ProviderDto(s.getProviderId(), s.getType().name(), s.getDisplayName(), s.getModel(), s.getApiKeyEnv(),
                keys.configured(s.getApiKeyEnv()), s.isEnabled(), s.getPriority(), s.getOptions(), registry.usable(s), today);
    }

    /** 오늘(한국 시간) 공급자별 호출 수 (사진 분석 + 시험 분석) */
    private Map<String, Today> today() {
        LocalDate day = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
        Instant from = day.atStartOfDay(ClockConfig.SEOUL).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ClockConfig.SEOUL).toInstant();
        Map<String, Today> out = new HashMap<>();
        for (ProviderStat r : queries.providerStats(from, to)) {
            out.put(r.providerId, new Today(r.success, r.failed));
        }
        return out;
    }

    private static Map<String, Object> withoutNulls(Map<String, Object> options) {
        Map<String, Object> out = new LinkedHashMap<>();
        options.forEach((k, v) -> {
            if (v != null) {
                out.put(k, v);
            }
        });
        return out;
    }

    private static String text(JsonNode body, String field) {
        JsonNode n = body.get(field);
        return n == null || n.isNull() ? null : n.isString() ? n.asString() : n.toString();
    }

    private static AiProviderType type(String raw, List<FieldError> errors) {
        try {
            AiProviderType t = raw == null ? null : AiProviderType.valueOf(raw.strip());
            if (t == null) {
                errors.add(new FieldError("type", "CLAUDE, OPENAI, GEMINI 중 하나여야 해요."));
            }
            return t;
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError("type", "CLAUDE, OPENAI, GEMINI 중 하나여야 해요."));
            return null;
        }
    }

    private static String displayName(String raw, List<FieldError> errors) {
        String v = raw == null ? null : raw.strip();
        if (v == null || v.isEmpty() || v.length() > 50) {
            errors.add(new FieldError("displayName", "1~50자로 입력해 주세요."));
        }
        return v;
    }

    private static String model(String raw, List<FieldError> errors) {
        String v = raw == null || raw.isBlank() ? null : raw.strip();
        if (v != null && !MODEL.matcher(v).matches()) {
            errors.add(new FieldError("model", "영문·숫자·. _ - : / @ 1~100자여야 해요."));
        }
        return v;
    }

    private static String apiKeyEnv(String raw, List<FieldError> errors) {
        String v = raw == null ? null : raw.strip();
        if (v == null || !ApiKeyResolver.ENV_NAME.matcher(v).matches()) {
            errors.add(new FieldError("apiKeyEnv", "영문 대문자·숫자·밑줄로 된 *_API_KEY 형식의 환경변수 이름이어야 해요."));
        }
        return v;
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
    }
}
