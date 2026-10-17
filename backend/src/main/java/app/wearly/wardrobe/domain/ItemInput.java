package app.wearly.wardrobe.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * 아이템 등록·수정 값 (05 문서 6장 ItemInput). wornCount·lastWornAt은 선택. 종류·세부 종류·색·패턴·계절·스타일·보유 상태는 코드 문자열이고,
 * 필수·길이·범위 불변식은 ItemRules(도메인)가, 코드 테이블 검증은 ItemCodeValidator가 한다. 웹 요청의 Bean Validation은 빠른 실패용이다.
 */
public record ItemInput(
    String name,
    String category,
    String subType,
    String color,
    String pattern,
    List<String> seasons,
    List<String> styles,
    Integer formality,
    String status,
    String brand,
    Integer wornCount,
    LocalDate lastWornAt) {}
