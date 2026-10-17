package app.wearly.code;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.code.application.port.in.CodeUsageSource;
import app.wearly.code.application.port.in.dto.CodeUsageCountDto;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Session;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * H1: code 컨텍스트는 옷장·프로필 테이블을 읽지 않고 CodeUsageSource(각 컨텍스트의 입력 어댑터)로 사용 수를 묻는다. 옷장 아이템의 코드 컬럼 7개와
 * 프로필의 코드 칸 2개가 모두 사용 수에 잡히는지 확인한다 (컬럼을 늘리고 쿼리를 빠뜨리면 여기서 실패한다).
 */
class CodeUsageSourceIntegrationTest extends IntegrationTestBase {

  @Autowired List<CodeUsageSource> sources;

  @Test
  void 아이템과_프로필_사용처가_하나씩_등록되어_있다() {
    assertThat(sources)
        .extracting(CodeUsageSource::kind)
        .containsExactlyInAnyOrder(CodeUsageSource.Kind.ITEM, CodeUsageSource.Kind.PROFILE);
  }

  @Test
  void 아이템의_코드_컬럼_7개가_모두_사용_수에_잡힌다() {
    CodeUsageSource items = source(CodeUsageSource.Kind.ITEM);
    Map<String, String> used = new HashMap<>();
    used.put("CATEGORY", "DRESS");
    used.put("SUB_TYPE", "JUMPSUIT");
    used.put("COLOR", "lavender");
    used.put("PATTERN", "FLORAL");
    used.put("ITEM_STATUS", "REPAIR");
    used.put("SEASON", "SUMMER");
    used.put("STYLE", "FEMININE");
    Map<String, Long> before = new HashMap<>();
    used.forEach((g, c) -> before.put(g, items.countUsing(g, c)));

    final Session s = signup();
    Map<String, Object> item = new HashMap<>();
    item.put("name", "사용 수 확인");
    item.put("category", "DRESS");
    item.put("subType", "JUMPSUIT");
    item.put("color", "lavender");
    item.put("pattern", "FLORAL");
    item.put("seasons", List.of("SUMMER"));
    item.put("styles", List.of("FEMININE"));
    item.put("formality", 1);
    item.put("status", "REPAIR");
    api().post("/api/v1/wardrobe/items", s.accessToken(), item).expect(201);

    Map<String, Long> byCode = byCode(items);
    used.forEach(
        (g, c) -> {
          assertThat(items.countUsing(g, c)).as(g).isEqualTo(before.get(g) + 1);
          assertThat(byCode.get(g + ":" + c)).as(g).isEqualTo(items.countUsing(g, c));
        });
    assertThat(items.countUsing("NOPE", "DRESS")).isZero();
  }

  @Test
  void 프로필의_색과_취향이_사용_수에_잡힌다() {
    CodeUsageSource profiles = source(CodeUsageSource.Kind.PROFILE);
    final long colors = profiles.countUsing("COLOR", "mint");
    final long moods = profiles.countUsing("STYLE", "SPORTY");
    final Session s = signup();
    Map<String, Object> profile = new HashMap<>();
    profile.put("name", "프로필");
    profile.put("dressCodes", List.of("CASUAL"));
    profile.put("moods", List.of("SPORTY"));
    profile.put("colors", List.of("mint"));
    profile.put("workType", "WEEKDAY");
    profile.put("workDays", List.of(1));
    profile.put("workRule", true);
    profile.put("offRule", true);
    profile.put("notify", Map.of("morning", true, "evening", true, "dMinus1", true));
    api().put("/api/v1/users/me/profile", s.accessToken(), profile).expect(200);

    assertThat(profiles.countUsing("COLOR", "mint")).isEqualTo(colors + 1);
    assertThat(profiles.countUsing("STYLE", "SPORTY")).isEqualTo(moods + 1);
    assertThat(profiles.countUsing("PATTERN", "SOLID")).isZero();
    Map<String, Long> byCode = byCode(profiles);
    assertThat(byCode.get("COLOR:mint")).isEqualTo(colors + 1);
    assertThat(byCode.get("STYLE:SPORTY")).isEqualTo(moods + 1);
  }

  private CodeUsageSource source(CodeUsageSource.Kind kind) {
    return sources.stream().filter(s -> s.kind() == kind).findFirst().orElseThrow();
  }

  private static Map<String, Long> byCode(CodeUsageSource source) {
    Map<String, Long> out = new HashMap<>();
    for (CodeUsageCountDto c : source.countByCode()) {
      out.merge(c.group() + ":" + c.code(), c.count(), Long::sum);
    }
    return out;
  }
}
