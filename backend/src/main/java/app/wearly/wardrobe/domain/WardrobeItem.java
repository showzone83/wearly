package app.wearly.wardrobe.domain;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.Zones;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 옷장 아이템 (순수 도메인 모델). 사용 중지(retirement)는 보유 상태(status)와 별개다 (13 문서). 색은 주 색상(color) + 보조 색 0~2개
 * (subColors), 구입일을 모르면 등록일(서울 날짜)을 넣고 추정 표시를 켠다 (이슈 #4)
 */
public class WardrobeItem {

  private final Long id;
  private final long userId;
  private String name;
  private String category;
  private String subType;
  private String color;
  private List<String> subColors;
  private String pattern;
  private List<String> seasons;
  private List<String> styles;
  private int formality;
  private String status;
  private String brand;
  private int wornCount;
  private LocalDate lastWornAt;
  private LocalDate purchasedAt;
  private boolean purchasedAtEstimated;
  private final Instant createdAt;
  private Retirement retirement;

  public WardrobeItem(
      Long id,
      long userId,
      String name,
      String category,
      String subType,
      String color,
      List<String> subColors,
      String pattern,
      List<String> seasons,
      List<String> styles,
      int formality,
      String status,
      String brand,
      int wornCount,
      LocalDate lastWornAt,
      LocalDate purchasedAt,
      boolean purchasedAtEstimated,
      Instant createdAt,
      Retirement retirement) {
    this.id = id;
    this.userId = userId;
    this.name = name;
    this.category = category;
    this.subType = subType;
    this.color = color;
    this.subColors = subColors;
    this.pattern = pattern;
    this.seasons = seasons;
    this.styles = styles;
    this.formality = formality;
    this.status = status;
    this.brand = brand;
    this.wornCount = wornCount;
    this.lastWornAt = lastWornAt;
    this.purchasedAt = purchasedAt;
    this.purchasedAtEstimated = purchasedAtEstimated;
    this.createdAt = createdAt;
    this.retirement = retirement;
  }

  /**
   * 아직 저장하지 않은 새 아이템
   *
   * @param today 오늘 (서울). 구입일 상한이고, 구입일을 모르면 이 날(등록일)을 넣는다
   */
  public static WardrobeItem create(long userId, ItemInput in, LocalDate today) {
    WardrobeItem item =
        new WardrobeItem(
            null, userId, null, null, null, null, List.of(), null, List.of(), List.of(), 0, null,
            null, 0, null, null, false, null, null);
    item.apply(in, today);
    return item;
  }

  /**
   * 모든 값을 덮어쓴다 (등록과 PATCH 병합 결과 저장에 같이 쓴다). 불변식(ItemRules)을 어기면 DomainValidationException. 이름·브랜드는
   * 앞뒤 공백을 지우고 빈 브랜드는 null, 계절·스타일은 중복을 없앤다(순서 유지), 착용 수가 없으면 0. 첫 색이 주 색상이고 나머지는 보조 색이다. 구입일이
   * 없으면(모름) 등록일 — 저장된 아이템은 created_at의 서울 날짜, 새 아이템은 today — 을 넣고 추정 표시를 켠다.
   *
   * @param today 오늘 (서울)
   */
  public void apply(ItemInput in, LocalDate today) {
    DomainValidationException.throwIfAny(ItemRules.validate(in, "", today));
    this.name = in.name().strip();
    this.category = in.category();
    this.subType = in.subType();
    this.color = in.colors().get(0);
    this.subColors = List.copyOf(in.colors().subList(1, in.colors().size()));
    this.pattern = in.pattern();
    this.seasons = distinct(in.seasons());
    this.styles = distinct(in.styles());
    this.formality = in.formality();
    this.status = in.status();
    this.brand = in.brand() == null || in.brand().isBlank() ? null : in.brand().strip();
    this.wornCount = in.wornCount() == null ? 0 : in.wornCount();
    this.lastWornAt = in.lastWornAt();
    if (in.purchasedAt() == null) {
      this.purchasedAt = registeredOn(today);
      this.purchasedAtEstimated = true;
    } else {
      this.purchasedAt = in.purchasedAt();
      this.purchasedAtEstimated = false;
    }
  }

  /** 등록일 (서울 날짜). 아직 저장 전이면 today */
  private LocalDate registeredOn(LocalDate today) {
    return createdAt == null ? today : LocalDate.ofInstant(createdAt, Zones.SEOUL);
  }

  /**
   * 사용하지 않는 옷으로 옮긴다. 이미 사용 중지면 이유·메모만 바꾸고 시각은 그대로 둔다. 메모는 앞뒤 공백을 지우고 빈 값은 null. 이유가 활성 코드인지는 유스케이스가
   * RetireRules로 먼저 본다.
   */
  public void retire(String reason, String memo, Instant now) {
    DomainValidationException.throwIfAny(RetireRules.validate(reason, memo));
    Instant at = retirement == null ? now : retirement.at();
    this.retirement = new Retirement(at, reason, RetireRules.memo(memo));
  }

  /** 다시 사용한다 (사용 중이면 그대로). 보유 상태는 바꾸지 않았으므로 원래 상태로 돌아온다 */
  public void restore() {
    this.retirement = null;
  }

  public boolean isRetired() {
    return retirement != null;
  }

  /** 사용 중이면 null */
  public Retirement retirement() {
    return retirement;
  }

  /** PATCH 병합의 출발점: 저장된 값을 입력 모양으로. 추정 구입일은 모름(null)으로 돌린다 */
  public ItemInput toInput() {
    return new ItemInput(
        name,
        category,
        subType,
        colors(),
        false,
        pattern,
        list(seasons),
        list(styles),
        formality,
        status,
        brand,
        wornCount,
        lastWornAt,
        purchasedAtEstimated ? null : purchasedAt);
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

  /** 주 색상 */
  public String color() {
    return color;
  }

  /** 보조 색 (0~2개, 순서 유지) */
  public List<String> subColors() {
    return list(subColors);
  }

  /** 주 색상 + 보조 색 (1~3개) */
  public List<String> colors() {
    List<String> out = new ArrayList<>();
    if (color != null) {
      out.add(color);
    }
    out.addAll(list(subColors));
    return List.copyOf(out);
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

  public LocalDate purchasedAt() {
    return purchasedAt;
  }

  /** 구입일을 몰라 등록일을 넣었는지 */
  public boolean purchasedAtEstimated() {
    return purchasedAtEstimated;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
