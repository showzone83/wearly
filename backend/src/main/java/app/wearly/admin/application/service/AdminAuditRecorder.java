package app.wearly.admin.application.service;

import app.wearly.admin.application.port.out.RecordAuditPort;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 변경 이력만 따로 한 트랜잭션으로 남긴다. 바뀐 내용이 이미 다른 곳에서 커밋된 경우에 쓴다 (날씨 지금 실행: 실행 행은 다른 스레드가 바로 봐야 해서 먼저 커밋한다).
 * 같은 클래스 안에서 부르면 @Transactional이 적용되지 않으므로 따로 둔다.
 */
@Service
public class AdminAuditRecorder {

  private final RecordAuditPort audit;

  public AdminAuditRecorder(RecordAuditPort audit) {
    this.audit = audit;
  }

  @Transactional
  public void record(
      long adminId,
      String action,
      String targetType,
      String targetId,
      Map<String, Object> before,
      Map<String, Object> after) {
    audit.record(adminId, action, targetType, targetId, before, after);
  }
}
