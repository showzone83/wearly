package app.wearly.code.application.port.in.dto;

import java.util.Map;

/** 코드 추가 (07 문서 4장). sortOrder가 없으면 그룹 맨 뒤(+10), active가 없으면 true */
public record CreateCodeDto(
    String code,
    String label,
    String parent,
    Integer sortOrder,
    Boolean active,
    Map<String, Object> attrs) {}
