package app.wearly.admin;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 관리자 변경 이력 남기기. 변경과 같은 트랜잭션에 쓴다 (변경이 롤백되면 이력도 남지 않는다) */
@Service
public class AuditService {

    public static final String CODE = "CODE";
    public static final String AI_PROVIDER = "AI_PROVIDER";
    public static final String USER = "USER";

    private final AuditLogRepository logs;
    private final JsonMapper jsonMapper;

    public AuditService(AuditLogRepository logs, JsonMapper jsonMapper) {
        this.logs = logs;
        this.jsonMapper = jsonMapper;
    }

    /** before·after는 DTO나 Map. JSON 객체로 바꿔 저장한다 (null이면 비움) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long adminUserId, String action, String targetType, String targetId, Object before, Object after) {
        logs.save(new AuditLog(adminUserId, action, targetType, targetId, toMap(before), toMap(after)));
    }

    private Map<String, Object> toMap(Object value) {
        return value == null ? null : jsonMapper.convertValue(value, new TypeReference<>() {
        });
    }
}
