package app.wearly.admin.application.port.in.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 관리자 AI 공급자 한 행 (유스케이스 출력, 07 문서 4·6장): ai 컨텍스트의 공급자 정보 + 오늘 호출 수. 키 값은 어디에도 없다. keyLast4·
 * keyUpdatedAt·keyUpdatedBy는 DB 키일 때만.
 */
public record AdminProviderDto(
    String providerId,
    String type,
    String displayName,
    String model,
    String apiKeyEnv,
    boolean keyConfigured,
    boolean enabled,
    int priority,
    Map<String, Object> options,
    boolean usable,
    TodayDto today,
    String keySource,
    String keyLast4,
    Instant keyUpdatedAt,
    String keyUpdatedBy,
    boolean secretStoreEnabled) {

  /** 오늘(한국 시간) 공급자 호출 수 (사진 분석 + 시험 분석 + 코디 추천) */
  public record TodayDto(long success, long failed) {

    public static final TodayDto NONE = new TodayDto(0, 0);
  }
}
