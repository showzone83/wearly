package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminCodeUseCase;
import app.wearly.admin.application.port.in.AdminViews.AdminCodeView;
import app.wearly.admin.application.port.in.AdminViews.AdminCodesView;
import app.wearly.admin.application.port.in.AdminViews.AdminGroupView;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.code.application.port.in.CodeInfo;
import app.wearly.code.application.port.in.CodeManagementUseCase;
import app.wearly.code.application.port.in.CreateCodeCommand;
import app.wearly.common.domain.PatchBody;
import app.wearly.common.domain.code.CodeSnapshot;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 코드 관리 (07 문서 4장). 실제 변경은 code 컨텍스트의 유스케이스가 하고(커밋 뒤 코드 캐시를 다시 읽음),
 * 여기서는 응답 모양을 만들고 변경 이력을 같은 트랜잭션에 남긴다.
 */
@Service
public class AdminCodeService implements AdminCodeUseCase {

    private final CodeCatalog catalog;
    private final CodeManagementUseCase codes;
    private final RecordAuditPort audit;

    public AdminCodeService(CodeCatalog catalog, CodeManagementUseCase codes, RecordAuditPort audit) {
        this.catalog = catalog;
        this.codes = codes;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminCodesView list() {
        CodeSnapshot s = catalog.current();
        Map<String, Long> usage = codes.itemUsage();
        return new AdminCodesView(s.version(), s.groups().stream()
                .map(g -> new AdminGroupView(g.group(), g.name(), g.description(), g.codes().stream()
                        .map(c -> new AdminCodeView(c.code(), c.label(), c.parent(), c.sortOrder(), c.active(), c.attrs(),
                                usage.getOrDefault(g.group() + ":" + c.code(), 0L)))
                        .toList()))
                .toList());
    }

    @Override
    @Transactional
    public AdminCodeView create(long adminId, String group, CreateCodeCommand command) {
        AdminCodeView after = view(codes.create(group, command));
        audit.record(adminId, "CODE_CREATE", AuditTargets.CODE, target(group, after.code()), null, after);
        return after;
    }

    @Override
    @Transactional
    public void reorder(long adminId, String group, List<String> order) {
        List<String> before = codes.reorder(group, order);
        audit.record(adminId, "CODE_REORDER", AuditTargets.CODE, group, Map.of("codes", before), Map.of("codes", order));
    }

    @Override
    @Transactional
    public AdminCodeView patch(long adminId, String group, String code, PatchBody body) {
        CodeManagementUseCase.Change change = codes.patch(group, code, body);
        AdminCodeView after = view(change.after());
        audit.record(adminId, "CODE_UPDATE", AuditTargets.CODE, target(group, code), view(change.before()), after);
        return after;
    }

    @Override
    @Transactional
    public void delete(long adminId, String group, String code) {
        CodeInfo before = codes.delete(group, code);
        audit.record(adminId, "CODE_DELETE", AuditTargets.CODE, target(group, code), view(before), null);
    }

    private static AdminCodeView view(CodeInfo c) {
        return new AdminCodeView(c.code(), c.label(), c.parent(), c.sortOrder(), c.active(), c.attrs(), c.usageCount());
    }

    static String target(String group, String code) {
        return group + ":" + code;
    }
}
