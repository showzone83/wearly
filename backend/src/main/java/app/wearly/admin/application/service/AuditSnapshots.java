package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.dto.AdminCodesDto.AdminCodeDto;
import app.wearly.admin.application.port.in.dto.AdminProviderDto;
import app.wearly.weather.application.port.in.dto.WeatherProviderDto;
import app.wearly.weather.application.port.in.dto.WeatherSettingDto;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 변경 이력(tb_admin_audit_log)의 before·after 모양 (07 문서 4장: 관리자 API 응답과 같은 필드). 관리자 화면이 이 JSON을 그대로 보여
 * 주므로 응답 모양을 바꿔도 이력 모양은 여기서 따로 지킨다. 시각(Instant)의 저장 형식은 영속성 어댑터가 정한다.
 */
final class AuditSnapshots {

  private AuditSnapshots() {}

  static Map<String, Object> provider(AdminProviderDto p) {
    Map<String, Object> today = new LinkedHashMap<>();
    today.put("success", p.today().success());
    today.put("failed", p.today().failed());
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("providerId", p.providerId());
    m.put("type", p.type());
    m.put("displayName", p.displayName());
    m.put("model", p.model());
    m.put("apiKeyEnv", p.apiKeyEnv());
    m.put("keyConfigured", p.keyConfigured());
    m.put("enabled", p.enabled());
    m.put("priority", p.priority());
    m.put("options", p.options());
    m.put("usable", p.usable());
    m.put("today", today);
    m.put("keySource", p.keySource());
    m.put("keyLast4", p.keyLast4());
    m.put("keyUpdatedAt", p.keyUpdatedAt());
    m.put("keyUpdatedBy", p.keyUpdatedBy());
    m.put("secretStoreEnabled", p.secretStoreEnabled());
    return m;
  }

  /** 키 변경 이력에는 출처와 끝 4자만 남긴다 */
  static Map<String, Object> providerKey(String keySource, String keyLast4) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("keySource", keySource);
    m.put("keyLast4", keyLast4);
    return m;
  }

  /** 날씨 스케줄 설정 (17 문서 6장, 관리자 API 응답의 setting과 같은 필드) */
  static Map<String, Object> weatherSetting(WeatherSettingDto s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("enabled", s.enabled());
    m.put("intervalMinutes", s.intervalMinutes());
    m.put("forecastDays", s.forecastDays());
    m.put("failureThreshold", s.failureThreshold());
    m.put("cooldownMinutes", s.cooldownMinutes());
    return m;
  }

  /** 날씨 공급자: 관리자가 바꾸는 설정과 상태 (사용량·시각은 남기지 않는다) */
  static Map<String, Object> weatherProvider(WeatherProviderDto p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", p.code());
    m.put("enabled", p.enabled());
    m.put("priority", p.priority());
    m.put("monthlyQuota", p.monthlyQuota());
    m.put("maxForecastDays", p.maxForecastDays());
    m.put("status", p.status());
    m.put("consecutiveFailures", p.consecutiveFailures());
    return m;
  }

  static Map<String, Object> code(AdminCodeDto c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", c.code());
    m.put("label", c.label());
    m.put("parent", c.parent());
    m.put("sortOrder", c.sortOrder());
    m.put("active", c.active());
    m.put("attrs", c.attrs());
    m.put("usageCount", c.usageCount());
    return m;
  }
}
