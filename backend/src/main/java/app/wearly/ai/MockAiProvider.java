package app.wearly.ai;

import app.wearly.code.CodeGroups;
import app.wearly.code.CodeSnapshot;
import app.wearly.code.CodeSnapshot.Entry;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 목업 공급자. 사진 바이트의 SHA-256으로 활성 코드 중에서 값을 고른다 (같은 사진·같은 코드 = 같은 결과).
 * 세부 종류를 먼저 고르고 종류는 그 상위로 정하므로 항상 짝이 맞는다.
 */
public class MockAiProvider implements AiProvider {

    public static final String ID = "mock";

    private final String id;

    public MockAiProvider() {
        this(ID);
    }

    public MockAiProvider(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String model() {
        return null;
    }

    @Override
    public AiResult analyze(AiImage image, CodeSnapshot codes) {
        List<Entry> subTypes = codes.selectableCodes(CodeGroups.SUB_TYPE);
        List<Entry> colors = codes.selectableCodes(CodeGroups.COLOR);
        List<Entry> patterns = codes.selectableCodes(CodeGroups.PATTERN);
        List<Entry> seasons = codes.selectableCodes(CodeGroups.SEASON);
        List<Entry> styles = codes.selectableCodes(CodeGroups.STYLE);
        if (subTypes.isEmpty() || colors.isEmpty() || patterns.isEmpty() || seasons.isEmpty() || styles.isEmpty()) {
            throw new AiProviderException("NO_CODES", "고를 수 있는 활성 코드가 없어요.");
        }
        byte[] h = HexFormat.of().parseHex(image.sha256());
        Entry subType = pick(subTypes, h[0]);
        Entry color = pick(colors, h[1]);
        Entry pattern = pick(patterns, h[2]);
        int formality = 1 + Math.floorMod(h[7], 3);
        double confidence = 0.6 + Math.floorMod(h[8], 36) / 100.0; // 0.60 ~ 0.95
        String name = color.label() + " " + subType.label();
        AiSuggestion suggestion = new AiSuggestion(name, subType.parent(), subType.code(), color.code(), pattern.code(),
                pickSome(seasons, h[3], h[4]), pickSome(styles, h[5], h[6]), formality,
                Math.round(confidence * 100) / 100.0);
        return new AiResult(suggestion, null, null, null);
    }

    private static Entry pick(List<Entry> values, byte b) {
        return values.get(Math.floorMod(b, values.size()));
    }

    /** 1~2개를 코드 순서대로 고른다 */
    private static List<String> pickSome(List<Entry> values, byte first, byte second) {
        int a = Math.floorMod(first, values.size());
        int b = Math.floorMod(second, values.size());
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (i == a || i == b) {
                picked.add(values.get(i).code());
            }
        }
        return picked;
    }
}
