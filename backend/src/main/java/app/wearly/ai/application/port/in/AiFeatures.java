package app.wearly.ai.application.port.in;

import app.wearly.ai.domain.AiCallRecord;
import java.util.List;

/** tb_ai_analysis_log.feature 값 (V5 CHECK와 같다) */
public final class AiFeatures {

  public static final String ITEM_ANALYZE = AiCallRecord.FEATURE_ITEM_ANALYZE;
  public static final String ADMIN_TEST = AiCallRecord.FEATURE_ADMIN_TEST;
  public static final String OUTFIT_RECOMMEND = AiCallRecord.FEATURE_OUTFIT_RECOMMEND;

  public static final List<String> ALL = List.of(ITEM_ANALYZE, ADMIN_TEST, OUTFIT_RECOMMEND);

  private AiFeatures() {}
}
