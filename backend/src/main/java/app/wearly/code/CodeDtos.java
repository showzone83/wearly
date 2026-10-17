package app.wearly.code;

import java.util.List;
import java.util.Map;

/** GET /api/v1/codes 응답 (06 문서 4장) */
public final class CodeDtos {

    private CodeDtos() {
    }

    public record CodesResponse(String version, List<GroupDto> groups) {
    }

    public record GroupDto(String group, String name, List<CodeDto> codes) {
    }

    public record CodeDto(String code, String label, String parent, int sortOrder, boolean active, Map<String, Object> attrs) {
    }

    public static CodesResponse from(CodeSnapshot s) {
        return new CodesResponse(s.version(), s.groups().stream()
                .map(g -> new GroupDto(g.group(), g.name(), g.codes().stream().map(CodeDtos::code).toList()))
                .toList());
    }

    public static CodeDto code(CodeEntry c) {
        return new CodeDto(c.code(), c.label(), c.parent(), c.sortOrder(), c.active(), c.attrs());
    }
}
