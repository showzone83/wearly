package app.wearly.wardrobe.adapter.in.web.request;

import app.wearly.wardrobe.application.port.in.dto.CreateItemDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.validator.constraints.Range;

/**
 * 05 문서 6장 ItemInput (ItemDto에서 id·createdAt을 뺀 등록 입력). wornCount·lastWornAt·purchasedAt·imageId(18
 * 문서)는 선택. 속성 값은 코드 문자열 (06 문서)이고 코드 테이블 검증은 유스케이스가 한다. Bean Validation은 빠른 실패용이다.
 *
 * <p>색 (이슈 #4): colors(1~3개, 첫 번째가 주 색상, 중복 없음) 또는 예전 color(하나). 둘 다 오면 colors를 쓰고, 둘 다 없으면 colors
 * 필수 오류. 개수·중복·필수는 도메인 규칙(ItemRules)이 본다. purchasedAt(YYYY-MM-DD, 1990-01-01~오늘)을 비우면 등록일로 저장한다.
 */
public record ItemRequest(
    @NotBlank @Size(max = 40) String name,
    @NotNull String category,
    @NotNull String subType,
    String color,
    List<String> colors,
    @NotNull String pattern,
    @NotNull List<@NotNull String> seasons,
    @NotNull List<@NotNull String> styles,
    @NotNull @Range(min = 1, max = 3) Integer formality,
    @NotNull String status,
    @Size(max = 40) String brand,
    @Min(0) Integer wornCount,
    LocalDate lastWornAt,
    LocalDate purchasedAt,
    // 18 문서: 붙일 사진 (POST /images/{imageId}/complete를 마친 내 이미지). PATCH에서 null이면 사진 빼기
    @Pattern(regexp = UUID_PATTERN) String imageId) {

  static final String UUID_PATTERN =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

  public CreateItemDto toDto() {
    return new CreateItemDto(
        name,
        category,
        subType,
        color,
        colors,
        pattern,
        seasons,
        styles,
        formality,
        status,
        brand,
        wornCount,
        lastWornAt,
        purchasedAt,
        imageId);
  }
}
