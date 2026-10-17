package app.wearly.wardrobe.adapter.out.persistence.mybatis.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** MyBatis 목록 조회 결과 한 행 */
public class ItemRowDto {

  private Long id;
  private String name;
  private String category;
  private String subType;
  private String color;
  private String pattern;
  private List<String> seasons;
  private List<String> styles;
  private short formality;
  private String status;
  private String brand;
  private int wornCount;
  private LocalDate lastWornAt;
  private Instant createdAt;
  private Instant retiredAt;
  private String retireReason;
  private String retireMemo;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getSubType() {
    return subType;
  }

  public void setSubType(String subType) {
    this.subType = subType;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public String getPattern() {
    return pattern;
  }

  public void setPattern(String pattern) {
    this.pattern = pattern;
  }

  public List<String> getSeasons() {
    return seasons;
  }

  public void setSeasons(List<String> seasons) {
    this.seasons = seasons;
  }

  public List<String> getStyles() {
    return styles;
  }

  public void setStyles(List<String> styles) {
    this.styles = styles;
  }

  public short getFormality() {
    return formality;
  }

  public void setFormality(short formality) {
    this.formality = formality;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String brand) {
    this.brand = brand;
  }

  public int getWornCount() {
    return wornCount;
  }

  public void setWornCount(int wornCount) {
    this.wornCount = wornCount;
  }

  public LocalDate getLastWornAt() {
    return lastWornAt;
  }

  public void setLastWornAt(LocalDate lastWornAt) {
    this.lastWornAt = lastWornAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getRetiredAt() {
    return retiredAt;
  }

  public void setRetiredAt(Instant retiredAt) {
    this.retiredAt = retiredAt;
  }

  public String getRetireReason() {
    return retireReason;
  }

  public void setRetireReason(String retireReason) {
    this.retireReason = retireReason;
  }

  public String getRetireMemo() {
    return retireMemo;
  }

  public void setRetireMemo(String retireMemo) {
    this.retireMemo = retireMemo;
  }
}
