package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminCodeUseCase;
import app.wearly.admin.application.port.in.dto.AdminCodesDto;
import app.wearly.admin.application.port.in.dto.AdminCodesDto.AdminCodeDto;
import app.wearly.admin.application.port.in.dto.AdminCodesDto.GroupDto;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.code.application.port.in.CodeManagementUseCase;
import app.wearly.code.application.port.in.dto.CodeChangeDto;
import app.wearly.code.application.port.in.dto.CodeDto;
import app.wearly.code.application.port.in.dto.CreateCodeDto;
import app.wearly.code.application.port.in.dto.PatchCodeDto;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 코드 관리 (07 문서 4장). 실제 변경은 code 컨텍스트의 유스케이스가 하고(커밋 뒤 코드 캐시를 다시 읽음), 여기서는 관리자 화면 모양을 만들고 변경 이력을
 * 같은 트랜잭션에 남긴다.
 */
@Service
public class AdminCodeService implements AdminCodeUseCase {

  private final CodeCatalog catalog;
  private final AdminCodeOrderWriter orderWriter;
  private final CodeManagementUseCase codes;
  private final RecordAuditPort audit;

  public AdminCodeService(
      CodeCatalog catalog,
      CodeManagementUseCase codes,
      RecordAuditPort audit,
      AdminCodeOrderWriter orderWriter) {
    this.catalog = catalog;
    this.orderWriter = orderWriter;
    this.codes = codes;
    this.audit = audit;
  }

  @Override
  @Transactional(readOnly = true)
  public AdminCodesDto list() {
    CodeSnapshot s = catalog.current();
    Map<String, Long> usage = codes.itemUsage();
    return new AdminCodesDto(
        s.version(),
        s.groups().stream()
            .map(
                g ->
                    new GroupDto(
                        g.group(),
                        g.name(),
                        g.description(),
                        g.codes().stream()
                            .map(
                                c ->
                                    new AdminCodeDto(
                                        c.code(),
                                        c.label(),
                                        c.parent(),
                                        c.sortOrder(),
                                        c.active(),
                                        c.attrs(),
                                        usage.getOrDefault(g.group() + ":" + c.code(), 0L)))
                            .toList()))
            .toList());
  }

  @Override
  @Transactional
  public AdminCodeDto create(long adminId, String group, CreateCodeDto command) {
    AdminCodeDto after = view(codes.create(group, command));
    audit.record(
        adminId,
        "CODE_CREATE",
        AuditTargets.CODE,
        target(group, after.code()),
        null,
        AuditSnapshots.code(after));
    return after;
  }

  /** 트랜잭션 밖: 쓰기(AdminCodeOrderWriter)가 커밋되어 코드 캐시가 다시 읽힌 뒤 목록을 만든다 */
  @Override
  public AdminCodesDto reorder(long adminId, String group, List<String> order) {
    orderWriter.reorder(adminId, group, order);
    return list();
  }

  @Override
  @Transactional
  public AdminCodeDto patch(long adminId, String group, String code, PatchCodeDto patch) {
    CodeChangeDto change = codes.patch(group, code, patch);
    AdminCodeDto after = view(change.after());
    audit.record(
        adminId,
        "CODE_UPDATE",
        AuditTargets.CODE,
        target(group, code),
        AuditSnapshots.code(view(change.before())),
        AuditSnapshots.code(after));
    return after;
  }

  @Override
  @Transactional
  public void delete(long adminId, String group, String code) {
    CodeDto before = codes.delete(group, code);
    audit.record(
        adminId,
        "CODE_DELETE",
        AuditTargets.CODE,
        target(group, code),
        AuditSnapshots.code(view(before)),
        null);
  }

  private static AdminCodeDto view(CodeDto c) {
    return new AdminCodeDto(
        c.code(), c.label(), c.parent(), c.sortOrder(), c.active(), c.attrs(), c.usageCount());
  }

  static String target(String group, String code) {
    return group + ":" + code;
  }
}
