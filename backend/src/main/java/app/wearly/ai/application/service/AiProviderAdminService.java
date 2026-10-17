package app.wearly.ai.application.service;

import app.wearly.ai.application.port.in.AiProviderAdminUseCase;
import app.wearly.ai.application.port.in.AiProviderInfo;
import app.wearly.ai.application.port.in.CreateProviderCommand;
import app.wearly.ai.application.port.in.ProviderTypeInfo;
import app.wearly.ai.application.port.out.AiProviderPort;
import app.wearly.ai.domain.AiProviderRules;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import app.wearly.ai.domain.AiSettings;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.PatchBody;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 AI 공급자 관리 (07 문서 3·4장). 쓰기는 JPA */
@Service
public class AiProviderAdminService implements AiProviderAdminUseCase {

    private final AiProviderPort settings;
    private final AiProviderRegistry registry;
    private final AiSettings aiSettings;

    public AiProviderAdminService(AiProviderPort settings, AiProviderRegistry registry, AiSettings aiSettings) {
        this.settings = settings;
        this.registry = registry;
        this.aiSettings = aiSettings;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiProviderInfo> list() {
        return settings.findAllOrdered().stream().map(this::info).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AiProviderInfo get(String providerId) {
        return info(settings.findById(providerId).orElseThrow(ApiException::notFound));
    }

    @Override
    public List<ProviderTypeInfo> types() {
        return Arrays.stream(AiProviderType.values())
                .map(t -> new ProviderTypeInfo(t.name(), t.description(), t.options().stream()
                        .map(o -> new ProviderTypeInfo.Option(o.name(), o.kind(), o.description(), o.defaultValue(), o.min(),
                                o.max(), o.values()))
                        .toList()))
                .toList();
    }

    @Override
    public boolean baseUrlAllowed() {
        return aiSettings.allowBaseUrl();
    }

    @Override
    public boolean mockMode() {
        return registry.isMockMode();
    }

    @Override
    @Transactional
    public AiProviderInfo create(CreateProviderCommand req) {
        if (req == null) {
            throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
        }
        List<FieldError> errors = new ArrayList<>();
        String id = AiProviderRules.providerId(req.providerId(), errors);
        AiProviderType type = AiProviderRules.type(req.type(), errors);
        String displayName = AiProviderRules.displayName(req.displayName(), errors);
        String model = AiProviderRules.model(req.model(), errors);
        String apiKeyEnv = AiProviderRules.apiKeyEnv(req.apiKeyEnv(), errors);
        boolean enabled = req.enabled() != null && req.enabled();
        Integer priority = req.priority();
        if (priority != null && (priority < 0 || priority > 10000)) {
            errors.add(new FieldError("priority", "0~10000이어야 해요."));
        }
        Map<String, Object> options = req.options() == null ? Map.of() : req.options();
        if (type != null) {
            errors.addAll(type.validate(options, aiSettings.allowBaseUrl()));
        }
        if (enabled && model == null) {
            errors.add(new FieldError("model", "사용하려면 모델이 필요해요."));
        }
        ApiException.throwIfAny(errors);
        if (settings.exists(id)) {
            throw new ApiException(ErrorCode.PROVIDER_EXISTS);
        }
        AiProviderSetting s = new AiProviderSetting(id, type, displayName, model, apiKeyEnv, enabled,
                priority == null ? settings.maxPriority() + 1 : priority, AiProviderRules.withoutNulls(options), null);
        return info(settings.create(s));
    }

    @Override
    @Transactional
    public AiProviderInfo patch(String providerId, PatchBody body) {
        AiProviderSetting s = settings.findById(providerId).orElseThrow(ApiException::notFound);
        if (body == null || !body.isObject()) {
            throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
        }
        List<FieldError> errors = new ArrayList<>();
        if (body.has("providerId") && !providerId.equals(body.string("providerId"))) {
            errors.add(new FieldError("providerId", "공급자 ID는 바꿀 수 없어요."));
        }
        AiProviderType type = body.has("type") ? AiProviderRules.type(body.text("type"), errors) : s.type();
        String displayName = body.has("displayName") ? AiProviderRules.displayName(body.text("displayName"), errors)
                : s.displayName();
        String model = body.has("model") ? AiProviderRules.model(body.text("model"), errors) : s.model();
        String apiKeyEnv = body.has("apiKeyEnv") ? AiProviderRules.apiKeyEnv(body.text("apiKeyEnv"), errors)
                : s.apiKeyEnv();
        boolean enabled = s.enabled();
        if (body.has("enabled")) {
            if (body.isBoolean("enabled")) {
                enabled = body.bool("enabled");
            } else {
                errors.add(new FieldError("enabled", "true 또는 false여야 해요."));
            }
        }
        int priority = s.priority();
        if (body.has("priority")) {
            if (body.isInt("priority") && body.intValue("priority") >= 0 && body.intValue("priority") <= 10000) {
                priority = body.intValue("priority");
            } else {
                errors.add(new FieldError("priority", "0~10000 정수여야 해요."));
            }
        }
        Map<String, Object> options = s.options();
        if (body.has("options")) {
            if (body.isNull("options")) {
                options = Map.of();
            } else if (!body.isObject("options")) {
                errors.add(new FieldError("options", "JSON 객체여야 해요."));
            } else {
                options = body.object("options");
            }
        }
        if (type != null) {
            errors.addAll(type.validate(options, aiSettings.allowBaseUrl()));
        }
        if (enabled && (model == null || model.isBlank())) {
            errors.add(new FieldError("model", "사용하려면 모델이 필요해요."));
        }
        ApiException.throwIfAny(errors);
        s.update(type, displayName, model, apiKeyEnv, enabled, priority, AiProviderRules.withoutNulls(options));
        return info(settings.update(s));
    }

    @Override
    @Transactional
    public AiProviderInfo delete(String providerId) {
        AiProviderSetting s = settings.findById(providerId).orElseThrow(ApiException::notFound);
        AiProviderInfo before = info(s);
        settings.delete(providerId);
        return before;
    }

    private AiProviderInfo info(AiProviderSetting s) {
        return new AiProviderInfo(s.providerId(), s.type().name(), s.displayName(), s.model(), s.apiKeyEnv(),
                registry.keyConfigured(s), s.enabled(), s.priority(), s.options(), registry.usable(s));
    }
}
