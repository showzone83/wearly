package app.wearly.ai.application.service;

import app.wearly.ai.application.port.in.AiProviderAdminUseCase;
import app.wearly.ai.application.port.in.AiProviderKeyUseCase;
import app.wearly.ai.application.port.in.dto.AiProviderDto;
import app.wearly.ai.application.port.in.dto.CreateProviderDto;
import app.wearly.ai.application.port.in.dto.KeyChangeDto;
import app.wearly.ai.application.port.in.dto.PatchProviderDto;
import app.wearly.ai.application.port.in.dto.ProviderCatalogDto;
import app.wearly.ai.application.port.in.dto.ProviderTypeDto;
import app.wearly.ai.application.port.in.dto.VerifiedKeyDto;
import app.wearly.ai.application.port.out.AiProviderPort;
import app.wearly.ai.application.port.out.ApiKeyVerifierPort;
import app.wearly.ai.application.port.out.ProviderSecretPort;
import app.wearly.ai.application.port.out.SecretCipherPort;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderRules;
import app.wearly.ai.domain.AiProviderSelection;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import app.wearly.ai.domain.AiSettings;
import app.wearly.ai.domain.ApiKeyRules;
import app.wearly.ai.domain.BuiltInModels;
import app.wearly.ai.domain.ModelListRules;
import app.wearly.ai.domain.ProviderSecret;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 AI 공급자 관리 (07 문서 3·4장)와 웹 입력 키 (6장). 쓰기는 JPA, 키는 AES-GCM으로 암호화해 저장한다 */
@Service
public class AiProviderAdminService implements AiProviderAdminUseCase, AiProviderKeyUseCase {

  private static final Logger log = LoggerFactory.getLogger(AiProviderAdminService.class);

  static final int MAX_PRIORITY = 10000;

  private final AiProviderPort settings;
  private final AiProviderRegistry registry;
  private final ApiKeyService keys;
  private final ProviderSecretPort secrets;
  private final SecretCipherPort cipher;
  private final ApiKeyVerifierPort verifier;
  private final UserUseCase users;
  private final AiSettings aiSettings;
  private final AiModelListService modelLists;
  private final BuiltInModels builtInModels;

