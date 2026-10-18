package app.wearly.code.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.code.application.port.in.CodeUsageSource;
import app.wearly.code.application.port.in.dto.CodeUsageCountDto;
import app.wearly.code.application.port.in.dto.CreateCodeDto;
import app.wearly.code.application.port.in.dto.PatchCodeDto;
import app.wearly.code.application.port.out.CodePort;
import app.wearly.code.application.port.out.CodeSnapshotPort;
import app.wearly.code.domain.Code;
import app.wearly.code.domain.CodeAttrs;
import app.wearly.common.application.Patch;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.sharedkernel.code.CodeEntry;
import app.wearly.sharedkernel.code.CodeGroupEntry;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 색 짝 양방향 반영, 삭제 가드, 캐시 재적재 요청 (가짜 포트, Spring 없이) */
class CodeManagementServiceTest {

  final CodeSnapshot snapshot = TestCodes.seed();
  final Map<String, Code> rows = new LinkedHashMap<>();
  final List<String> saved = new ArrayList<>();
  final Map<String, Long> itemUsage = new HashMap<>();
  final Map<String, Long> profileUsage = new HashMap<>();
  int refreshes;

  final CodePort codes =
      new CodePort() {
        @Override
        public Optional<Code> find(String group, String code) {
          return Optional.ofNullable(rows.get(group + ":" + code));
        }

        @Override
        public List<Code> findByGroup(String group) {
          return rows.values().stream().filter(c -> c.groupCode().equals(group)).toList();
        }

        @Override
        public List<Code> findIn(String group, List<String> list) {
          return findByGroup(group).stream().filter(c -> list.contains(c.code())).toList();
        }

        @Override
        public boolean exists(String group, String code) {
          return rows.containsKey(group + ":" + code);
        }

        @Override
        public long countChildren(String parentGroup, String parentCode) {
          return findByGroup(parentGroup).stream()
              .filter(c -> parentCode.equals(c.parentCode()))
              .count();
        }

        @Override
        public int maxSortOrder(String group) {
          return findByGroup(group).stream().mapToInt(Code::sortOrder).max().orElse(0);
        }

        @Override
        public Code save(Code code) {
          rows.put(code.groupCode() + ":" + code.code(), code);
          saved.add(code.code());
          return code;
        }

        @Override
        public void saveAll(List<Code> list) {
          list.forEach(this::save);
        }

        @Override
        public void delete(Code code) {
          rows.remove(code.groupCode() + ":" + code.code());
        }
      };

  final CodeSnapshotPort snapshots =
      new CodeSnapshotPort() {
        @Override
        public CodeSnapshot current() {
          return snapshot;
        }

        @Override
        public void refreshAfterCommit() {
          refreshes++;
        }
      };

  /** 프로필을 먼저 넘겨도 문구는 아이템 → 프로필 순서 */
  final CodeManagementService service =
      new CodeManagementService(
          codes,
          List.of(
              usage(CodeUsageSource.Kind.PROFILE, profileUsage),
              usage(CodeUsageSource.Kind.ITEM, itemUsage)),
          snapshots);

  CodeManagementServiceTest() {
    long id = 1;
    for (CodeGroupEntry g : snapshot.groups()) {
      for (CodeEntry e : g.codes()) {
        rows.put(
            g.group() + ":" + e.code(),
            new Code(
                id++,
                g.group(),
                e.code(),
                e.label(),
                e.parent(),
                e.sortOrder(),
                e.active(),
                new LinkedHashMap<>(e.attrs())));
      }
    }
  }

  @Test
  void 색을_추가하면_짝으로_고른_색에도_이_색을_넣는다() {
    service.create(CodeGroups.COLOR, color("teal_test", List.of("white", "navy")));

    assertThat(pairs("teal_test")).containsExactly("navy", "white");
    assertThat(pairs("white")).contains("teal_test");
    assertThat(pairs("navy")).contains("teal_test");
    assertThat(pairs("beige")).doesNotContain("teal_test");
    assertThat(refreshes).isEqualTo(1);
  }

