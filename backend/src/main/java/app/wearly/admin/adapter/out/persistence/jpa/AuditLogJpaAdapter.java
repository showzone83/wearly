package app.wearly.admin.adapter.out.persistence.jpa;

import app.wearly.admin.application.port.out.RecordAuditPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * RecordAuditPort 구현 (JPA). 변경과 같은 트랜잭션에 쓴다 (트랜잭션 밖에서 부르면 오류). before·after의 시각(Instant)은 API와 같은
 * ISO-8601 UTC 밀리초 문자열로 저장한다 (관리자 화면이 그대로 보여 준다).
 */
@Component
class AuditLogJpaAdapter implements RecordAuditPort {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final AuditLogJpaRepository logs;
  private final JsonMapper jsonMapper;

  AuditLogJpaAdapter(AuditLogJpaRepository logs, JsonMapper jsonMapper) {
    this.logs = logs;
    this.jsonMapper = jsonMapper;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      Long adminUserId,
      String action,
      String targetType,
      String targetId,
      Map<String, Object> before,
      Map<String, Object> after) {
    logs.save(
        new AuditLogEntity(
            adminUserId, action, targetType, targetId, toJson(before), toJson(after)));
  }

  private Map<String, Object> toJson(Map<String, Object> value) {
    return value == null ? null : jsonMapper.convertValue(storable(value), MAP);
  }

  /** Instant는 밀리초까지 자른 ISO-8601 문자열로 (중첩 Map·List 안까지) */
  private static Object storable(Object value) {
    if (value instanceof Instant instant) {
      return instant.truncatedTo(ChronoUnit.MILLIS).toString();
    }
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> out = new LinkedHashMap<>();
      map.forEach((k, v) -> out.put(k, storable(v)));
      return out;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(AuditLogJpaAdapter::storable).toList();
    }
    return value;
  }
}
