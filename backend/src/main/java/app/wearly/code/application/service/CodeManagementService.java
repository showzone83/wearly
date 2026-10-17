package app.wearly.code.application.service;

import app.wearly.code.application.port.in.CodeManagementUseCase;
import app.wearly.code.application.port.in.CodeUsageSource;
import app.wearly.code.application.port.in.dto.CodeChangeDto;
import app.wearly.code.application.port.in.dto.CodeDto;
import app.wearly.code.application.port.in.dto.CodeUsageCountDto;
import app.wearly.code.application.port.in.dto.CreateCodeDto;
import app.wearly.code.application.port.in.dto.PatchCodeDto;
import app.wearly.code.application.port.out.CodePort;
import app.wearly.code.application.port.out.CodeSnapshotPort;
import app.wearly.code.domain.Code;
import app.wearly.code.domain.CodeAttrs;
import app.wearly.code.domain.CodeRules;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.code.CodeGroups;
import app.wearly.common.domain.code.CodeSnapshot;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 코드 관리 (07 문서 4장). 쓰기는 JPA(CodePort). 사용 수는 코드를 쓰는 컨텍스트(옷장·프로필)에 CodeUsageSource로 묻는다 (code는
 * 다른 컨텍스트의 테이블을 읽지 않는다, H1). 바꾸면 커밋 뒤 코드 캐시를 다시 읽는다(/codes version이 바뀜).
 */
@Service
public class CodeManagementService implements CodeManagementUseCase {

  private final CodePort codes;
  private final List<CodeUsageSource> sources;
  private final CodeSnapshotPort snapshots;

