package app.wearly.ai.application.port.in.dto;

import java.time.Instant;
import java.util.List;

/**
 * 공급자 모델 목록 (GET /admin/ai/providers/{providerId}/models).
 *
 * @param source LIVE(제공사에서 받은 목록) 또는 CATALOG(내장 목록)
 * @param fetchedAt LIVE면 제공사에서 받은 시각(캐시에서 줬으면 처음 받은 시각), CATALOG면 응답을 만든 시각
 * @param models 추천 먼저, createdAt 최신순, ID 순
 * @param error CATALOG일 때 그 이유 (LIVE면 null)
 */
public record AiModelListDto(
    String source, Instant fetchedAt, List<Model> models, ErrorInfo error) {

  public static final String LIVE = "LIVE";
  public static final String CATALOG = "CATALOG";

  /** 키가 없어 제공사 목록을 부르지 않았다 */
  public static final String NO_API_KEY = "NO_API_KEY";

  /** 제공사 목록 조회가 실패했다 (거부·오류·시간 초과·응답 형식) */
  public static final String MODEL_LIST_FAILED = "MODEL_LIST_FAILED";

  public record Model(String id, String displayName, Instant createdAt, boolean recommended) {}

  /** message는 한국어 안내. 키 값·제공사 응답 원문은 넣지 않는다 */
  public record ErrorInfo(String code, String message) {}
}