  public AiProviderAdminService(
      AiProviderPort settings,
      AiProviderRegistry registry,
      ApiKeyService keys,
      ProviderSecretPort secrets,
      SecretCipherPort cipher,
      ApiKeyVerifierPort verifier,
      UserUseCase users,
      AiSettings aiSettings,
      AiModelListService modelLists,
      BuiltInModels builtInModels) {
    this.settings = settings;
    this.registry = registry;
    this.keys = keys;
    this.secrets = secrets;
    this.cipher = cipher;
    this.verifier = verifier;
    this.users = users;
    this.aiSettings = aiSettings;
    this.modelLists = modelLists;
    this.builtInModels = builtInModels;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AiProviderDto> list() {
    return settings.findAllOrdered().stream().map(this::info).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public AiProviderDto get(String providerId) {
    return info(settings.findById(providerId).orElseThrow(BusinessException::notFound));
  }

  @Override
  public ProviderCatalogDto catalog() {
    return new ProviderCatalogDto(types(), aiSettings.allowBaseUrl(), registry.isMockMode());
  }

  private List<ProviderTypeDto> types() {
    return Arrays.stream(AiProviderType.values())
        .map(
            t ->
                new ProviderTypeDto(
                    t.name(),
                    t.description(),
                    t.options().stream()
                        .map(
                            o ->
                                new ProviderTypeDto.Option(
                                    o.name(),
                                    o.kind(),
                                    o.description(),
                                    o.defaultValue(),
                                    o.min(),
                                    o.max(),
                                    o.values()))
                        .toList(),
                    ModelListRules.sorted(builtInModels.of(t)).stream()
                        .map(
                            m ->
                                new ProviderTypeDto.Model(m.id(), m.displayName(), m.recommended()))
                        .toList()))
        .toList();
  }

  @Override
  @Transactional
  public AiProviderDto create(CreateProviderDto req) {
    if (req == null) {
      throw BusinessException.validation(FieldError.invalidType("body", "object"));
    }
    List<FieldError> errors = new ArrayList<>();
    final String id = AiProviderRules.providerId(req.providerId(), errors);
    AiProviderType type = AiProviderRules.type(req.type(), errors);
    final String displayName = AiProviderRules.displayName(req.displayName(), errors);
    String model = AiProviderRules.model(req.model(), errors);
    final String apiKeyEnv = AiProviderRules.apiKeyEnv(req.apiKeyEnv(), errors);
    boolean enabled = req.enabled() != null && req.enabled();
    Integer priority = req.priority();
    if (priority != null && (priority < 0 || priority > 10000)) {
      errors.add(FieldError.outOfRange("priority", 0, MAX_PRIORITY));
    }
    Map<String, Object> options = req.options() == null ? Map.of() : req.options();
    if (type != null) {
      errors.addAll(type.validate(options, aiSettings.allowBaseUrl()));
    }
    if (enabled && model == null) {
      // 사용하려면 모델이 필요하다
      errors.add(FieldError.required("model"));
    }
    BusinessException.throwIfAny(errors);
    if (settings.exists(id)) {
      throw new BusinessException(ErrorCode.PROVIDER_EXISTS);
    }
    AiProviderSetting s =
        new AiProviderSetting(
            id,
            type,
            displayName,
            model,
            apiKeyEnv,
            enabled,
            priority == null ? settings.maxPriority() + 1 : priority,
            AiProviderRules.withoutNulls(options),
            null);
    return info(settings.create(s));
  }

  @Override
  @Transactional
  public AiProviderDto patch(String providerId, PatchProviderDto patch) {
    AiProviderSetting s = settings.findById(providerId).orElseThrow(BusinessException::notFound);
    if (patch == null) {
      throw BusinessException.validation(FieldError.invalidType("body", "object"));
    }
    List<FieldError> errors = new ArrayList<>();
    if (patch.providerId().present() && !providerId.equals(patch.providerId().value())) {
      errors.add(new FieldError("providerId", FieldErrorCode.IMMUTABLE));
    }
    final AiProviderType type =
        patch.type().present() ? AiProviderRules.type(patch.type().value(), errors) : s.type();
    final String displayName =
        patch.displayName().present()
            ? AiProviderRules.displayName(patch.displayName().value(), errors)
            : s.displayName();
    final String model =
        patch.model().present() ? AiProviderRules.model(patch.model().value(), errors) : s.model();
    final String apiKeyEnv =
        patch.apiKeyEnv().present()
            ? AiProviderRules.apiKeyEnv(patch.apiKeyEnv().value(), errors)
            : s.apiKeyEnv();
    boolean enabled = patch.enabled().apply("enabled", s.enabled(), errors);
    int priority = patch.priority().apply("priority", s.priority(), errors);
    if (patch.priority().present() && !patch.priority().invalid() && !validPriority(priority)) {
      errors.add(FieldError.outOfRange("priority", 0, MAX_PRIORITY));
      priority = s.priority();
    }
    Map<String, Object> options = patch.options().apply("options", s.options(), errors);
    if (type != null) {
      errors.addAll(type.validate(options, aiSettings.allowBaseUrl()));
    }
    if (enabled && (model == null || model.isBlank())) {
      errors.add(FieldError.required("model"));
    }
    BusinessException.throwIfAny(errors);
    s.update(
        type,
        displayName,
        model,
        apiKeyEnv,
        enabled,
        priority,
        AiProviderRules.withoutNulls(options));
    // 종류·키 이름·API 주소가 바뀌었을 수 있다 (캐시 범위에도 들어 있지만 한 번 더)
    modelLists.invalidate(providerId);
    return info(settings.update(s));
  }

  @Override
  @Transactional
  public AiProviderDto delete(String providerId) {
    AiProviderSetting s = settings.findById(providerId).orElseThrow(BusinessException::notFound);
    final AiProviderDto before = info(s);
    settings.delete(providerId);
    // 웹 키 행은 FK ON DELETE CASCADE로 함께 지워진다
    keys.invalidate(providerId);
    modelLists.invalidate(providerId);
    return before;
  }

  /** 마스터 키(WEARLY_SECRET_KEY)가 없으면 409 SECRET_KEY_MISSING */
  private void requireSecretStore() {
    if (!keys.storeEnabled()) {
      throw new BusinessException(ErrorCode.SECRET_KEY_MISSING);
    }
  }

  /** 트랜잭션 없이: 공급자 HTTP 호출 동안 DB 연결을 잡지 않는다 (M6) */
  @Override
  public VerifiedKeyDto checkKey(String providerId, String apiKey, boolean verify) {
    AiProviderSetting s = settings.findById(providerId).orElseThrow(BusinessException::notFound);
    requireSecretStore();
    List<FieldError> errors = new ArrayList<>();
    String key = ApiKeyRules.normalize(apiKey, errors);
    BusinessException.throwIfAny(errors);
    if (verify) {
      try {
        verifier.verify(s, key);
      } catch (AiProviderException e) {
        logRejectedKey(providerId, key, e);
        // 응답에는 사유 코드만 (공급자 응답 원문은 로그에만)
        throw new BusinessException(ErrorCode.API_KEY_INVALID, Map.of("reason", e.reason()));
      }
    }
    return new VerifiedKeyDto(providerId, key);
  }

  @Override
  @Transactional
  public KeyChangeDto saveKey(VerifiedKeyDto verified, long adminUserId) {
    String providerId = verified.providerId();
    AiProviderSetting s = settings.findById(providerId).orElseThrow(BusinessException::notFound);
    requireSecretStore();
    final AiProviderDto before = info(s);
    secrets.save(
        new ProviderSecret(
            providerId,
            cipher.seal(verified.key(), providerId),
            ApiKeyRules.last4(verified.key()),
            adminUserId,
            null));
    keys.invalidate(providerId);
    modelLists.invalidate(providerId);
    return new KeyChangeDto(before, info(s));
  }

  @Override
  @Transactional
  public Optional<KeyChangeDto> deleteKey(String providerId) {
    AiProviderSetting s = settings.findById(providerId).orElseThrow(BusinessException::notFound);
    final AiProviderDto before = info(s);
    if (!secrets.delete(providerId)) {
      return Optional.empty();
    }
    keys.invalidate(providerId);
    modelLists.invalidate(providerId);
    return Optional.of(new KeyChangeDto(before, info(s)));
  }

  /** 키 확인 실패의 자세한 내용(공급자 응답, 키 조각은 지운다)은 로그로만 */
  private static void logRejectedKey(String providerId, String key, AiProviderException e) {
    String detail = e.getMessage() == null ? null : e.getMessage().replace(key, "***");
    log.warn("API 키 확인 실패 provider={} reason={} detail={}", providerId, e.reason(), detail);
  }

  private static boolean validPriority(int priority) {
    return priority >= 0 && priority <= MAX_PRIORITY;
  }

  private AiProviderDto info(AiProviderSetting s) {
    ApiKeyService.KeyState key = keys.state(s);
    boolean configured = key.choice().present();
    String updatedBy =
        key.updatedBy() == null
            ? null
            : users.find(key.updatedBy()).map(UserAccountDto::email).orElse(null);
    return new AiProviderDto(
        s.providerId(),
        s.type().name(),
        s.displayName(),
        s.model(),
        s.apiKeyEnv(),
        configured,
        s.enabled(),
        s.priority(),
        s.options(),
        AiProviderSelection.usable(s, configured),
        key.source().name(),
        key.last4(),
        key.updatedAt(),
        updatedBy,
        keys.storeEnabled());
  }
}
