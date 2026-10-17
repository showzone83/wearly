package app.wearly.ai.application.service;

import app.wearly.ai.application.port.in.AiProviderAdminUseCase;
import app.wearly.ai.application.port.in.AiProviderInfo;
import app.wearly.ai.application.port.in.CreateProviderCommand;
import app.wearly.ai.application.port.in.ProviderTypeInfo;
import app.wearly.ai.application.port.out.AiProviderPort;
import app.wearly.ai.application.port.out.ApiKeyVerifierPort;
import app.wearly.ai.application.port.out.ProviderSecretPort;
import app.wearly.ai.application.port.out.SecretCipherPort;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderSelection;
import app.wearly.ai.domain.ApiKeyRules;
import app.wearly.ai.domain.ProviderSecret;
import app.wearly.ai.domain.AiProviderRules;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import app.wearly.ai.domain.AiSettings;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.PatchBody;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.user.application.port.in.UserAccount;
import app.wearly.user.application.port.in.UserUseCase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 AI 공급자 관리 (07 문서 3·4장)와 웹 입력 키 (6장). 쓰기는 JPA, 키는 AES-GCM으로 암호화해 저장한다 */
@Service
public class AiProviderAdminService implements AiProviderAdminUseCase {

    private final AiProviderPort settings;
    private final AiProviderRegistry registry;
    private final ApiKeyService keys;
    private final ProviderSecretPort secrets;
    private final SecretCipherPort cipher;
    private final ApiKeyVerifierPort verifier;
    private final UserUseCase users;
    private final AiSettings aiSettings;

    public AiProviderAdminService(AiProviderPort settings, AiProviderRegistry registry, ApiKeyService keys,
            ProviderSecretPort secrets, SecretCipherPort cipher, ApiKeyVerifierPort verifier, UserUseCase users,
            AiSettings aiSettings) {
        this.settings = settings;
        this.registry = registry;
        this.keys = keys;
        this.secrets = secrets;
        this.cipher = cipher;
        this.verifier = verifier;
        this.users = users;
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
        // 웹 키 행은 FK ON DELETE CASCADE로 함께 지워진다
        keys.invalidate(providerId);
        return before;
    }

    @Override
    public void requireSecretStore() {
        if (!keys.storeEnabled()) {
            throw new ApiException(ErrorCode.SECRET_KEY_MISSING);
        }
    }

    @Override
    @Transactional
    public KeyChange setKey(String providerId, String apiKey, boolean verify, long adminUserId) {
        AiProviderSetting s = settings.findById(providerId).orElseThrow(ApiException::notFound);
        requireSecretStore();
        List<FieldError> errors = new ArrayList<>();
        String key = ApiKeyRules.normalize(apiKey, errors);
        ApiException.throwIfAny(errors);
        if (verify) {
            try {
                verifier.verify(s, key);
            } catch (AiProviderException e) {
                // 어댑터가 키 조각을 지운 메시지에서 한 번 더 지운다
                String reason = e.getMessage() == null ? e.reason() : e.getMessage().replace(key, "***");
                throw new ApiException(ErrorCode.API_KEY_INVALID, "API 키를 확인하지 못했어요: " + reason);
            }
        }
        AiProviderInfo before = info(s);
        secrets.save(new ProviderSecret(providerId, cipher.seal(key, providerId), ApiKeyRules.last4(key), adminUserId,
                null));
        keys.invalidate(providerId);
        return new KeyChange(before, info(s));
    }

    @Override
    @Transactional
    public Optional<KeyChange> deleteKey(String providerId) {
        AiProviderSetting s = settings.findById(providerId).orElseThrow(ApiException::notFound);
        AiProviderInfo before = info(s);
        if (!secrets.delete(providerId)) {
            return Optional.empty();
        }
        keys.invalidate(providerId);
        return Optional.of(new KeyChange(before, info(s)));
    }

    private AiProviderInfo info(AiProviderSetting s) {
        ApiKeyService.KeyState key = keys.state(s);
        boolean configured = key.choice().present();
        String updatedBy = key.updatedBy() == null ? null
                : users.find(key.updatedBy()).map(UserAccount::email).orElse(null);
        return new AiProviderInfo(s.providerId(), s.type().name(), s.displayName(), s.model(), s.apiKeyEnv(), configured,
                s.enabled(), s.priority(), s.options(), AiProviderSelection.usable(s, configured), key.source().name(),
                key.last4(), key.updatedAt(), updatedBy, keys.storeEnabled());
    }
}
