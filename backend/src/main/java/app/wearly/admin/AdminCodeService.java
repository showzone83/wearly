package app.wearly.admin;

import app.wearly.admin.AdminCodeDtos.AdminCodeDto;
import app.wearly.admin.AdminCodeDtos.AdminCodesResponse;
import app.wearly.admin.AdminCodeDtos.AdminGroupDto;
import app.wearly.admin.AdminCodeDtos.CreateCodeRequest;
import app.wearly.code.Code;
import app.wearly.code.CodeCatalog;
import app.wearly.code.CodeGroups;
import app.wearly.code.CodeQueryMapper;
import app.wearly.code.CodeRepository;
import app.wearly.code.CodeSnapshot;
import app.wearly.code.CodeUsageRow;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse.FieldError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 코드 관리 (07 문서 4장). 쓰기는 JPA, 사용 수 집계는 MyBatis.
 * 바꾸면 커밋 뒤 코드 캐시를 다시 읽고(/codes version이 바뀜) 변경 이력을 남긴다.
 */
@Service
public class AdminCodeService {

    /** COLOR는 영문 소문자(앱 ColorKey와 같은 모양), 그 밖은 영문 대문자. 숫자·밑줄 허용, 1~30자 */
    static final Pattern UPPER_CODE = Pattern.compile("^[A-Z0-9_]{1,30}$");
    static final Pattern LOWER_CODE = Pattern.compile("^[a-z0-9_]{1,30}$");

    private final CodeRepository codes;
    private final CodeQueryMapper queries;
    private final CodeCatalog catalog;
    private final AuditService audit;
    private final JsonMapper jsonMapper;