  @Test
  void 짝을_바꾸면_뺀_색에서는_빼고_새로_넣은_색에는_넣는다() {
    service.create(CodeGroups.COLOR, color("teal_test", List.of("white", "navy")));
    saved.clear();

    service.patch(CodeGroups.COLOR, "teal_test", attrsPatch(colorAttrs(List.of("white", "beige"))));

    assertThat(pairs("navy")).doesNotContain("teal_test");
    assertThat(pairs("beige")).contains("teal_test");
    assertThat(pairs("white")).contains("teal_test");
    // 그대로인 짝(white)은 다시 저장하지 않는다
    assertThat(saved).containsExactlyInAnyOrder("teal_test", "navy", "beige");
  }

  @Test
  void 삭제하면_상대_색의_짝에서도_뺀다() {
    service.create(CodeGroups.COLOR, color("teal_test", List.of("white")));

    service.delete(CodeGroups.COLOR, "teal_test");

    assertThat(rows).doesNotContainKey("COLOR:teal_test");
    assertThat(pairs("white")).doesNotContain("teal_test");
  }

  @Test
  void 쓰고_있는_코드는_사용처_순서대로_이유를_적어_삭제를_거부한다() {
    itemUsage.put("COLOR:white", 2L);
    profileUsage.put("COLOR:white", 1L);

    assertThatThrownBy(() -> service.delete(CodeGroups.COLOR, "white"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.CODE_IN_USE);
              // 사용처 순서(아이템 → 프로필)대로 종류와 개수를 params로 준다
              assertThat(e.params().get("usages"))
                  .isEqualTo(
                      List.of(
                          Map.of("kind", "ITEM", "count", 2L),
                          Map.of("kind", "PROFILE", "count", 1L)));
            });
    assertThat(rows).containsKey("COLOR:white");
    assertThat(refreshes).isZero();
  }

  @Test
  void 하위_세부_종류가_있는_종류는_삭제를_거부한다() {
    long children = codes.countChildren(CodeGroups.SUB_TYPE, "TOP");
    assertThat(children).isPositive();

    assertThatThrownBy(() -> service.delete(CodeGroups.CATEGORY, "TOP"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.params().get("usages"))
                    .isEqualTo(List.of(Map.of("kind", "SUB_TYPE", "count", children))));
  }

  @Test
  void 관리자_사용_수는_아이템_사용처만_센다() {
    itemUsage.put("COLOR:navy", 3L);
    profileUsage.put("COLOR:navy", 5L);

    assertThat(service.patch(CodeGroups.COLOR, "navy", attrsPatch(null)).after().usageCount())
        .isEqualTo(3L);
    assertThat(service.itemUsage()).containsEntry("COLOR:navy", 3L);
  }

  private List<String> pairs(String color) {
    return CodeAttrs.pairsOf(rows.get("COLOR:" + color).attrs());
  }

  private static CreateCodeDto color(String code, List<String> pairs) {
    return new CreateCodeDto(code, code, null, null, null, colorAttrs(pairs));
  }

  private static Map<String, Object> colorAttrs(List<String> pairs) {
    Map<String, Object> attrs = new LinkedHashMap<>();
    attrs.put("hex", "#AAF0D1");
    attrs.put("neutral", false);
    attrs.put("light", 0.8);
    attrs.put("pairs", pairs);
    return attrs;
  }

  /** attrs만 보낸 PATCH. null이면 아무것도 보내지 않은 PATCH */
  private static PatchCodeDto attrsPatch(Map<String, Object> attrs) {
    return new PatchCodeDto(
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        Patch.absent(),
        attrs == null ? Patch.absent() : Patch.of(attrs));
  }

  private static CodeUsageSource usage(CodeUsageSource.Kind kind, Map<String, Long> counts) {
    return new CodeUsageSource() {
      @Override
      public Kind kind() {
        return kind;
      }

      @Override
      public List<CodeUsageCountDto> countByCode() {
        return counts.entrySet().stream()
            .map(
                e -> {
                  String[] gc = e.getKey().split(":");
                  return new CodeUsageCountDto(gc[0], gc[1], e.getValue());
                })
            .toList();
      }

      @Override
      public long countUsing(String group, String code) {
        return counts.getOrDefault(group + ":" + code, 0L);
      }
    };
  }
}
