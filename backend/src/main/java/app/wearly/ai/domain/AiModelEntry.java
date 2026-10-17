package app.wearly.ai.domain;

import java.time.Instant;

/**
 * 공급자 모델 목록의 한 줄 (관리자 화면 모델 드롭다운, 07 문서 4장).
 *
 * @param id 모델 ID (tb_ai_provider.model에 그대로 넣는 값)
 * @param displayName 화면 이름. 제공사가 주지 않으면 id
 * @param createdAt 제공사가 알려 준 출시(등록) 시각. 모르면 null
 * @param recommended 내장 목록에서 추천으로 표시한 모델
 */
public record AiModelEntry(String id, String displayName, Instant createdAt, boolean recommended) {

  public AiModelEntry {
    displayName = displayName == null || displayName.isBlank() ? id : displayName.strip();
  }

  /** 제공사 목록에서 읽은 줄 (추천 여부는 서비스가 내장 목록과 맞춰 붙인다) */
  public static AiModelEntry live(String id, String displayName, Instant createdAt) {
    return new AiModelEntry(id, displayName, createdAt, false);
  }
}