    public AdminCodeService(CodeRepository codes, CodeQueryMapper queries, CodeCatalog catalog, AuditService audit,
            JsonMapper jsonMapper) {
        this.codes = codes;
        this.queries = queries;
        this.catalog = catalog;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public AdminCodesResponse list() {
        CodeSnapshot s = catalog.current();
        Map<String, Long> usage = new HashMap<>();
        for (CodeUsageRow r : queries.countItemUsage()) {
            usage.merge(r.getGroupCode() + ":" + r.getCode(), r.getCount(), Long::sum);
        }
        return new AdminCodesResponse(s.version(), s.groups().stream()
                .map(g -> new AdminGroupDto(g.group(), g.name(), g.description(), g.codes().stream()
                        .map(c -> new AdminCodeDto(c.code(), c.label(), c.parent(), c.sortOrder(), c.active(), c.attrs(),
                                usage.getOrDefault(g.group() + ":" + c.code(), 0L)))
                        .toList()))
                .toList());
    }

    @Transactional
    public AdminCodeDto create(long adminId, String group, CreateCodeRequest req) {
        CodeSnapshot snapshot = requireGroup(group);
        if (req == null) {
            throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
        }
        List<FieldError> errors = new ArrayList<>();
        String code = req.code() == null ? null : req.code().strip();
        Pattern format = CodeGroups.COLOR.equals(group) ? LOWER_CODE : UPPER_CODE;
        if (code == null || !format.matcher(code).matches()) {
            errors.add(new FieldError("code", CodeGroups.COLOR.equals(group)
                    ? "영문 소문자·숫자·밑줄 1~30자여야 해요." : "영문 대문자·숫자·밑줄 1~30자여야 해요."));
        }
        String label = label(req.label(), errors);
        String parent = parent(group, req.parent(), snapshot, errors);
        Map<String, Object> attrs = CodeAttrs.validate(group, req.attrs(), code, snapshot, errors);
        throwIfAny(errors);
        if (codes.existsByGroupCodeAndCode(group, code)) {
            throw new ApiException(ErrorCode.CODE_EXISTS);
        }
        Code entity = new Code(group, code);
        int sortOrder = req.sortOrder() == null ? codes.maxSortOrder(group) + 10 : req.sortOrder();
        entity.update(label, parent, sortOrder, req.active() == null || req.active(), attrs);
        codes.saveAndFlush(entity);
        if (CodeGroups.COLOR.equals(group)) {
            syncPairs(code, List.of(), CodeAttrs.pairsOf(attrs));
        }
        AdminCodeDto after = dto(entity);
        audit.record(adminId, "CODE_CREATE", AuditService.CODE, target(group, code), null, after);
        catalog.reloadAfterCommit();
        return after;
    }

    /** 보낸 필드만 바꾼다. attrs를 보내면 attrs 전체를 바꾼다. 코드 문자열은 못 바꾼다 */
    @Transactional
    public AdminCodeDto patch(long adminId, String group, String code, JsonNode body) {
        CodeSnapshot snapshot = requireGroup(group);
        Code entity = codes.findByGroupCodeAndCode(group, code).orElseThrow(ApiException::notFound);
        if (body == null || !body.isObject()) {
            throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
        }
        AdminCodeDto before = dto(entity);
        List<FieldError> errors = new ArrayList<>();
        if (body.has("code") && !code.equals(body.get("code").asString(null))) {
            errors.add(new FieldError("code", "코드는 바꿀 수 없어요."));
        }
        String label = entity.getLabel();
        if (body.has("label")) {
            label = label(body.get("label").isString() ? body.get("label").asString() : null, errors);
        }
        String parent = entity.getParentCode();
        if (body.has("parent")) {
            JsonNode p = body.get("parent");
            parent = parent(group, p.isNull() ? null : p.isString() ? p.asString() : "", snapshot, errors);
        }
        int sortOrder = entity.getSortOrder();
        if (body.has("sortOrder")) {
            JsonNode n = body.get("sortOrder");
            if (n.isIntegralNumber() && n.canConvertToInt()) {
                sortOrder = n.asInt();
            } else {
                errors.add(new FieldError("sortOrder", "정수여야 해요."));
            }
        }
        boolean active = entity.isActive();
        if (body.has("active")) {
            JsonNode a = body.get("active");
            if (a.isBoolean()) {
                active = a.asBoolean();
            } else {
                errors.add(new FieldError("active", "true 또는 false여야 해요."));
            }
        }
        Map<String, Object> attrs = entity.getAttrs();
        if (body.has("attrs")) {
            JsonNode a = body.get("attrs");
            if (!a.isObject()) {
                errors.add(new FieldError("attrs", "JSON 객체여야 해요."));
            } else {
                Map<String, Object> raw = jsonMapper.convertValue(a, new TypeReference<LinkedHashMap<String, Object>>() {
                });
                attrs = CodeAttrs.validate(group, raw, code, snapshot, errors);
            }
        }
        throwIfAny(errors);
        List<String> oldPairs = CodeAttrs.pairsOf(entity.getAttrs());
        entity.update(label, parent, sortOrder, active, attrs);
        codes.saveAndFlush(entity);
        if (CodeGroups.COLOR.equals(group)) {
            syncPairs(code, oldPairs, CodeAttrs.pairsOf(attrs));
        }
        AdminCodeDto after = dto(entity);
        audit.record(adminId, "CODE_UPDATE", AuditService.CODE, target(group, code), before, after);
        catalog.reloadAfterCommit();
        return after;
    }

    /** 보낸 순서대로 sortOrder를 10, 20, …으로. 그룹의 코드 전부를 한 번씩 보내야 한다 */
    @Transactional
    public void reorder(long adminId, String group, List<String> order) {
        requireGroup(group);
        List<Code> all = codes.findByGroupCodeOrderBySortOrderAscIdAsc(group);
        Set<String> existing = new HashSet<>();
        all.forEach(c -> existing.add(c.getCode()));
        if (order == null || order.size() != all.size() || new HashSet<>(order).size() != order.size()
                || !existing.equals(new HashSet<>(order))) {
            throw ApiException.validation("codes", "그룹의 코드 " + all.size() + "개를 빠짐없이 한 번씩 보내 주세요.");
        }
        List<String> before = all.stream().map(Code::getCode).toList();
        Map<String, Code> byCode = new HashMap<>();
        all.forEach(c -> byCode.put(c.getCode(), c));
        for (int i = 0; i < order.size(); i++) {
            Code c = byCode.get(order.get(i));
            c.setSortOrder((i + 1) * 10);
            c.touch();
        }
        codes.saveAllAndFlush(all);
        audit.record(adminId, "CODE_REORDER", AuditService.CODE, group, Map.of("codes", before), Map.of("codes", order));
        catalog.reloadAfterCommit();
    }

    /** 쓰는 아이템·프로필이 없을 때만 지운다. CATEGORY는 하위 SUB_TYPE이 있어도 못 지운다 */
    @Transactional
    public void delete(long adminId, String group, String code) {
        requireGroup(group);
        Code entity = codes.findByGroupCodeAndCode(group, code).orElseThrow(ApiException::notFound);
        long items = queries.countItemsUsing(group, code);
        long profiles = queries.countProfilesUsing(group, code);
        long children = CodeGroups.CATEGORY.equals(group) ? codes.countByGroupCodeAndParentCode(CodeGroups.SUB_TYPE, code) : 0;
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
            throw new ApiException(ErrorCode.CODE_IN_USE,
                    String.join(", ", reasons) + "에서 쓰고 있어 삭제할 수 없어요. 대신 비활성화해 주세요.");
        }
        AdminCodeDto before = dto(entity);
        if (CodeGroups.COLOR.equals(group)) {
            syncPairs(code, CodeAttrs.pairsOf(entity.getAttrs()), List.of());
        }
        codes.delete(entity);
        codes.flush();
        audit.record(adminId, "CODE_DELETE", AuditService.CODE, target(group, code), before, null);
        catalog.reloadAfterCommit();
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
        for (Code other : codes.findByGroupCodeAndCodeIn(CodeGroups.COLOR, touched)) {
            TreeSet<String> pairs = new TreeSet<>(CodeAttrs.pairsOf(other.getAttrs()));
            boolean changed = added.contains(other.getCode()) ? pairs.add(color) : pairs.remove(color);
            if (changed) {
                Map<String, Object> attrs = new LinkedHashMap<>(other.getAttrs());
                attrs.put("pairs", List.copyOf(pairs));
                other.setAttrs(attrs);
                codes.save(other);
            }
        }
        codes.flush();
    }

