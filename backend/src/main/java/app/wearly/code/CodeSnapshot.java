package app.wearly.code;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 어느 한 시점의 코드 테이블 전체 (읽기 전용). CodeCatalog가 만들고 통째로 바꾼다.
 * - 저장된 값의 존재 여부는 비활성 코드까지 포함해 본다.
 * - 새로 고를 수 있는지(selectable)는 활성 코드만, SUB_TYPE은 상위 CATEGORY도 활성이어야 한다.
 */
public final class CodeSnapshot {

    public record Entry(String group, String code, String label, String parent, int sortOrder, boolean active,
            Map<String, Object> attrs) {
    }

    public record Group(String group, String name, String description, int sortOrder, List<Entry> codes) {
    }

    private final String version;
    private final List<Group> groups;
    private final Map<String, Group> byGroup;
    private final Map<String, Map<String, Entry>> index;

    public CodeSnapshot(String version, List<Group> groups) {
        this.version = version;
        this.groups = List.copyOf(groups);
        Map<String, Group> byGroup = new LinkedHashMap<>();
        Map<String, Map<String, Entry>> index = new LinkedHashMap<>();
        for (Group g : groups) {
            byGroup.put(g.group(), g);
            Map<String, Entry> codes = new LinkedHashMap<>();
            g.codes().forEach(e -> codes.put(e.code(), e));
            index.put(g.group(), Collections.unmodifiableMap(codes));
        }
        this.byGroup = Collections.unmodifiableMap(byGroup);
        this.index = Collections.unmodifiableMap(index);
    }

    public String version() {
        return version;
    }

    public List<Group> groups() {
        return groups;
    }

    public Optional<Group> group(String group) {
        return Optional.ofNullable(byGroup.get(group));
    }

    public Optional<Entry> find(String group, String code) {
        if (code == null) {
            return Optional.empty();
        }
        Map<String, Entry> codes = index.get(group);
        return codes == null ? Optional.empty() : Optional.ofNullable(codes.get(code));
    }

    /** 그룹에 있는 코드인지 (비활성 포함) */
    public boolean exists(String group, String code) {
        return find(group, code).isPresent();
    }

    /** 새로 고를 수 있는 코드인지: 활성이고, SUB_TYPE이면 상위 CATEGORY도 활성 */
    public boolean selectable(String group, String code) {
        return find(group, code).map(this::selectable).orElse(false);
    }

    private boolean selectable(Entry e) {
        if (!e.active()) {
            return false;
        }
        if (CodeGroups.SUB_TYPE.equals(e.group())) {
            return find(CodeGroups.CATEGORY, e.parent()).map(Entry::active).orElse(false);
        }
        return true;
    }

    /** SUB_TYPE의 상위 CATEGORY가 category인지 */
    public boolean belongsTo(String subType, String category) {
        return find(CodeGroups.SUB_TYPE, subType).map(e -> Objects.equals(e.parent(), category)).orElse(false);
    }

    /** 그룹의 코드 전부 (sort_order 순, 비활성 포함) */
    public List<Entry> codes(String group) {
        Group g = byGroup.get(group);
        return g == null ? List.of() : g.codes();
    }

    /** 새로 고를 수 있는 코드 (sort_order 순) */
    public List<Entry> selectableCodes(String group) {
        return codes(group).stream().filter(this::selectable).toList();
    }

    /** 표시 이름. 모르는 코드면 코드 문자열 그대로 */
    public String label(String group, String code) {
        return find(group, code).map(Entry::label).orElse(code);
    }
}
