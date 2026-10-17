package app.wearly.common.domain.code;

import java.util.Map;

/** 캐시된 코드 한 줄 (읽기 전용) */
public record CodeEntry(
    String code,
    String label,
    String parent,
    int sortOrder,
    boolean active,
    Map<String, Object> attrs) {}
