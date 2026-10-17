package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.ai.adapter.out.ai.mock.MockAiProvider;
import app.wearly.ai.application.service.AiAnalysisService;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiSuggestion;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockAiProviderTest {

  final MockAiProvider provider = new MockAiProvider();
  final CodeSnapshot codes = TestCodes.seed();

  @Test
  void 같은_바이트는_같은_결과() {
    byte[] photo = {1, 2, 3, 4, 5};
    AiImage image = new AiImage(photo, "image/jpeg", AiAnalysisService.sha256(photo));
    assertThat(provider.analyze(image, codes)).isEqualTo(provider.analyze(image, codes));
    assertThat(provider.name()).isEqualTo("mock");
    assertThat(provider.model()).isNull();
  }

  @Test
  void 어떤_사진이든_활성_코드_안에서_짝이_맞는_값을_고른다() {
    Random random = new Random(42);
    Set<String> seenSubTypes = new HashSet<>();
    for (int i = 0; i < 3000; i++) {
      byte[] photo = new byte[32];
      random.nextBytes(photo);
      AiSuggestion s =
          provider
              .analyze(new AiImage(photo, "image/png", AiAnalysisService.sha256(photo)), codes)
              .suggestion();
      assertThat(codes.belongsTo(s.subType(), s.category())).isTrue();
      assertThat(codes.selectable(CodeGroups.COLOR, s.color())).isTrue();
      assertThat(codes.selectable(CodeGroups.PATTERN, s.pattern())).isTrue();
      assertThat(s.seasons())
          .isNotEmpty()
          .doesNotHaveDuplicates()
          .allMatch(v -> codes.selectable(CodeGroups.SEASON, v));
      assertThat(s.styles())
          .isNotEmpty()
          .doesNotHaveDuplicates()
          .allMatch(v -> codes.selectable(CodeGroups.STYLE, v));
      assertThat(s.formality()).isBetween(1, 3);
      assertThat(s.confidence()).isBetween(0.6, 0.95);
      assertThat(s.name()).contains(codes.label(CodeGroups.SUB_TYPE, s.subType()));
      seenSubTypes.add(s.subType());
    }
    assertThat(seenSubTypes).hasSize(codes.selectableCodes(CodeGroups.SUB_TYPE).size());
  }

  @Test
  void 비활성_코드는_고르지_않는다() {
    CodeSnapshot partial = TestCodes.withInactive("CATEGORY:TOP", "COLOR:navy", "STYLE:NEAT");
    Random random = new Random(7);
    for (int i = 0; i < 1000; i++) {
      byte[] photo = new byte[16];
      random.nextBytes(photo);
      AiSuggestion s =
          provider
              .analyze(new AiImage(photo, "image/png", AiAnalysisService.sha256(photo)), partial)
              .suggestion();
      assertThat(s.category()).isNotEqualTo("TOP");
      assertThat(s.color()).isNotEqualTo("navy");
      assertThat(s.styles()).doesNotContain("NEAT");
    }
  }
}
