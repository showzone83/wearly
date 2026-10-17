package app.wearly.wardrobe.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 옷장 아이템 (순수 도메인 모델) */
public class WardrobeItem {

  private final Long id;
  private final long userId;
  private String name;
  private String category;
  private String subType;
  private String color;
  private String pattern;
  private List<String> seasons;
  private List<String> styles;
  private int formality;
  private String status;
  private String brand;
  private int wornCount;
  private LocalDate lastWornAt;
  private final Instant createdAt;

  public WardrobeItem(
      Long id,
      long userId,
      String name,
      String category,
      String subType,
      String color,
      String pattern,
      List<String> seasons,
      List<String> styles,
      int formality,
      String status,
      String brand,
      int wornCount,
      LocalDate lastWornAt,
      Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.name = name;
    this.category = category;
    this.subType = subType;
    this.color = color;
    this.pattern = pattern;
    this.seasons = seasons;
    this.styles = styles;
    this.formality = formality;
    this.status = status;
    this.brand = brand;
    this.wornCount = wornCount;
    this.lastWornAt = lastWornAt;
    this.createdAt = createdAt;
  }

  /** 아직 저장하지 않은 새 아이템 */
  public static WardrobeItem create(long userId, ItemInput in) {
    WardrobeItem item =
        new WardrobeItem(
            null, userId, null, null, null, null, null, List.of(), List.of(), 0, null, null, 0,
            null, null);
    item.apply(in);
    return item;
  }

  /**
   * 모든 값을 덮어쓴다 (등록과 PATCH 병합 결과 저장에 같이 쓴다). 이름·브랜드는 앞뒤 공백을 지우고 빈 브랜드는 null, 계절·스타일은 중복을 없앤다(순서 유지),
   * 착용 수가 없으면 0.
   */
  public void apply(ItemInput in) {
    this.name = in.name().strip();
    this.category = in.category();
    this.subType = in.subType();
    this.color = in.color();
    this.pattern = in.pattern();
    this.seasons = distinct(in.seasons());
    this.styles = distinct(in.styles());
    this.formality = in.formality();
    this.status = in.status();
    this.brand = in.brand() == null || in.brand().isBlank() ? null : in.brand().strip();
    this.wornCount = in.wornCount() == null ? 0 : in.wornCount();
    this.lastWornAt = in.lastWornAt();
  }

  /** PATCH 병합의 출발점: 저장된 값을 입력 모양으로 */
  public ItemInput toInput() {
    return new ItemInput(
        name,
        category,
        subType,
        color,
        pattern,
        list(seasons),
        list(styles),
        formality,
        status,
        brand,
        wornCount,
        lastWornAt);
  }

  static List<String> distinct(List<String> values) {
    return values == null ? List.of() : values.stream().distinct().toList();
  }

  private static List<String> list(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  public Long id() {
    return id;
  }

  public long userId() {
    return userId;
  }

  public String name() {
    return name;
  }

  public String category() {
    return category;
  }

  public String subType() {
    return subType;
  }

  public String color() {
    return color;
  }

  public String pattern() {
    return pattern;
  }

  public List<String> seasons() {
    return list(seasons);
  }

  public List<String> styles() {
    return list(styles);
  }

  public int formality() {
    return formality;
  }

  public String status() {
    return status;
  }

  public String brand() {
    return brand;
  }

  public int wornCount() {
    return wornCount;
  }

  public LocalDate lastWornAt() {
    return lastWornAt;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
