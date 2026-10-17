package app.wearly.admin.application.service;

import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.code.application.port.in.CodeManagementUseCase;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 코드 순서 바꾸기와 변경 이력(CODE_REORDER)을 한 트랜잭션으로 (AdminCodeService.reorder가 커밋 뒤 목록을 다시 읽도록 나눈다, L10) */
@Service
public class AdminCodeOrderWriter {

  private final CodeManagementUseCase codes;
  private final RecordAuditPort audit;

  public AdminCodeOrderWriter(CodeManagementUseCase codes, RecordAuditPort audit) {
    this.codes = codes;
    this.audit = audit;
  }

  @Transactional
  public void reorder(long adminId, String group, List<String> order) {
    List<String> before = codes.reorder(group, order);
    audit.record(
        adminId,
        "CODE_REORDER",
        AuditTargets.CODE,
        group,
        Map.of("codes", before),
        Map.of("codes", order));
  }
}
