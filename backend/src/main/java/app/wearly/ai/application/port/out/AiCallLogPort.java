package app.wearly.ai.application.port.out;

import app.wearly.ai.domain.AiCallRecord;

/** AI 호출 기록 저장 (JPA, tb_ai_analysis_log) */
public interface AiCallLogPort {

  void save(AiCallRecord record);
}
