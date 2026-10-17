package app.wearly.ai.application.port.in;

import app.wearly.ai.domain.AiCallRecord;
import java.util.List;

/**
 * ai 컨텍스트가 소유한 기능 이름 (tb_ai_analysis_log.feature). 구조화 JSON 생성을 쓰는 다른 컨텍스트는 자기 기능 이름을 정해 넘긴다 (L6,
 * 예: outfit의 OutfitFeatures). ai는 이름 형식만 본다 (V7 CHECK와 같은 {@link #isValidName}).
 */
public final class AiFeatures {

  public static final String ITEM_ANALYZE = AiCallRecord.FEATURE_ITEM_ANALYZE;
  public static final String ADMIN_TEST = AiCallRecord.FEATURE_ADMIN_TEST;

  public static final List<String> ALL = List.of(ITEM_ANALYZE, ADMIN_TEST);

  private AiFeatures() {}

  /** 대문자로 시작하는 대문자·숫자·밑줄 2~30자 */
  public static boolean isValidName(String feature) {
    return feature != null && AiCallRecord.FEATURE_FORMAT.matcher(feature).matches();
  }
}
