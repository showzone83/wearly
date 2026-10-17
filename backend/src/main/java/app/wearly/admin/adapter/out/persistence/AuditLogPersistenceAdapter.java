package app.wearly.admin.adapter.out.persistence;

import app.wearly.admin.application.port.out.RecordAuditPort;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** RecordAuditPort 구현 (JPA). 변경과 같은 트랜잭션에 쓴다 (트랜잭션 밖에서 부르면 오류) */
@Component
class AuditLogPersistenceAdapter implements RecordAuditPort {

    private final AuditLogRepository logs;
    private final JsonMapper jsonMapper;

    AuditLogPersistenceAdapter(AuditLogRepository logs, JsonMapper jsonMapper) {
        this.logs = logs;
        this.jsonMapper = jsonMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long adminUserId, String action, String targetType, String targetId, Object before, Object after) {
        logs.save(new AuditLogEntity(adminUserId, action, targetType, targetId, toMap(before), toMap(after)));
    }

    private Map<String, Object> toMap(Object value) {
        return value == null ? null : jsonMapper.convertValue(value, new TypeReference<>() {
        });
    }
}
