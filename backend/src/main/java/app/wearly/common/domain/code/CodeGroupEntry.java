package app.wearly.common.domain.code;

import java.util.List;

/** 캐시된 코드 그룹. codes는 sort_order 순서 */
public record CodeGroupEntry(String group, String name, String description, int sortOrder, List<CodeEntry> codes) {
}
