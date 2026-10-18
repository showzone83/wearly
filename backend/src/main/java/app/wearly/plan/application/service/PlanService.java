package app.wearly.plan.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.application.Patch;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.plan.application.port.in.PlanUseCase;
import app.wearly.plan.application.port.in.dto.PatchPlanDto;
import app.wearly.plan.application.port.in.dto.PlanBulkResultDto;
import app.wearly.plan.application.port.in.dto.PlanDto;
import app.wearly.plan.application.port.in.dto.PlanInputDto;
import app.wearly.plan.application.port.in.dto.PlanPlaceDto;
import app.wearly.plan.application.port.in.dto.PlanSaveResultDto;
import app.wearly.plan.application.port.in.dto.PlanSearchDto;
import app.wearly.plan.application.port.in.dto.PlanWeatherDto;
import app.wearly.plan.application.port.out.PlanFilter;
import app.wearly.plan.application.port.out.PlanPort;
import app.wearly.plan.application.port.out.PlanQueryPort;
import app.wearly.plan.domain.Plan;
import app.wearly.plan.domain.PlanDraft;
import app.wearly.plan.domain.PlanOutfits;
import app.wearly.plan.domain.PlanRules;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.wardrobe.application.port.in.ItemExistenceUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예정 코디 쓰기(JPA)·목록(MyBatis). 쓰기는 회원별 잠금 안에서 개수 상한(500)과 clientId 중복을 지킨다. 응답의 missingItemIds는
 * wardrobe 컨텍스트의 유스케이스로 아이템이 아직 있는지 물어 만든다.
 */
@Service
public class PlanService implements PlanUseCase {

  /** PLAN_LIMIT params */
  private static final Map<String, Object> LIMIT_PARAMS =
      Map.of("max", PlanRules.MAX_PLANS_PER_USER);

  private final PlanPort plans;
  private final PlanQueryPort queries;
  private final CodeCatalog catalog;
  private final ItemExistenceUseCase items;
  private final Clock clock;

