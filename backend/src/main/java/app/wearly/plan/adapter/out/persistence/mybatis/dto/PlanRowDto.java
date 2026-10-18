package app.wearly.plan.adapter.out.persistence.mybatis.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** MyBatis 예정 목록 한 행 (concepts는 TEXT[], weather·outfit은 JSONB → Map) */
public class PlanRowDto {

  private Long id;
  private Long userId;
  private String clientId;
  private LocalDate plannedDate;
  private String timeOfDay;
  private String title;
  private String occasion;
  private List<String> concepts;
  private String memo;
  private String placeRegionCode;
  private String placeLabel;
  private Double placeLat;
  private Double placeLon;
  private Map<String, Object> weather;
  private boolean forecastReady;
  private Map<String, Object> outfit;
  private Instant createdAt;
  private Instant updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public LocalDate getPlannedDate() {
    return plannedDate;
  }

  public void setPlannedDate(LocalDate plannedDate) {
    this.plannedDate = plannedDate;
  }

  public String getTimeOfDay() {
    return timeOfDay;
  }

  public void setTimeOfDay(String timeOfDay) {
    this.timeOfDay = timeOfDay;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getOccasion() {
    return occasion;
  }

  public void setOccasion(String occasion) {
    this.occasion = occasion;
  }

  public List<String> getConcepts() {
    return concepts;
  }

  public void setConcepts(List<String> concepts) {
    this.concepts = concepts;
  }

  public String getMemo() {
    return memo;
  }

  public void setMemo(String memo) {
    this.memo = memo;
  }

  public String getPlaceRegionCode() {
    return placeRegionCode;
  }

  public void setPlaceRegionCode(String placeRegionCode) {
    this.placeRegionCode = placeRegionCode;
  }

  public String getPlaceLabel() {
    return placeLabel;
  }

  public void setPlaceLabel(String placeLabel) {
    this.placeLabel = placeLabel;
  }

  public Double getPlaceLat() {
    return placeLat;
  }

  public void setPlaceLat(Double placeLat) {
    this.placeLat = placeLat;
  }

  public Double getPlaceLon() {
    return placeLon;
  }

  public void setPlaceLon(Double placeLon) {
    this.placeLon = placeLon;
  }

  public Map<String, Object> getWeather() {
    return weather;
  }

  public void setWeather(Map<String, Object> weather) {
    this.weather = weather;
  }

  public boolean isForecastReady() {
    return forecastReady;
  }

  public void setForecastReady(boolean forecastReady) {
    this.forecastReady = forecastReady;
  }

  public Map<String, Object> getOutfit() {
    return outfit;
  }

  public void setOutfit(Map<String, Object> outfit) {
    this.outfit = outfit;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