    private CodeSnapshot requireGroup(String group) {
        CodeSnapshot s = catalog.current();
        if (!s.hasGroup(group)) {
            throw ApiException.notFound();
        }
        return s;
    }

    private static String label(String raw, List<FieldError> errors) {
        String label = raw == null ? null : raw.strip();
        if (label == null || label.isEmpty() || label.length() > 50) {
            errors.add(new FieldError("label", "1~50자로 입력해 주세요."));
        }
        return label;
    }

    /** SUB_TYPE은 parent(CATEGORY 코드) 필수, 다른 그룹은 parent를 둘 수 없다 */
    private static String parent(String group, String raw, CodeSnapshot snapshot, List<FieldError> errors) {
        String parent = raw == null || raw.isBlank() ? null : raw.strip();
        if (CodeGroups.SUB_TYPE.equals(group)) {
            if (parent == null) {
                errors.add(new FieldError("parent", "세부 종류는 상위 종류(CATEGORY 코드)가 필요해요."));
            } else if (!snapshot.known(CodeGroups.CATEGORY, parent)) {
                errors.add(new FieldError("parent", "없는 종류 코드예요."));
            }
        } else if (parent != null) {
            errors.add(new FieldError("parent", "세부 종류(SUB_TYPE)만 상위 코드를 가질 수 있어요."));
        }
        return parent;
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
    }

    private AdminCodeDto dto(Code c) {
        return new AdminCodeDto(c.getCode(), c.getLabel(), c.getParentCode(), c.getSortOrder(), c.isActive(), c.getAttrs(),
                queries.countItemsUsing(c.getGroupCode(), c.getCode()));
    }

    static String target(String group, String code) {
        return group + ":" + code;
    }
}
