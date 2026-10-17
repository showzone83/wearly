package app.wearly.code.adapter.out.persistence.jpa;

import app.wearly.code.application.port.out.CodePort;
import app.wearly.code.domain.Code;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** CodePort 구현 (JPA). 엔티티 ↔ 도메인 모델 변환. 쓰기는 모두 flush하고 돌아온다 (M5) */
@Component
class CodeJpaAdapter implements CodePort {

  private final CodeJpaRepository codes;

  CodeJpaAdapter(CodeJpaRepository codes) {
    this.codes = codes;
  }

  @Override
  public Optional<Code> find(String group, String code) {
    return codes.findByGroupCodeAndCode(group, code).map(CodeJpaAdapter::toDomain);
  }

  @Override
  public List<Code> findByGroup(String group) {
    return codes.findByGroupCodeOrderBySortOrderAscIdAsc(group).stream()
        .map(CodeJpaAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Code> findIn(String group, List<String> list) {
    return codes.findByGroupCodeAndCodeIn(group, list).stream()
        .map(CodeJpaAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean exists(String group, String code) {
    return codes.existsByGroupCodeAndCode(group, code);
  }

  @Override
  public long countChildren(String parentGroup, String parentCode) {
    return codes.countByGroupCodeAndParentCode(parentGroup, parentCode);
  }

  @Override
  public int maxSortOrder(String group) {
    return codes.maxSortOrder(group);
  }

  @Override
  public Code save(Code c) {
    return toDomain(codes.saveAndFlush(apply(c)));
  }

  @Override
  public void saveAll(List<Code> list) {
    codes.saveAllAndFlush(list.stream().map(this::apply).toList());
  }

  @Override
  public void delete(Code c) {
    codes.findById(c.id()).ifPresent(codes::delete);
    codes.flush();
  }

  private CodeEntity apply(Code c) {
    CodeEntity entity =
        c.id() == null
            ? new CodeEntity(c.groupCode(), c.code())
            : codes
                .findById(c.id())
                .orElseThrow(() -> new IllegalStateException("코드가 없어요: " + c.id()));
    entity.update(c.label(), c.parentCode(), c.sortOrder(), c.active(), c.attrs());
    if (c.touched()) {
      entity.touch();
    }
    return entity;
  }

  private static Code toDomain(CodeEntity e) {
    return new Code(
        e.getId(),
        e.getGroupCode(),
        e.getCode(),
        e.getLabel(),
        e.getParentCode(),
        e.getSortOrder(),
        e.isActive(),
        e.getAttrs());
  }
}