  public CodeManagementService(
      CodePort codes, List<CodeUsageSource> sources, CodeSnapshotPort snapshots) {
    this.codes = codes;
    this.sources = sources.stream().sorted(Comparator.comparing(CodeUsageSource::kind)).toList();
    this.snapshots = snapshots;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Long> itemUsage() {
    Map<String, Long> usage = new HashMap<>();
    for (CodeUsageSource s : sources(CodeUsageSource.Kind.ITEM)) {
      for (CodeUsageCountDto c : s.countByCode()) {
        usage.merge(CodeRules.target(c.group(), c.code()), c.count(), Long::sum);
      }
    }
    return usage;
  }

  @Override
  @Transactional
  public CodeDto create(String group, CreateCodeDto req) {
    CodeSnapshot snapshot = requireGroup(group);
    if (req == null) {
      throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
    }
    List<FieldError> errors = new ArrayList<>();
    String code = CodeRules.code(group, req.code(), errors);
    String label = CodeRules.label(req.label(), errors);
    String parent = CodeRules.parent(group, req.parent(), snapshot, errors);
    Map<String, Object> attrs = CodeAttrs.validate(group, req.attrs(), code, snapshot, errors);
    ApiException.throwIfAny(errors);
    if (codes.exists(group, code)) {
      throw new ApiException(ErrorCode.CODE_EXISTS);
    }
    int sortOrder = req.sortOrder() == null ? codes.maxSortOrder(group) + 10 : req.sortOrder();
    final Code saved =
        codes.save(
            Code.newCode(
                group,
                code,
                label,
                parent,
                sortOrder,
                req.active() == null || req.active(),
                attrs));
    if (CodeGroups.COLOR.equals(group)) {
      syncPairs(code, List.of(), CodeAttrs.pairsOf(attrs));
    }
    snapshots.refreshAfterCommit();
    return info(saved);
  }

  @Override
  @Transactional
  public CodeChangeDto patch(String group, String code, PatchCodeDto patch) {
    final CodeSnapshot snapshot = requireGroup(group);
    Code entity = codes.find(group, code).orElseThrow(ApiException::notFound);
    if (patch == null) {
      throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
    }
    final CodeDto before = info(entity);
    List<FieldError> errors = new ArrayList<>();
    if (patch.code().present() && !code.equals(patch.code().value())) {
      errors.add(new FieldError("code", "코드는 바꿀 수 없어요."));
    }
    String label =
        patch.label().present() ? CodeRules.label(patch.label().value(), errors) : entity.label();
    String parent =
        patch.parent().present()
            ? CodeRules.parent(group, patch.parent().value(), snapshot, errors)
            : entity.parentCode();
    int sortOrder = patch.sortOrder().apply("sortOrder", entity.sortOrder(), errors);
    boolean active = patch.active().apply("active", entity.active(), errors);
    Map<String, Object> attrs = entity.attrs();
    if (patch.attrs().invalid()) {
      errors.add(new FieldError("attrs", patch.attrs().error()));
    } else if (patch.attrs().present()) {
      attrs = CodeAttrs.validate(group, patch.attrs().value(), code, snapshot, errors);
    }
    ApiException.throwIfAny(errors);
    List<String> oldPairs = CodeAttrs.pairsOf(entity.attrs());
    entity.update(label, parent, sortOrder, active, attrs);
    final Code saved = codes.save(entity);
    if (CodeGroups.COLOR.equals(group)) {
      syncPairs(code, oldPairs, CodeAttrs.pairsOf(attrs));
    }
    snapshots.refreshAfterCommit();
    return new CodeChangeDto(before, info(saved));
  }

  @Override
  @Transactional
  public List<String> reorder(String group, List<String> order) {
    requireGroup(group);
    List<Code> all = codes.findByGroup(group);
    List<String> before = all.stream().map(Code::code).toList();
    if (!CodeRules.isCompleteOrder(before, order)) {
      throw ApiException.validation("codes", "그룹의 코드 " + all.size() + "개를 빠짐없이 한 번씩 보내 주세요.");
    }
    Map<String, Code> byCode = new HashMap<>();
    all.forEach(c -> byCode.put(c.code(), c));
    for (int i = 0; i < order.size(); i++) {
      byCode.get(order.get(i)).moveTo((i + 1) * 10);
    }
    codes.saveAll(all);
    snapshots.refreshAfterCommit();
    return before;
  }

  @Override
  @Transactional
  public CodeDto delete(String group, String code) {
    requireGroup(group);
    final Code entity = codes.find(group, code).orElseThrow(ApiException::notFound);
    List<String> reasons = new ArrayList<>();
    for (CodeUsageSource s : sources) {
      long n = s.countUsing(group, code);
      if (n > 0) {
        reasons.add(s.kind().label() + " " + n + "개");
      }
    }
    long children =
        CodeGroups.CATEGORY.equals(group) ? codes.countChildren(CodeGroups.SUB_TYPE, code) : 0;
    if (children > 0) {
      reasons.add("하위 세부 종류 " + children + "개");
    }
    if (!reasons.isEmpty()) {
      throw new ApiException(
          ErrorCode.CODE_IN_USE, String.join(", ", reasons) + "에서 쓰고 있어 삭제할 수 없어요. 대신 비활성화해 주세요.");
    }
    final CodeDto before = info(entity);
    if (CodeGroups.COLOR.equals(group)) {
      syncPairs(code, CodeAttrs.pairsOf(entity.attrs()), List.of());
    }
    codes.delete(entity);
    snapshots.refreshAfterCommit();
    return before;
  }

  /** COLOR pairs 양방향 반영: 새로 넣은 상대 색에는 이 색을 넣고, 뺀 상대 색에서는 이 색을 뺀다 */
  private void syncPairs(String color, List<String> oldPairs, List<String> newPairs) {
    Set<String> added = new TreeSet<>(newPairs);
    added.removeAll(oldPairs);
    Set<String> removed = new TreeSet<>(oldPairs);
    removed.removeAll(newPairs);
    List<String> touched = new ArrayList<>(added);
    touched.addAll(removed);
    if (touched.isEmpty()) {
      return;
    }
    for (Code other : codes.findIn(CodeGroups.COLOR, touched)) {
      TreeSet<String> pairs = new TreeSet<>(CodeAttrs.pairsOf(other.attrs()));
      boolean changed = added.contains(other.code()) ? pairs.add(color) : pairs.remove(color);
      if (changed) {
        Map<String, Object> attrs = new LinkedHashMap<>(other.attrs());
        attrs.put("pairs", List.copyOf(pairs));
        other.replaceAttrs(attrs);
        codes.save(other);
      }
    }
  }

  private CodeSnapshot requireGroup(String group) {
    CodeSnapshot s = snapshots.current();
    if (!s.hasGroup(group)) {
      throw ApiException.notFound();
    }
    return s;
  }

  private CodeDto info(Code c) {
    return new CodeDto(
        c.groupCode(),
        c.code(),
        c.label(),
        c.parentCode(),
        c.sortOrder(),
        c.active(),
        c.attrs(),
        countItemsUsing(c.groupCode(), c.code()));
  }

  /** 관리자 화면의 usageCount = 이 코드를 쓰는 아이템 수 (07 문서 4장) */
  private long countItemsUsing(String group, String code) {
    return sources(CodeUsageSource.Kind.ITEM).stream()
        .mapToLong(s -> s.countUsing(group, code))
        .sum();
  }

  private List<CodeUsageSource> sources(CodeUsageSource.Kind kind) {
    return sources.stream().filter(s -> s.kind() == kind).toList();
  }
}
