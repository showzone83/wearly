package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Session;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 공급자 오류는 502 AI_UNAVAILABLE이고 한도에 세지 않는다. */
class AiFailureIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    AiProviderRegistry registry;

    @Autowired
    AiAnalysisService service;

    @Autowired
    AiUsageMapper usageMapper;

    @Test
    void 공급자_오류는_502이고_실패_기록만_남는다() {
        AiProvider provider = mock(AiProvider.class);
        when(provider.id()).thenReturn("claude");
        when(provider.model()).thenReturn("claude-opus-5-5");
        when(provider.analyze(any(), any())).thenThrow(new AiProviderException("INVALID_OUTPUT", "허용 목록 밖"));
        when(registry.candidates()).thenReturn(List.of(provider));

        Session s = signup();
        for (int i = 0; i < 4; i++) {
            api().upload("/api/v1/wardrobe/items/analyze", s.accessToken(), "image", new byte[] {1, 2, (byte) i}, "a.jpg",
                    "image/jpeg").expectError(502, "AI_UNAVAILABLE");
        }
        long userId = Long.parseLong(s.userId());
        assertThat(service.usedToday(userId)).isZero();
        assertThat(usageMapper.findRecent(userId, 10)).hasSize(4)
                .allSatisfy(row -> assertThat(row.getStatus()).isEqualTo("FAILED"));

        doThrow(new IllegalStateException("뜻밖의 오류")).when(provider).analyze(any(), any());
        api().upload("/api/v1/wardrobe/items/analyze", s.accessToken(), "image", new byte[] {9}, "a.jpg", "image/jpeg")
                .expectError(502, "AI_UNAVAILABLE");
    }
}
