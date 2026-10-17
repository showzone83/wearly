package app.wearly.code;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** GET /api/v1/codes 응답 (doc/06_코드_관리.md 4장). 관리자 응답은 description·usageCount를 더한다. */
public final class CodeDtos {

    private CodeDtos() {
    }

    public record CodesResponse(String version, List<GroupDto> groups) {
    }

    public record GroupDto(String group, String name, @JsonInclude(JsonInclude.Include.NON_NULL) String description,
            List<CodeDto> codes) {
    }

    /** parent는 null도 그대로 내보내고, usageCount는 관리자 응답에만 넣는다 */
    public record CodeDto(String code, String label, String parent, int sortOrder, boolean active,
            Map<String, Object> attrs, @JsonInclude(JsonInclude.Include.NON_NULL) Long usageCount) {
    }

    public static CodesResponse publicView(CodeSnapshot s) {
        return new CodesResponse(s.version(), s.groups().stream()
                .map(g -> new GroupDto(g.group(), g.name(), null, g.codes().stream()
                        .map(c -> toDto(c, null))
                        .toList()))
                .toList());
    }

    public static CodeDto toDto(CodeSnapshot.Entry c, Long usageCount) {
        return new CodeDto(c.code(), c.label(), c.parent(), c.sortOrder(), c.active(), c.attrs(), usageCount);
    }
}
