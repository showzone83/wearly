package app.wearly.code;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 코드 메모리 캐시. 시작할 때 읽고, 관리자 API로 코드가 바뀌면 커밋 뒤 다시 읽는다.
 * version = 코드 테이블 max(updated_at) + "#" + 개수. 바뀌면 /codes의 ETag도 바뀐다.
 */
@Component
public class CodeCatalog {

    private static final Logger log = LoggerFactory.getLogger(CodeCatalog.class);

    private final CodeQueryMapper mapper;
    private final AtomicReference<CodeSnapshot> current = new AtomicReference<>();

    public CodeCatalog(CodeQueryMapper mapper) {
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    void loadOnStartup() {
        CodeSnapshot s = reload();
        int count = s.groups().stream().mapToInt(g -> g.codes().size()).sum();
        log.info("코드 {}개 그룹, {}개 코드를 읽었습니다 (version={})", s.groups().size(), count, s.version());
    }

    /** 지금 캐시. 아직 안 읽었으면 읽는다. */
    public CodeSnapshot snapshot() {
        CodeSnapshot s = current.get();
        return s != null ? s : reload();
    }

    public synchronized CodeSnapshot reload() {
        CodeSnapshot s = load();
        current.set(s);
        return s;
    }

    /** 진행 중인 트랜잭션이 커밋된 뒤 다시 읽는다 (트랜잭션 밖이면 바로). */
    public void reloadAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reload();
                }
            });
        } else {
            reload();
        }
    }

    private CodeSnapshot load() {
        List<CodeRow> rows = mapper.findCodes();
        Map<String, List<CodeSnapshot.Entry>> byGroup = new LinkedHashMap<>();
        Instant max = Instant.EPOCH;
        for (CodeRow r : rows) {
            byGroup.computeIfAbsent(r.getGroupCode(), k -> new ArrayList<>()).add(new CodeSnapshot.Entry(
                    r.getGroupCode(), r.getCode(), r.getLabel(), r.getParentCode(), r.getSortOrder(), r.isActive(),
                    r.getAttrs() == null ? Map.of() : r.getAttrs()));
            if (r.getUpdatedAt() != null && r.getUpdatedAt().isAfter(max)) {
                max = r.getUpdatedAt();
            }
        }
        List<CodeSnapshot.Group> groups = new ArrayList<>();
        for (CodeGroupRow g : mapper.findGroups()) {
            groups.add(new CodeSnapshot.Group(g.getGroupCode(), g.getName(), g.getDescription(), g.getSortOrder(),
                    List.copyOf(byGroup.getOrDefault(g.getGroupCode(), List.of()))));
        }
        return new CodeSnapshot(max + "#" + rows.size(), groups);
    }
}
