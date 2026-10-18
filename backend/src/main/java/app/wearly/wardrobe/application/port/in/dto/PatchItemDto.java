package app.wearly.wardrobe.application.port.in.dto;

import app.wearly.common.application.Patch;
import app.wearly.common.domain.FieldError;
import java.time.LocalDate;
import java.util.List;

/**
 * 아이템 부분 수정 (05 문서 6장, Partial&lt;ItemInput&gt;). 보낸 필드만 바꾸고 null을 보내면 그 값을 비운다. inputErrors는 입력
 * 어댑터가 찾은 형식 오류(JSON 객체가 아님, 타입이 틀림, 필수·길이·범위)로, 유스케이스가 아이템을 찾은 뒤(없으면 404 먼저) 그대로 400으로 돌려준다.
 *
 * <p>색 (이슈 #4): colors를 보내면 색 전체를 바꾼다. colors 없이 예전 color만 보내면 주 색상만 바꾸고 보조 색은 둔다(같은 색이 보조 색에 있으면
 * 뺀다). 둘 다 오면 colors. purchasedAt에 null을 보내면 모름 → 등록일·추정 표시로 돌아간다.
 */
public record PatchItemDto(
    Patch<String> name,
    Patch<String> category,
    Patch<String> subType,
    Patch<String> color,
    Patch<List<String>> colors,
    Patch<String> pattern,
    Patch<List<String>> seasons,
    Patch<List<String>> styles,
    Patch<Integer> formality,
    Patch<String> status,
    Patch<String> brand,
    Patch<Integer> wornCount,
    Patch<LocalDate> lastWornAt,
    Patch<LocalDate> purchasedAt,
    // 18 문서: 사진 바꾸기(새 imageId)·빼기(null)
    Patch<String> imageId,
    List<FieldError> inputErrors) {}