  public PlanService(
      PlanPort plans,
      PlanQueryPort queries,
      CodeCatalog catalog,
      ItemExistenceUseCase items,
      Clock clock) {
    this.plans = plans;
    this.queries = queries;
    this.catalog = catalog;
    this.items = items;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PlanDto> list(PlanSearchDto q) {
    List<FieldError> errors = new ArrayList<>();
    LocalDate from = queryDate(q.from(), "from", errors);
    LocalDate to = queryDate(q.to(), "to", errors);
    if (from != null && to != null && to.isBefore(from)) {
      errors.add(new FieldError("to", FieldErrorCode.OUT_OF_RANGE, Map.of("min", from.toString())));
    }
    BusinessException.throwIfAny(errors);
    return withMissing(q.userId(), queries.findPlans(new PlanFilter(q.userId(), from, to)));
  }

  @Override
  @Transactional
  public PlanSaveResultDto create(long userId, PlanInputDto dto) {
    if (dto == null) {
      throw BusinessException.validation(FieldError.invalidType("body", "object"));
    }
    PlanDraft draft = dto.toDraft();
    String clientId = PlanRules.clientId(draft.clientId());
    queries.lockUser(userId);
    Plan existing =
        clientId == null
            ? null
            : plans.findByClientIds(userId, List.of(clientId)).stream().findFirst().orElse(null);
    BusinessException.throwIfAny(
        PlanRules.validate(
            draft,
            existing == null ? null : existing.toDraft(),
            today(),
            catalog.current(),
            "",
            false));
    if (existing != null) {
      existing.apply(PlanRules.toInput(draft));
      return new PlanSaveResultDto(one(userId, plans.save(existing)), false);
    }
    checkLimit(userId, 1);
    Plan saved = plans.save(Plan.create(userId, clientId, PlanRules.toInput(draft)));
    return new PlanSaveResultDto(one(userId, saved), true);
  }

  @Override
  @Transactional
  public PlanBulkResultDto bulk(long userId, List<PlanInputDto> inputs) {
    if (inputs == null) {
      throw BusinessException.validation(FieldError.required("items"));
    }
    if (inputs.size() > PlanRules.BULK_MAX) {
      throw BusinessException.validation(FieldError.tooMany("items", PlanRules.BULK_MAX));
    }
    List<PlanDraft> drafts = new ArrayList<>(inputs.size());
    List<FieldError> errors = new ArrayList<>();
    Set<String> clientIds = new LinkedHashSet<>();
    for (int i = 0; i < inputs.size(); i++) {
      PlanInputDto dto = inputs.get(i);
      if (dto == null) {
        errors.add(FieldError.required("items[" + i + "]"));
        drafts.add(null);
        continue;
      }
      PlanDraft draft = dto.toDraft();
      drafts.add(draft);
      String clientId = PlanRules.clientId(draft.clientId());
      if (clientId != null && !clientIds.add(clientId)) {
        errors.add(new FieldError("items[" + i + "].clientId", FieldErrorCode.DUPLICATE));
      }
    }
    BusinessException.throwIfAny(errors);

    queries.lockUser(userId);
    Map<String, Plan> existing = new HashMap<>();
    if (!clientIds.isEmpty()) {
      plans.findByClientIds(userId, clientIds).forEach(p -> existing.put(p.clientId(), p));
    }
    LocalDate today = today();
    CodeSnapshot codes = catalog.current();
    for (int i = 0; i < drafts.size(); i++) {
      PlanDraft draft = drafts.get(i);
      Plan before = existing.get(PlanRules.clientId(draft.clientId()));
      errors.addAll(
          PlanRules.validate(
              draft,
              before == null ? null : before.toDraft(),
              today,
              codes,
              "items[" + i + "].",
              true));
    }
    BusinessException.throwIfAny(errors);

    int creates = drafts.size() - existing.size();
    checkLimit(userId, creates);
    List<Plan> saved = new ArrayList<>(drafts.size());
    for (PlanDraft draft : drafts) {
      String clientId = PlanRules.clientId(draft.clientId());
      Plan before = existing.get(clientId);
      if (before != null) {
        before.apply(PlanRules.toInput(draft));
        saved.add(plans.save(before));
      } else {
        saved.add(plans.save(Plan.create(userId, clientId, PlanRules.toInput(draft))));
      }
    }
    return new PlanBulkResultDto(withMissing(userId, saved), creates, existing.size());
  }

  @Override
  @Transactional
  public PlanDto patch(long userId, long planId, PatchPlanDto patch) {
    Plan plan = find(userId, planId);
    if (patch == null) {
      throw BusinessException.validation(FieldError.invalidType("body", "object"));
    }
    BusinessException.throwIfAny(patch.inputErrors());
    PlanDraft before = plan.toDraft();
    PlanDraft merged =
        new PlanDraft(
            before.clientId(),
            patch.date().orElse(before.date()),
            patch.timeOfDay().orElse(before.timeOfDay()),
            patch.title().orElse(before.title()),
            patch.occasion().orElse(before.occasion()),
            patch.concepts().orElse(before.concepts()),
            patch.memo().orElse(before.memo()),
            place(patch.place(), before.place()),
            weather(patch.weather(), before.weather()),
            patch.weatherSource().orElse(before.weatherSource()),
            patch.weatherCheckedAt().orElse(before.weatherCheckedAt()),
            patch.forecastReady().orElse(before.forecastReady()),
            patch.outfit().orElse(before.outfit()));
    BusinessException.throwIfAny(
        PlanRules.validate(merged, before, today(), catalog.current(), "", false));
    plan.apply(PlanRules.toInput(merged));
    return one(userId, plans.save(plan));
  }

  @Override
  @Transactional
  public void delete(long userId, long planId) {
    plans.delete(find(userId, planId));
  }

  private Plan find(long userId, long planId) {
    return plans.find(userId, planId).orElseThrow(BusinessException::notFound);
  }

  /** 지금 개수 + 새로 만들 수가 상한을 넘으면 409 PLAN_LIMIT (잠금 안에서 부른다) */
  private void checkLimit(long userId, int creates) {
    if (creates > 0 && queries.count(userId) + creates > PlanRules.MAX_PLANS_PER_USER) {
      throw new BusinessException(ErrorCode.PLAN_LIMIT, LIMIT_PARAMS);
    }
  }

  private PlanDto one(long userId, Plan plan) {
    return withMissing(userId, List.of(plan)).getFirst();
  }

  /** 코디 아이템 중 옷장에 없는 ID를 붙인다 (모든 예정의 서버 아이템 ID를 한 번에 묻는다) */
  private List<PlanDto> withMissing(long userId, List<Plan> found) {
    Set<Long> ids = new HashSet<>();
    for (Plan p : found) {
      for (String id : PlanOutfits.itemIds(p.input().outfit())) {
        Long n = PlanOutfits.serverItemId(id);
        if (n != null) {
          ids.add(n);
        }
      }
    }
    Set<Long> existing = ids.isEmpty() ? Set.of() : items.existingIds(userId, ids);
    return found.stream()
        .map(p -> PlanDto.from(p, PlanOutfits.missing(p.input().outfit(), existing)))
        .toList();
  }

  private LocalDate today() {
    return Zones.today(clock);
  }

  private static PlanDraft.Place place(Patch<PlanPlaceDto> patch, PlanDraft.Place current) {
    if (!patch.present() || patch.invalid()) {
      return current;
    }
    return patch.value() == null ? null : patch.value().toDraft();
  }

  private static PlanDraft.Weather weather(Patch<PlanWeatherDto> patch, PlanDraft.Weather current) {
    if (!patch.present() || patch.invalid()) {
      return current;
    }
    return patch.value() == null ? null : patch.value().toDraft();
  }

  private static LocalDate queryDate(String raw, String field, List<FieldError> errors) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(raw.strip());
    } catch (DateTimeParseException e) {
      errors.add(FieldError.invalidFormat(field, "YYYY-MM-DD"));
      return null;
    }
  }
}
