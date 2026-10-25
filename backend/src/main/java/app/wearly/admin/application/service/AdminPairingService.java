package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminPairingUseCase;
import app.wearly.admin.application.port.in.dto.AdminPairingActionDto;
import app.wearly.admin.application.port.in.dto.AdminPairingDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.out.AdminPairingQueryPort;
import app.wearly.admin.application.port.out.AdminPairingQueryPort.PairingFilter;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.application.port.out.dto.AdminPairingRowDto;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.admin.domain.SearchText;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.BusinessException;
import app.wearly.pairing.application.port.in.PairingAdminUseCase;
import app.wearly.pairing.application.port.in.dto.PairingAdminEndDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 옷장 도우미 연결 관리 (doc/26 6단계): 목록·기록은 MyBatis 읽기, 강제 해제는 pairing 컨텍스트의 입력 포트 + 변경 이력 */
@Service
public class AdminPairingService implements AdminPairingUseCase {

  /** tb_closet_pairing.status 값 (pairing 컨텍스트 PairingStatus와 같다) */
  static final List<String> STATUSES = List.of("PENDING", "ACTIVE", "ENDED");

  static final int ACTIONS_LIMIT = 100;

  private final AdminPairingQueryPort queries;
  private final PairingAdminUseCase pairings;
  private final RecordAuditPort audit;

  public AdminPairingService(
      AdminPairingQueryPort queries, PairingAdminUseCase pairings, RecordAuditPort audit) {
    this.queries = queries;
    this.pairings = pairings;
    this.audit = audit;
  }

  @Override
  @Transactional(readOnly = true)
  public PageDto<AdminPairingDto> list(String q, String status, PagingDto paging) {
    String s = SearchText.blankToNull(status);
    if (s != null && !STATUSES.contains(s)) {
      throw BusinessException.validation(FieldError.notAllowed("status", STATUSES));
    }
    PairingFilter filter = new PairingFilter(SearchText.blankToNull(q), s);
    List<AdminPairingDto> items =
        queries.findPairings(filter, paging.size(), paging.offset()).stream()
            .map(AdminPairingService::toDto)
            .toList();
    return new PageDto<>(items, paging.page(), paging.size(), queries.countPairings(filter));
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminPairingActionDto> actions(long pairingId) {
    if (queries.findPairing(pairingId) == null) {
      throw BusinessException.notFound();
    }
    return queries.findActions(pairingId, ACTIONS_LIMIT).stream()
        .map(
            r ->
                new AdminPairingActionDto(
                    r.id(), r.action(), r.itemId(), r.itemName(), r.createdAt()))
        .toList();
  }

  @Override
  @Transactional
  public void forceEnd(long adminId, long pairingId) {
    PairingAdminEndDto ended = pairings.forceEnd(pairingId);
    Map<String, Object> before = new LinkedHashMap<>();
    before.put("ownerId", Long.toString(ended.ownerId()));
    before.put("helperId", Long.toString(ended.helperId()));
    before.put("status", ended.status());
    before.put("permission", ended.permission());
    audit.record(
        adminId,
        "PAIRING_FORCE_END",
        AuditTargets.PAIRING,
        Long.toString(pairingId),
        before,
        Map.of("status", "ENDED", "endedBy", "ADMIN"));
  }

  private static AdminPairingDto toDto(AdminPairingRowDto r) {
    return new AdminPairingDto(
        r.id(),
        r.status(),
        r.permission(),
        r.issuedBy(),
        r.notifyOwner(),
        r.createdAt(),
        r.approvedAt(),
        r.endedAt(),
        r.endedBy(),
        r.ownerId(),
        r.ownerEmail(),
        r.ownerName(),
        r.helperId(),
        r.helperEmail(),
        r.helperName(),
        r.actionCount(),
        r.lastActionAt());
  }
}
