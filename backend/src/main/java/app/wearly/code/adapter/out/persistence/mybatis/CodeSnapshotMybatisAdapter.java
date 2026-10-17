package app.wearly.code.adapter.out.persistence.mybatis;

import app.wearly.code.adapter.out.persistence.mybatis.dto.CodeRowDto;
import app.wearly.code.adapter.out.persistence.mybatis.mapper.CodeMapper;
import app.wearly.code.application.port.out.CodeSnapshotPort;
import app.wearly.common.domain.code.CodeEntry;
import app.wearly.common.domain.code.CodeGroupEntry;
import app.wearly.common.domain.code.CodeSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 코드 메모리 캐시 (06 문서 4장 "캐시"). 시작할 때 MyBatis로 읽고, 관리자 API로 코드가 바뀌면 커밋 뒤에 다시 읽는다. 읽기는 잠금 없이 volatile
 * 스냅샷을 돌려준다.
 */
@Component
class CodeSnapshotMybatisAdapter implements CodeSnapshotPort {

  private static final Logger log = LoggerFactory.getLogger(CodeSnapshotMybatisAdapter.class);

  private final CodeMapper mapper;
  private volatile CodeSnapshot snapshot;

  CodeSnapshotMybatisAdapter(CodeMapper mapper) {
    this.mapper = mapper;
  }

  /** 현재 스냅샷. 아직 읽지 않았으면 지금 읽는다 */
  @Override
  public CodeSnapshot current() {
    CodeSnapshot s = snapshot;
    if (s == null) {
      synchronized (this) {
        if (snapshot == null) {
          snapshot = load();
        }
        s = snapshot;
      }
    }
    return s;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void warmUp() {
    CodeSnapshot s = reload();
    log.info(
        "코드 캐시: {}개 그룹, {}개 코드 (version={})",
        s.groups().size(),
        s.groups().stream().mapToInt(g -> g.codes().size()).sum(),
        s.version());
  }

  synchronized CodeSnapshot reload() {
    snapshot = load();
    return snapshot;
  }

  @Override
  public void refreshAfterCommit() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
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
    Map<String, List<CodeEntry>> byGroup = new LinkedHashMap<>();
    for (CodeRowDto r : mapper.findCodes()) {
      Map<String, Object> attrs =
          r.getAttrs() == null ? Map.of() : Collections.unmodifiableMap(r.getAttrs());
      byGroup
          .computeIfAbsent(r.getGroupCode(), k -> new ArrayList<>())
          .add(
              new CodeEntry(
                  r.getCode(),
                  r.getLabel(),
                  r.getParentCode(),
                  r.getSortOrder(),
                  r.isActive(),
                  attrs));
    }
    List<CodeGroupEntry> groups =
        mapper.findGroups().stream()
            .map(
                g ->
                    new CodeGroupEntry(
                        g.getGroupCode(),
                        g.getName(),
                        g.getDescription(),
                        g.getSortOrder(),
                        List.copyOf(byGroup.getOrDefault(g.getGroupCode(), List.of()))))
            .toList();
    return new CodeSnapshot(mapper.version(), groups);
  }
}
