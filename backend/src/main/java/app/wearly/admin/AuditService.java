package app.wearly.admin;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 변경 이력을 남긴다. 바꾸는 작업과 같은 트랜잭션에서 저장해 함께 커밋·롤백된다. */
@Service
public class AuditService {

    public static final String CODE = "CODE";
    public static final String AI_PROVIDER = "AI_PROVIDER";
    public static final String USER = "USER";

    private final AdminAuditLogRepository logs;

    public AuditService(AdminAuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long adminUserId, String action, String targetType, String targetId, Map<String, Object> before,
            Map<String, Object> after) {
        logs.save(new AdminAuditLog(adminUserId, action, targetType, targetId, before, after));
    }
}
