package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminAiProviderUseCase;
import app.wearly.admin.application.port.in.dto.AdminProviderDto;
import app.wearly.admin.application.port.in.dto.AdminProviderDto.TodayDto;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.application.port.out.dto.ProviderStatDto;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.ai.application.port.in.AiModelListUseCase;
import app.wearly.ai.application.port.in.AiProviderAdminUseCase;
import app.wearly.ai.application.port.in.AiProviderKeyUseCase;
import app.wearly.ai.application.port.in.ProviderTestUseCase;
import app.wearly.ai.application.port.in.dto.AiModelListDto;
import app.wearly.ai.application.port.in.dto.AiProviderDto;
import app.wearly.ai.application.port.in.dto.CreateProviderDto;
import app.wearly.ai.application.port.in.dto.KeyChangeDto;
import app.wearly.ai.application.port.in.dto.PatchProviderDto;
import app.wearly.ai.application.port.in.dto.ProviderCatalogDto;
import app.wearly.ai.application.port.in.dto.ProviderTestResultDto;
import app.wearly.ai.application.port.in.dto.VerifiedKeyDto;
import app.wearly.auth.application.port.in.AccountUseCase;
import app.wearly.common.application.ImageUpload;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 AI 공급자 관리 (07 문서 3·4장). 실제 변경은 ai 컨텍스트의 유스케이스가 하고, 여기서는 오늘 호출 집계(MyBatis)를 붙인 응답을 만들고 변경 이력을
 * 같은 트랜잭션에 남긴다.
 */
@Service
public class AdminAiService implements AdminAiProviderUseCase {

  private final AiProviderAdminUseCase providers;
  private final AiProviderKeyUseCase keys;
  private final AdminAiKeyWriter keyWriter;
  private final ProviderTestUseCase tester;
  private final AiModelListUseCase modelLists;
  private final AccountUseCase accounts;
  private final AdminQueryPort queries;
  private final RecordAuditPort audit;
  private final Clock clock;

