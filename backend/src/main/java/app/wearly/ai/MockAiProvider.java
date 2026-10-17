package app.wearly.ai;

import app.wearly.domain.Category;
import app.wearly.domain.ColorKey;
import app.wearly.domain.Pattern;
import app.wearly.domain.Season;
import app.wearly.domain.StyleTag;
import app.wearly.domain.SubType;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 목업 공급자. 사진 바이트의 SHA-256으로 값을 정한다 (같은 사진 = 같은 결과).
 * 세부 종류를 먼저 고르고 카테고리는 그 짝으로 정하므로 항상 짝이 맞는다.
 */
public class MockAiProvider implements AiProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String model() {
        return null;
    }

    @Override
    public AiResult analyze(AiImage image) {
        byte[] h = HexFormat.of().parseHex(image.sha256());
        SubType subType = pick(SubType.values(), h[0]);
        Category category = subType.category();
        ColorKey color = pick(ColorKey.values(), h[1]);
        Pattern pattern = pick(Pattern.values(), h[2]);
        List<Season> seasons = pickSome(Season.values(), h[3], h[4]);
        List<StyleTag> styles = pickSome(StyleTag.values(), h[5], h[6]);
        int formality = 1 + Math.floorMod(h[7], 3);
        double confidence = 0.6 + Math.floorMod(h[8], 36) / 100.0; // 0.60 ~ 0.95
        String name = color.label() + " " + subType.label();
        AiSuggestion suggestion = new AiSuggestion(name, category, subType, color, pattern, seasons, styles, formality,
                Math.round(confidence * 100) / 100.0);
        return new AiResult(suggestion, null, null, null);
    }

    private static <T> T pick(T[] values, byte b) {
        return values[Math.floorMod(b, values.length)];
    }

    /** 1~2개를 원래 열거 순서대로 고른다 */
    private static <T> List<T> pickSome(T[] values, byte first, byte second) {
        int a = Math.floorMod(first, values.length);
        int b = Math.floorMod(second, values.length);
        List<T> picked = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            if (i == a || i == b) {
                picked.add(values[i]);
            }
        }
        return picked;
    }
}
