package app.wearly.ai.adapter.out.persistence;

import app.wearly.ai.application.port.out.AiCallLogPort;
import app.wearly.ai.domain.AiCallRecord;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** AiCallLogPort 구현 (JPA). 결과 제안은 JSONB로 저장한다 */
@Component
class AiCallLogPersistenceAdapter implements AiCallLogPort {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final AiAnalysisLogRepository logs;
  private final JsonMapper jsonMapper;

  AiCallLogPersistenceAdapter(AiAnalysisLogRepository logs, JsonMapper jsonMapper) {
    this.logs = logs;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void save(AiCallRecord r) {
    Map<String, Object> result =
        r.result() == null ? null : jsonMapper.convertValue(r.result(), MAP);
    logs.save(
        new AiAnalysisLogEntity(
            r.userId(),
            r.feature(),
            r.provider(),
            r.model(),
            r.status(),
            r.latencyMs(),
            toInt(r.inputTokens()),
            toInt(r.outputTokens()),
            r.imageSha256(),
            r.imageBytes(),
            result,
            r.errorCode()));
  }

  private static Integer toInt(Long value) {
    return value == null ? null : (int) Math.min(Integer.MAX_VALUE, value);
  }
}
