package app.wearly.admin;

import app.wearly.admin.AdminDtos.CodeCreateRequest;
import app.wearly.admin.AdminRows.KeyCount;
import app.wearly.code.Code;
import app.wearly.code.CodeCatalog;
import app.wearly.code.CodeDtos;
import app.wearly.code.CodeDtos.CodeDto;
import app.wearly.code.CodeDtos.CodesResponse;
import app.wearly.code.CodeDtos.GroupDto;
import app.wearly.code.CodeGroups;
import app.wearly.code.CodeRepository;
import app.wearly.code.CodeSnapshot;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse.FieldError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 코드 관리 (doc/07_관리자_사이트.md 4장 코드). 쓰기는 JPA, 사용 수는 MyBatis.
 * 바꾸면 같은 트랜잭션에 변경 이력을 남기고, 커밋 뒤 코드 캐시를 다시 읽는다(/codes의 version이 바뀜).
 */
@Service
public class AdminCodeService {

    private static final Pattern UPPER_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,29}$");
    private static final Pattern COLOR_CODE = Pattern.compile("^[a-z][a-z0-9_]{0,29}$");
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final CodeRepository codes;
    private final CodeCatalog catalog;
    private final AdminQueryMapper queries;
    private final AuditService audit;
    private final JsonMapper jsonMapper;

    public AdminCodeService(CodeRepository codes, CodeCatalog catalog, AdminQueryMapper queries, AuditService audit,
            JsonMapper jsonMapper) {
        this.codes = codes;
        this.catalog = catalog;
        this.queries = queries;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
    }

    /** /codes와 같은 모양 + 그룹 description, 코드별 usageCount(그 코드를 쓰는 아이템 수) */
    @Transactional(readOnly = true)
    public CodesResponse list() {
        CodeSnapshot s = catalog.snapshot();
        List<GroupDto> groups = new ArrayList<>();
        for (CodeSnapshot.Group g : s.groups()) {
            Map<String, Long> usage = toMap(queries.itemUsage(g.group()));
            groups.add(new GroupDto(g.group(), g.name(), g.description() == null ? "" : g.description(),
                    g.codes().stream().map(c -> CodeDtos.toDto(c, usage.getOrDefault(c.code(), 0L))).toList()));
        }
        return new CodesResponse(s.version(), groups);
    }

    @Transactional
    public CodeDto create(long adminId, String group, CodeCreateRequest req) {
        requireGroup(group);
        List<FieldError> errors = new ArrayList<>();
        String code = req.code() == null ? null : req.code().strip();
        Pattern pattern = CodeGroups.COLOR.equals(group) ? COLOR_CODE : UPPER_CODE;
        if (code == null || !pattern.matcher(code).matches()) {
            errors.add(new FieldError("code", CodeGroups.COLOR.equals(group)
                    ? "영문 소문자로 시작하는 소문자·숫자·밑줄 1~30자여야 해요."
                    : "영문 대문자로 시작하는 대문자·숫자·밑줄 1~30자여야 해요."));
        }
        String label = checkLabel(req.label(), errors);
        String parent = checkParent(group, req.parent(), true, errors);
        Map<String, Object> attrs = CodeAttrsValidator.normalize(group, req.attrs(), this::colorExists, code, errors);
        throwIfAny(errors);
        if (codes.existsByGroupCodeAndCode(group, code)) {
            throw new ApiException(ErrorCode.CODE_EXISTS);
        }
        Code entity = new Code(group, code);
        entity.setLabel(label);
        entity.setParentCode(parent);
        entity.setSortOrder(req.sortOrder() != null ? req.sortOrder() : codes.maxSortOrder(group) + 10);
        entity.setActive(req.active() == null || req.active());
        entity.setAttrs(attrs);
        try {
            codes.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.CODE_EXISTS); // 동시에 같은 코드를 만든 경우
        }
        audit.record(adminId, "CODE_CREATE", AuditService.CODE, targetId(group, code), null, entity.snapshot());
        if (CodeGroups.COLOR.equals(group)) {
            syncPairs(adminId, entity);
        }
        catalog.reloadAfterCommit();
        return toDto(entity);
    }

    /** { label?, parent?, sortOrder?, active?, attrs? }. 코드 문자열은 못 바꾼다. attrs는 통째로 바꾼다 */
    @Transactional
    public CodeDto patch(long adminId, String group, String code, JsonNode body) {
        requireGroup(group);
        Code entity = codes.findByGroupCodeAndCode(group, code).orElseThrow(ApiException::notFound);
        if (body == null || !body.isObject()) {
            throw ApiException.validation("body", "JSON 객체를 보내 주세요.");
        }
        Map<String, Object> before = entity.snapshot();
        List<FieldError> errors = new ArrayList<>();
        if (body.has("code") && !(body.get("code").isString() && code.equals(body.get("code").asString()))) {
            errors.add(new FieldError("code", "코드는 바꿀 수 없어요."));
        }
        if (body.has("label")) {
            JsonNode n = body.get("label");
            String label = checkLabel(n.isString() ? n.asString() : null, errors);
            if (label != null) {
                entity.setLabel(label);
            }
        }
        if (body.has("parent")) {
            JsonNode n = body.get("parent");
            if (!n.isNull() && !n.isString()) {
                errors.add(new FieldError("parent", "문자열이어야 해요."));
            } else {
                entity.setParentCode(checkParent(group, n.isNull() ? null : n.asString(), true, errors));
            }
        }
        if (body.has("sortOrder")) {
            JsonNode n = body.get("sortOrder");
            if (!n.isIntegralNumber() || !n.canConvertToInt()) {
                errors.add(new FieldError("sortOrder", "정수여야 해요."));
            } else {
                entity.setSortOrder(n.asInt());
            }
        }
        if (body.has("active")) {
            JsonNode n = body.get("active");
            if (!n.isBoolean()) {
                errors.add(new FieldError("active", "true 또는 false여야 해요."));
            } else {
                entity.setActive(n.asBoolean());
            }
        }
        if (body.has("attrs")) {
            JsonNode n = body.get("attrs");
            if (!n.isObject()) {
                errors.add(new FieldError("attrs", "객체여야 해요."));
            } else {
                Map<String, Object> raw = jsonMapper.convertValue(n, MAP);
                entity.setAttrs(CodeAttrsValidator.normalize(group, raw, this::colorExists, code, errors));
            }
        }
        throwIfAny(errors);
        codes.saveAndFlush(entity);
        Map<String, Object> after = entity.snapshot();
        if (!after.equals(before)) {
            audit.record(adminId, "CODE_UPDATE", AuditService.CODE, targetId(group, code), before, after);
            if (CodeGroups.COLOR.equals(group)) {
                syncPairs(adminId, entity);
            }
            catalog.reloadAfterCommit();
        }
        return toDto(entity);
    }

    /** codes 순서대로 sortOrder를 10, 20, …으로. 그룹의 코드를 빠짐없이 한 번씩 보내야 한다 */
    @Transactional
    public List<CodeDto> reorder(long adminId, String group, List<String> order) {
        requireGroup(group);
        List<Code> all = codes.findByGroupCodeOrderBySortOrderAscIdAsc(group);
        Set<String> existing = new HashSet<>();
        all.forEach(c -> existing.add(c.getCode()));
        if (order == null || order.size() != all.size() || !new HashSet<>(order).equals(existing)
                || new HashSet<>(order).size() != order.size()) {
            throw ApiException.validation("codes", "그룹의 코드를 빠짐없이 한 번씩 보내 주세요.");
        }
        Map<String, Code> byCode = new HashMap<>();
        all.forEach(c -> byCode.put(c.getCode(), c));
        List<String> beforeOrder = all.stream().map(Code::getCode).toList();
        for (int i = 0; i < order.size(); i++) {
            byCode.get(order.get(i)).setSortOrder((i + 1) * 10);
        }
        codes.saveAllAndFlush(all);
        audit.record(adminId, "CODE_REORDER", AuditService.CODE, group, Map.of("codes", beforeOrder),
                Map.of("codes", List.copyOf(order)));
        catalog.reloadAfterCommit();
        return order.stream().map(byCode::get).map(AdminCodeService::toDto).toList();
    }

    /** 쓰는 아이템·프로필(·하위 세부 종류)이 없을 때만 지운다. 있으면 409 CODE_IN_USE (대신 비활성화) */
    @Transactional
    public void delete(long adminId, String group, String code) {
        requireGroup(group);
        Code entity = codes.findByGroupCodeAndCode(group, code).orElseThrow(ApiException::notFound);
        long items = toMap(queries.itemUsage(group)).getOrDefault(code, 0L);
        long profiles = toMap(queries.profileUsage(group)).getOrDefault(code, 0L);
        boolean children = CodeGroups.CATEGORY.equals(group) && codes.existsByGroupCodeAndParentCode(CodeGroups.SUB_TYPE, code);
        if (items > 0 || profiles > 0 || children) {
            String why = items > 0 ? "아이템 " + items + "개가 쓰고 있어요."
                    : profiles > 0 ? "프로필 " + profiles + "개가 쓰고 있어요." : "하위 세부 종류가 있어요.";
            throw new ApiException(ErrorCode.CODE_IN_USE, ErrorCode.CODE_IN_USE.defaultMessage() + " (" + why + ")");
        }
        Map<String, Object> before = entity.snapshot();
        codes.delete(entity);
        codes.flush();
        audit.record(adminId, "CODE_DELETE", AuditService.CODE, targetId(group, code), before, null);
        if (CodeGroups.COLOR.equals(group)) {
            removeFromPairs(adminId, code);
        }
        catalog.reloadAfterCommit();
    }

    /** 색의 pairs를 양방향으로 맞춘다: 내 pairs에 있으면 상대에도 나를 넣고, 없으면 상대에서 나를 뺀다 */
    private void syncPairs(long adminId, Code self) {
        Set<String> mine = new HashSet<>(pairsOf(self));
        for (Code other : codes.findByGroupCodeOrderBySortOrderAscIdAsc(CodeGroups.COLOR)) {
            if (other.getCode().equals(self.getCode())) {
                continue;
            }
            List<String> theirs = pairsOf(other);
            boolean should = mine.contains(other.getCode());
            if (should != theirs.contains(self.getCode())) {
                TreeSet<String> next = new TreeSet<>(theirs);
                if (should) {
                    next.add(self.getCode());
                } else {
                    next.remove(self.getCode());
                }
                updatePairs(adminId, other, new ArrayList<>(next));
            }
        }
    }

    private void removeFromPairs(long adminId, String deleted) {
        for (Code other : codes.findByGroupCodeOrderBySortOrderAscIdAsc(CodeGroups.COLOR)) {
            List<String> theirs = pairsOf(other);
            if (theirs.contains(deleted)) {
                List<String> next = new ArrayList<>(theirs);
                next.remove(deleted);
                updatePairs(adminId, other, next);
            }
        }
    }

    private void updatePairs(long adminId, Code other, List<String> pairs) {
        Map<String, Object> before = other.snapshot();
        Map<String, Object> attrs = new java.util.LinkedHashMap<>(other.getAttrs());
        attrs.put("pairs", pairs);
        other.setAttrs(attrs);
        codes.saveAndFlush(other);
        audit.record(adminId, "CODE_PAIRS_SYNC", AuditService.CODE, targetId(CodeGroups.COLOR, other.getCode()), before,
                other.snapshot());
    }

    private static List<String> pairsOf(Code c) {
        Object raw = c.getAttrs() == null ? null : c.getAttrs().get("pairs");
        if (raw instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private boolean colorExists(String code) {
        return codes.existsByGroupCodeAndCode(CodeGroups.COLOR, code);
    }

    private void requireGroup(String group) {
        if (catalog.snapshot().group(group).isEmpty()) {
            throw ApiException.notFound();
        }
    }

    private static String checkLabel(String label, List<FieldError> errors) {
        if (label == null || label.isBlank() || label.strip().length() > 50) {
            errors.add(new FieldError("label", "1~50자로 입력해 주세요."));
            return null;
        }
        return label.strip();
    }

    /** SUB_TYPE은 있는 CATEGORY 코드가 필수, 다른 그룹은 parent를 쓰지 않는다 */
    private String checkParent(String group, String parent, boolean required, List<FieldError> errors) {
        String p = parent == null || parent.isBlank() ? null : parent.strip();
        if (CodeGroups.SUB_TYPE.equals(group)) {
            if (p == null) {
                if (required) {
                    errors.add(new FieldError("parent", "세부 종류는 상위 종류(CATEGORY 코드)가 필요해요."));
                }
                return null;
            }
            if (!codes.existsByGroupCodeAndCode(CodeGroups.CATEGORY, p)) {
                errors.add(new FieldError("parent", "있는 종류(CATEGORY) 코드여야 해요."));
            }
            return p;
        }
        if (p != null) {
            errors.add(new FieldError("parent", "세부 종류(SUB_TYPE)만 상위 코드를 가져요."));
        }
        return null;
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
    }

    private static Map<String, Long> toMap(List<KeyCount> rows) {
        Map<String, Long> m = new HashMap<>();
        rows.forEach(r -> m.put(r.key, r.count));
        return m;
    }

    static String targetId(String group, String code) {
        return group + ":" + code;
    }

    private static CodeDto toDto(Code c) {
        return new CodeDto(c.getCode(), c.getLabel(), c.getParentCode(), c.getSortOrder(), c.isActive(), c.getAttrs(),
                null);
    }
}
