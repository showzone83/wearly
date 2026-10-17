package app.wearly.admin.application.service;

import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.ai.application.port.in.AiProviderKeyUseCase;
import app.wearly.ai.application.port.in.dto.KeyChangeDto;
import app.wearly.ai.application.port.in.dto.VerifiedKeyDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확인을 마친 웹 키 저장과 그 변경 이력을 한 트랜잭션으로 묶는다 (M6). 키 확인(외부 HTTP)은 AdminAiService가 트랜잭션 밖에서 먼저 한다. 트랜잭션
 * 경계가 메서드에 있어야 하므로 AdminAiService와 다른 빈으로 둔다.
 */
@Service
public class AdminAiKeyWriter {

  private final AiProviderKeyUseCase keys;
  private final RecordAuditPort audit;

  public AdminAiKeyWriter(AiProviderKeyUseCase keys, RecordAuditPort audit) {
    this.keys = keys;
    this.audit = audit;
  }

  /** 변경 이력 AI_PROVIDER_KEY_SET (before·after에 keySource·keyLast4만) */
  @Transactional
  public KeyChangeDto save(long adminId, VerifiedKeyDto key) {
    KeyChangeDto change = keys.saveKey(key, adminId);
    audit.record(
        adminId,
        "AI_PROVIDER_KEY_SET",
        AuditTargets.AI_PROVIDER,
        key.providerId(),
        AuditSnapshots.providerKey(change.before().keySource(), change.before().keyLast4()),
        AuditSnapshots.providerKey(change.after().keySource(), change.after().keyLast4()));
    return change;
  }
}
