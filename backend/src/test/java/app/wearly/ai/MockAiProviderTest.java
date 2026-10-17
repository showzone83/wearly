package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.code.CodeGroups;
import app.wearly.code.CodeSnapshot;
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
        assertThat(provider.id()).isEqualTo("mock");
        assertThat(provider.model()).isNull();
    }

    @Test
    void 어떤_사진이든_활성_코드에서_짝이_맞는_값을_고른다() {
        Random random = new Random(42);
        Set<String> seenSubTypes = new HashSet<>();
        SuggestionParser parser = new SuggestionParser(tools.jackson.databind.json.JsonMapper.builder().build());
        for (int i = 0; i < 3000; i++) {
            byte[] photo = new byte[32];
            random.nextBytes(photo);
            AiSuggestion s = provider.analyze(new AiImage(photo, "image/png", AiAnalysisService.sha256(photo)), codes)
                    .suggestion();
            assertThat(codes.belongsTo(s.subType(), s.category())).isTrue();
            assertThat(codes.selectable(CodeGroups.COLOR, s.color())).isTrue();
            assertThat(codes.selectable(CodeGroups.PATTERN, s.pattern())).isTrue();
            assertThat(s.formality()).isBetween(1, 3);
            assertThat(s.confidence()).isBetween(0.6, 0.95);
            assertThat(s.seasons()).isNotEmpty().doesNotHaveDuplicates();
            assertThat(s.styles()).isNotEmpty().doesNotHaveDuplicates();
            assertThat(s.name()).contains(codes.label(CodeGroups.SUB_TYPE, s.subType()));
            seenSubTypes.add(s.subType());
        }
        assertThat(seenSubTypes).hasSize(codes.selectableCodes(CodeGroups.SUB_TYPE).size()).contains("JEANS", "DRESS");
    }

    @Test
    void 비활성_코드는_고르지_않는다() {
        CodeSnapshot limited = TestCodes.withInactive(CodeGroups.CATEGORY, "TOP", "BOTTOM", "OUTER", "SHOES", "BAG", "ACC");
        Random random = new Random(7);
        for (int i = 0; i < 300; i++) {
            byte[] photo = new byte[16];
            random.nextBytes(photo);
            AiSuggestion s = provider.analyze(new AiImage(photo, "image/png", AiAnalysisService.sha256(photo)), limited)
                    .suggestion();
            assertThat(s.category()).isEqualTo("DRESS"); // 상위 종류가 비활성인 세부 종류도 빠진다
        }
    }
}
