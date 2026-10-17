package app.wearly.code;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 어느 한 시점의 코드 테이블 전체 (불변). 검증·AI 스키마·통계가 같은 스냅샷을 본다.
 * - known: 그룹에 있는 코드 (비활성 포함)
 * - selectable: 새로 고를 수 있는 코드. 활성이어야 하고, SUB_TYPE은 상위 CATEGORY도 활성이어야 한다
 */
public final class CodeSnapshot {

    private final String version;
    private final List<CodeGroupEntry> groups;
    private final Map<String, Map<String, CodeEntry>> index;

    public CodeSnapshot(String version, List<CodeGroupEntry> groups) {
        this.version = version;
        this.groups = List.copyOf(groups);
        Map<String, Map<String, CodeEntry>> idx = new LinkedHashMap<>();
        for (CodeGroupEntry g : groups) {
            Map<String, CodeEntry> codes = new LinkedHashMap<>();
            g.codes().forEach(c -> codes.put(c.code(), c));
            idx.put(g.group(), Collections.unmodifiableMap(codes));
        }
        this.index = Collections.unmodifiableMap(idx);
    }

    public String version() {
        return version;
    }

    public List<CodeGroupEntry> groups() {
        return groups;
    }

    public boolean hasGroup(String group) {
        return index.containsKey(group);
    }

    public Optional<CodeGroupEntry> group(String group) {
        return groups.stream().filter(g -> g.group().equals(group)).findFirst();
    }

    /** 그룹의 코드 전부 (sort_order 순서, 비활성 포함) */
    public List<CodeEntry> codes(String group) {
        return group(group).map(CodeGroupEntry::codes).orElse(List.of());
    }

    public Optional<CodeEntry> find(String group, String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(index.getOrDefault(group, Map.of()).get(code));
    }

    public boolean known(String group, String code) {
        return find(group, code).isPresent();
    }

    public boolean selectable(String group, String code) {
        return find(group, code).map(this::selectable).orElse(false);
    }

    private boolean selectable(CodeEntry entry) {
        if (!entry.active()) {
            return false;
        }
        if (entry.parent() != null) {
            return find(CodeGroups.CATEGORY, entry.parent()).map(CodeEntry::active).orElse(false);
        }
        return true;
    }

    /** 새로 고를 수 있는 코드 (sort_order 순서). SUB_TYPE은 상위 종류가 비활성이면 빠진다 */
    public List<CodeEntry> selectableCodes(String group) {
        return codes(group).stream().filter(this::selectable).toList();
    }

    /** subType이 category의 하위인지 */
    public boolean belongsTo(String subType, String category) {
        return find(CodeGroups.SUB_TYPE, subType).map(s -> s.parent() != null && s.parent().equals(category)).orElse(false);
    }

    public String label(String group, String code) {
        return find(group, code).map(CodeEntry::label).orElse(code);
    }
}
