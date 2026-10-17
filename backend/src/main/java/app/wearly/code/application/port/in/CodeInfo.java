package app.wearly.code.application.port.in;

import java.util.Map;

/** 관리자에게 보여 주는 코드 한 줄. usageCount = 이 코드를 쓰는 아이템 수 */
public record CodeInfo(String group, String code, String label, String parent, int sortOrder, boolean active,
        Map<String, Object> attrs, long usageCount) {
}
