package app.wearly.admin;

import java.util.List;
import java.util.Map;

/** GET /admin/codes 응답·POST 요청 (07 문서 4장 "코드") */
public final class AdminCodeDtos {

    private AdminCodeDtos() {
    }

    public record AdminCodesResponse(String version, List<AdminGroupDto> groups) {
    }

    public record AdminGroupDto(String group, String name, String description, List<AdminCodeDto> codes) {
    }

    /** usageCount = 이 코드를 쓰는 아이템 수 */
    public record AdminCodeDto(String code, String label, String parent, int sortOrder, boolean active,
            Map<String, Object> attrs, long usageCount) {
    }

    public record CreateCodeRequest(String code, String label, String parent, Integer sortOrder, Boolean active,
            Map<String, Object> attrs) {
    }

    public record OrderRequest(List<String> codes) {
    }
}