  public AdminAiService(
      AiProviderAdminUseCase providers,
      AiProviderKeyUseCase keys,
      AdminAiKeyWriter keyWriter,
      ProviderTestUseCase tester,
      AiModelListUseCase modelLists,
      AccountUseCase accounts,
      AdminQueryPort queries,
      RecordAuditPort audit,
      Clock clock) {
    this.providers = providers;
    this.keys = keys;
    this.keyWriter = keyWriter;
    this.tester = tester;
    this.modelLists = modelLists;
    this.accounts = accounts;
    this.queries = queries;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminProviderDto> list() {
    Map<String, TodayDto> today = today();
    return providers.list().stream()
        .map(p -> view(p, today.getOrDefault(p.providerId(), TodayDto.NONE)))
        .toList();
  }

  @Override
  public ProviderCatalogDto types() {
    return providers.catalog();
  }

  /** 트랜잭션 없이: 제공사 HTTP 호출 동안 DB 연결을 잡지 않는다. 읽기라 변경 이력은 없다 */
  @Override
  public AiModelListDto models(String providerId, boolean refresh) {
    return modelLists.models(providerId, refresh);
  }

  @Override
  @Transactional
  public AdminProviderDto create(long adminId, CreateProviderDto command) {
    AdminProviderDto after = view(providers.create(command), TodayDto.NONE);
    audit.record(
        adminId,
        "AI_PROVIDER_CREATE",
        AuditTargets.AI_PROVIDER,
        after.providerId(),
        null,
        AuditSnapshots.provider(after));
    return after;
  }

  @Override
  @Transactional
  public AdminProviderDto patch(long adminId, String providerId, PatchProviderDto patch) {
    AiProviderDto current = providers.get(providerId);
    Map<String, TodayDto> today = today();
    TodayDto t = today.getOrDefault(providerId, TodayDto.NONE);
    AdminProviderDto before = view(current, t);
    AdminProviderDto after = view(providers.patch(providerId, patch), t);
    audit.record(
        adminId,
        "AI_PROVIDER_UPDATE",
        AuditTargets.AI_PROVIDER,
        providerId,
        AuditSnapshots.provider(before),
        AuditSnapshots.provider(after));
    return after;
  }

  @Override
  @Transactional
  public void delete(long adminId, String providerId) {
    AdminProviderDto before = view(providers.delete(providerId), TodayDto.NONE);
    audit.record(
        adminId,
        "AI_PROVIDER_DELETE",
        AuditTargets.AI_PROVIDER,
        providerId,
        AuditSnapshots.provider(before),
        null);
  }

  /**
   * 트랜잭션 없이 404 → 409 → 403 → 키 확인(400, 외부 HTTP)까지 하고, 저장과 변경 이력만 트랜잭션으로 묶는다 (M6). 외부 API가 느려도 그동안
   * DB 연결을 잡지 않는다.
   */
  @Override
  public AdminProviderDto setKey(
      long adminId, String providerId, String apiKey, String currentPassword, Boolean verify) {
    AiProviderDto current = providers.get(providerId);
    if (!current.secretStoreEnabled()) {
      throw new ApiException(ErrorCode.SECRET_KEY_MISSING);
    }
    reauthenticate(adminId, currentPassword);
    VerifiedKeyDto key = keys.checkKey(providerId, apiKey, verify == null || verify);
    KeyChangeDto change = keyWriter.save(adminId, key);
    return view(change.after(), today().getOrDefault(providerId, TodayDto.NONE));
  }

  @Override
  @Transactional
  public AdminProviderDto deleteKey(long adminId, String providerId, String currentPassword) {
    providers.get(providerId);
    reauthenticate(adminId, currentPassword);
    Optional<KeyChangeDto> change = keys.deleteKey(providerId);
    change.ifPresent(
        c ->
            audit.record(
                adminId,
                "AI_PROVIDER_KEY_DELETE",
                AuditTargets.AI_PROVIDER,
                providerId,
                keyState(c.before()),
                keyState(c.after())));
    AiProviderDto after =
        change.map(KeyChangeDto::after).orElseGet(() -> providers.get(providerId));
    return view(after, today().getOrDefault(providerId, TodayDto.NONE));
  }

  /** 요청한 관리자의 비밀번호를 다시 확인한다 (틀리거나 비면 403 REAUTH_FAILED) */
  private void reauthenticate(long adminId, String currentPassword) {
    if (!accounts.passwordMatches(adminId, currentPassword)) {
      throw new ApiException(ErrorCode.REAUTH_FAILED);
    }
  }

  private static Map<String, Object> keyState(AiProviderDto p) {
    return AuditSnapshots.providerKey(p.keySource(), p.keyLast4());
  }

  @Override
  public ProviderTestResultDto test(long adminId, String providerId, ImageUpload image) {
    return tester.test(adminId, providerId, image);
  }

  private static AdminProviderDto view(AiProviderDto p, TodayDto today) {
    return new AdminProviderDto(
        p.providerId(),
        p.type(),
        p.displayName(),
        p.model(),
        p.apiKeyEnv(),
        p.keyConfigured(),
        p.enabled(),
        p.priority(),
        p.options(),
        p.usable(),
        today,
        p.keySource(),
        p.keyLast4(),
        p.keyUpdatedAt(),
        p.keyUpdatedBy(),
        p.secretStoreEnabled());
  }

  /** 오늘(한국 시간) 공급자별 호출 수 (사진 분석 + 시험 분석) */
  private Map<String, TodayDto> today() {
    LocalDate day = Zones.today(clock);
    Map<String, TodayDto> out = new HashMap<>();
    for (ProviderStatDto r :
        queries.providerStats(Zones.startOf(day), Zones.startOf(day.plusDays(1)))) {
      out.put(r.providerId(), new TodayDto(r.success(), r.failed()));
    }
    return out;
  }
}
