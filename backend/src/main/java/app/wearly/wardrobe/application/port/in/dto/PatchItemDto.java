package app.wearly.wardrobe.application.port.in.dto;

import app.wearly.common.application.Patch;
import app.wearly.common.domain.FieldError;
import java.time.LocalDate;
import java.util.List;

/**
 * 아이템 부분 수정 (05 문서 6장, Partial&lt;ItemInput&gt;). 보낸 필드만 바꾸고 null을 보내면 그 값을 비운다. inputErrors는 입력
 * 어댑터가 찾은 형식 오류(JSON 객체가 아님, 타입이 틀림, 필수·길이·범위)로, 유스케이스가 아이템을 찾은 뒤(없으면 404 먼저) 그대로 400으로 돌려준다.
 */
public record PatchItemDto(
    Patch<String> name,
    Patch<String> category,
    Patch<String> subType,
    Patch<String> color,
    Patch<String> pattern,
    Patch<List<String>> seasons,
    Patch<List<String>> styles,
    Patch<Integer> formality,
    Patch<String> status,
    Patch<String> brand,
    Patch<Integer> wornCount,
    Patch<LocalDate> lastWornAt,
    // 18 문서: 사진 바꾸기(새 imageId)·빼기(null)
    Patch<String> imageId,
    List<FieldError> inputErrors) {}
