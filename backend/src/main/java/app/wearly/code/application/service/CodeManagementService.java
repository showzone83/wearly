package app.wearly.code.application.service;

import app.wearly.code.application.port.in.CodeInfo;
import app.wearly.code.application.port.in.CodeManagementUseCase;
import app.wearly.code.application.port.in.CreateCodeCommand;
import app.wearly.code.application.port.out.CodePort;
import app.wearly.code.application.port.out.CodeSnapshotPort;
import app.wearly.code.application.port.out.CodeUsagePort;
import app.wearly.code.domain.Code;
import app.wearly.code.domain.CodeAttrs;
import app.wearly.code.domain.CodeRules;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.PatchBody;
import app.wearly.common.domain.code.CodeGroups;
import app.wearly.common.domain.code.CodeSnapshot;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 코드 관리 (07 문서 4장). 쓰기는 JPA(CodePort), 사용 수 집계는 MyBatis(CodeUsagePort). 바꾸면 커밋 뒤 코드 캐시를 다시
 * 읽는다(/codes version이 바뀜).
 */
@Service
public class CodeManagementService implements CodeManagementUseCase {

  private final CodePort codes;
  private final CodeUsagePort usage;
  private final CodeSnapshotPort snapshots;

  public CodeManagementService(CodePort codes, CodeUsagePort usage, CodeSnapshotPort snapshots) {
    this.codes = codes;
    this.usage = usage;
    this.snapshots = snapshots;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Long> itemUsage() {
    return usage.itemUsage();
  }

  @Override
  @Transactional
  public CodeInfo create(String group, CreateCodeCommand req) {
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
    Code saved =
        codes.save(
            Code.newCode(
                group,
                code,
                label,
                parent,
                sortOrder,
                req.active() == null || req.active(),
                attrs));
    codes.flush();
    if (CodeGroups.COLOR.equals(group)) {
      syncPairs(code, List.of(), CodeAttrs.pairsOf(attrs));
    }
    snapshots.refreshAfterCommit();
    return info(saved);
  }

  @Override
  @Transactional
  public Change patch(String group, String code, PatchBody body) {
    CodeSnapshot snapshot = requireGroup(group);
    Code entity = codes.find(group, code).orElseThrow(ApiException::notFound);
    if (body == null || !body.isObject()) {
      throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
    }
    CodeInfo before = info(entity);
    List<FieldError> errors = new ArrayList<>();
    if (body.has("code") && !code.equals(body.string("code"))) {
      errors.add(new FieldError("code", "코드는 바꿀 수 없어요."));
    }
    String label = entity.label();
    if (body.has("label")) {
      label = CodeRules.label(body.isString("label") ? body.string("label") : null, errors);
    }
    String parent = entity.parentCode();
    if (body.has("parent")) {
      String raw =
          body.isNull("parent") ? null : body.isString("parent") ? body.string("parent") : "";
      parent = CodeRules.parent(group, raw, snapshot, errors);
    }
    int sortOrder = entity.sortOrder();
    if (body.has("sortOrder")) {
      if (body.isInt("sortOrder")) {
        sortOrder = body.intValue("sortOrder");
      } else {
        errors.add(new FieldError("sortOrder", "정수여야 해요."));
      }
    }
    boolean active = entity.active();
    if (body.has("active")) {
      if (body.isBoolean("active")) {
        active = body.bool("active");
      } else {
        errors.add(new FieldError("active", "true 또는 false여야 해요."));
      }
    }
    Map<String, Object> attrs = entity.attrs();
    if (body.has("attrs")) {
      if (!body.isObject("attrs")) {
        errors.add(new FieldError("attrs", "JSON 객체여야 해요."));
      } else {
        attrs = CodeAttrs.validate(group, body.object("attrs"), code, snapshot, errors);
      }
    }
    ApiException.throwIfAny(errors);
    List<String> oldPairs = CodeAttrs.pairsOf(entity.attrs());
    entity.update(label, parent, sortOrder, active, attrs);
    Code saved = codes.save(entity);
    codes.flush();
    if (CodeGroups.COLOR.equals(group)) {
      syncPairs(code, oldPairs, CodeAttrs.pairsOf(attrs));
    }
    snapshots.refreshAfterCommit();
    return new Change(before, info(saved));
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
    codes.saveAllAndFlush(all);
    snapshots.refreshAfterCommit();
    return before;
  }

  @Override
  @Transactional
  public CodeInfo delete(String group, String code) {
    requireGroup(group);
    Code entity = codes.find(group, code).orElseThrow(ApiException::notFound);
    long items = usage.countItemsUsing(group, code);
    long profiles = usage.countProfilesUsing(group, code);
    long children =
        CodeGroups.CATEGORY.equals(group) ? codes.countChildren(CodeGroups.SUB_TYPE, code) : 0;
    if (items > 0 || profiles > 0 || children > 0) {
      List<String> reasons = new ArrayList<>();
      if (items > 0) {
        reasons.add("아이템 " + items + "개");
      }
      if (profiles > 0) {
        reasons.add("프로필 " + profiles + "개");
      }
      if (children > 0) {
        reasons.add("하위 세부 종류 " + children + "개");
      }
      throw new ApiException(
          ErrorCode.CODE_IN_USE, String.join(", ", reasons) + "에서 쓰고 있어 삭제할 수 없어요. 대신 비활성화해 주세요.");
    }
    CodeInfo before = info(entity);
    if (CodeGroups.COLOR.equals(group)) {
      syncPairs(code, CodeAttrs.pairsOf(entity.attrs()), List.of());
    }
    codes.delete(entity);
    codes.flush();
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
    codes.flush();
  }

  private CodeSnapshot requireGroup(String group) {
    CodeSnapshot s = snapshots.current();
    if (!s.hasGroup(group)) {
      throw ApiException.notFound();
    }
    return s;
  }

  private CodeInfo info(Code c) {
    return new CodeInfo(
        c.groupCode(),
        c.code(),
        c.label(),
        c.parentCode(),
        c.sortOrder(),
        c.active(),
        c.attrs(),
        usage.countItemsUsing(c.groupCode(), c.code()));
  }
}
