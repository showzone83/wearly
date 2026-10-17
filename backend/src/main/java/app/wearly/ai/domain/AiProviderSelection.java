package app.wearly.ai.domain;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 분석 때 쓸 공급자 고르기 (07 문서 3장 "분석 때 공급자 고르기").
 * enabled이고 모델이 있고 키가 있는(usable) 공급자를 priority 순으로 시도한다.
 * priority가 같으면 받은 순서(저장소의 provider_id 순)를 지킨다.
 */
public final class AiProviderSelection {

    /** 시도 순서: priority 오름차순 (안정 정렬이라 같으면 받은 순서) */
    public static final Comparator<AiProviderSetting> ORDER = Comparator.comparingInt(AiProviderSetting::priority);

    private AiProviderSelection() {
    }

    /** 사용·모델·키 모두 갖춤 */
    public static boolean usable(AiProviderSetting s, boolean keyAvailable) {
        return s.enabled() && s.hasModel() && keyAvailable;
    }

    /** 쓸 수 있는 공급자만 시도 순서대로 */
    public static List<AiProviderSetting> candidates(List<AiProviderSetting> all, Predicate<AiProviderSetting> hasKey) {
        return all.stream().filter(s -> usable(s, hasKey.test(s))).sorted(ORDER).toList();
    }
}
