package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminAiProviderUseCase;
import app.wearly.admin.application.port.in.AdminViews.ProviderView;
import app.wearly.admin.application.port.in.AdminViews.Today;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.AdminRows.ProviderStat;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.ai.application.port.in.AiProviderAdminUseCase;
import app.wearly.ai.application.port.in.AiProviderInfo;
import app.wearly.ai.application.port.in.CreateProviderCommand;
import app.wearly.ai.application.port.in.ProviderTestResult;
import app.wearly.ai.application.port.in.ProviderTestUseCase;
import app.wearly.common.domain.ImageUpload;
import app.wearly.common.domain.PatchBody;
import app.wearly.common.domain.Zones;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 AI 공급자 관리 (07 문서 3·4장). 실제 변경은 ai 컨텍스트의 유스케이스가 하고,
 * 여기서는 오늘 호출 집계(MyBatis)를 붙인 응답을 만들고 변경 이력을 같은 트랜잭션에 남긴다.
 */
@Service
public class AdminAiService implements AdminAiProviderUseCase {

    private final AiProviderAdminUseCase providers;
    private final ProviderTestUseCase tester;
    private final AdminQueryPort queries;
    private final RecordAuditPort audit;
    private final Clock clock;

    public AdminAiService(AiProviderAdminUseCase providers, ProviderTestUseCase tester, AdminQueryPort queries,
            RecordAuditPort audit, Clock clock) {
        this.providers = providers;
        this.tester = tester;
        this.queries = queries;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderView> list() {
        Map<String, Today> today = today();
        return providers.list().stream().map(p -> view(p, today.getOrDefault(p.providerId(), new Today(0, 0)))).toList();
    }

    @Override
    public Types types() {
        return new Types(providers.types(), providers.baseUrlAllowed(), providers.mockMode());
    }

    @Override
    @Transactional
    public ProviderView create(long adminId, CreateProviderCommand command) {
        ProviderView after = view(providers.create(command), new Today(0, 0));
        audit.record(adminId, "AI_PROVIDER_CREATE", AuditTargets.AI_PROVIDER, after.providerId(), null, after);
        return after;
    }

    @Override
    @Transactional
    public ProviderView patch(long adminId, String providerId, PatchBody body) {
        AiProviderInfo current = providers.get(providerId);
        Map<String, Today> today = today();
        Today t = today.getOrDefault(providerId, new Today(0, 0));
        ProviderView before = view(current, t);
        ProviderView after = view(providers.patch(providerId, body), t);
        audit.record(adminId, "AI_PROVIDER_UPDATE", AuditTargets.AI_PROVIDER, providerId, before, after);
        return after;
    }

    @Override
    @Transactional
    public void delete(long adminId, String providerId) {
        ProviderView before = view(providers.delete(providerId), new Today(0, 0));
        audit.record(adminId, "AI_PROVIDER_DELETE", AuditTargets.AI_PROVIDER, providerId, before, null);
    }

    @Override
    public ProviderTestResult test(long adminId, String providerId, ImageUpload image) {
        return tester.test(adminId, providerId, image);
    }

    private static ProviderView view(AiProviderInfo p, Today today) {
        return new ProviderView(p.providerId(), p.type(), p.displayName(), p.model(), p.apiKeyEnv(), p.keyConfigured(),
                p.enabled(), p.priority(), p.options(), p.usable(), today);
    }

    /** 오늘(한국 시간) 공급자별 호출 수 (사진 분석 + 시험 분석) */
    private Map<String, Today> today() {
        LocalDate day = Zones.today(clock);
        Map<String, Today> out = new HashMap<>();
        for (ProviderStat r : queries.providerStats(Zones.startOf(day), Zones.startOf(day.plusDays(1)))) {
            out.put(r.providerId, new Today(r.success, r.failed));
        }
        return out;
    }
}
