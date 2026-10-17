package app.wearly.ai.application.port.in.dto;

import java.util.List;

/**
 * 지원 공급자 종류와 options 설명 (GET /admin/ai/types).
 *
 * @param models 내장 모델 목록 (wearly.ai.model-catalog, 추천 먼저). 아직 저장하지 않은 공급자의 모델 드롭다운에 쓴다
 */
public record ProviderTypeDto(
    String type, String description, List<Option> options, List<Model> models) {

  /** 내장 모델 한 줄 */
  public record Model(String id, String displayName, boolean recommended) {}

  /** min·max는 숫자 칸, values는 enum 칸에만 있다 (없으면 null) */
  public record Option(
      String name,
      String kind,
      String description,
      Object defaultValue,
      Number min,
      Number max,
      List<String> values) {}
}
