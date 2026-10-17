package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockAiProviderTest {

    final MockAiProvider provider = new MockAiProvider();

    @Test
    void 같은_바이트는_같은_결과() {
        byte[] photo = {1, 2, 3, 4, 5};
        AiImage image = new AiImage(photo, "image/jpeg", AiAnalysisService.sha256(photo));
        assertThat(provider.analyze(image)).isEqualTo(provider.analyze(image));
        assertThat(provider.name()).isEqualTo("mock");
        assertThat(provider.model()).isNull();
    }

    @Test
    void 어떤_사진이든_카테고리와_세부_종류_짝이_맞고_범위_안의_값이다() {
        Random random = new Random(42);
        Set<String> seenSubTypes = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            byte[] photo = new byte[32];
            random.nextBytes(photo);
            AiSuggestion s = provider.analyze(new AiImage(photo, "image/png", AiAnalysisService.sha256(photo))).suggestion();
            assertThat(s.subType().category()).isEqualTo(s.category());
            assertThat(s.formality()).isBetween(1, 3);
            assertThat(s.confidence()).isBetween(0.6, 0.95);
            assertThat(s.seasons()).isNotEmpty().doesNotHaveDuplicates();
            assertThat(s.styles()).isNotEmpty().doesNotHaveDuplicates();
            assertThat(s.name()).contains(s.subType().label());
            seenSubTypes.add(s.subType().name());
        }
        assertThat(seenSubTypes).hasSize(15);
    }
}
