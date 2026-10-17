package app.wearly.code.application.port.in.dto;

import app.wearly.common.application.Patch;
import java.util.Map;

/**
 * 코드 부분 수정 (07 문서 4장). 보낸 필드만 바꾼다. code는 바꿀 수 없고(보내면 같은 값이어야 함), attrs를 보내면 attrs 전체를 바꾼다. parent의
 * null은 상위 코드를 비운다는 뜻이다.
 */
public record PatchCodeDto(
    Patch<String> code,
    Patch<String> label,
    Patch<String> parent,
    Patch<Integer> sortOrder,
    Patch<Boolean> active,
    Patch<Map<String, Object>> attrs) {}
