package app.wearly.ai.adapter.out.ai.mock;

import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiJsonPrompt;
import app.wearly.ai.domain.AiJsonResult;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderRules;
import app.wearly.ai.domain.AiResult;
import app.wearly.ai.domain.AiSuggestion;
import app.wearly.common.domain.code.CodeEntry;
import app.wearly.common.domain.code.CodeGroups;
import app.wearly.common.domain.code.CodeSnapshot;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 목업 공급자 (WEARLY_AI_PROVIDER=mock일 때만). 사진 바이트의 SHA-256으로 활성 코드 중에서 값을 고른다 (같은 사진 + 같은 코드 = 같은 결과).
 * 세부 종류를 먼저 고르고 종류는 그 parent로 정하므로 항상 짝이 맞는다.
 */
public class MockAiProvider implements AiModelPort {

  public static final String ID = AiProviderRules.MOCK_ID;

  @Override
  public String name() {
    return ID;
  }

  @Override
  public String model() {
    return null;
  }

  @Override
  public AiResult analyze(AiImage image, CodeSnapshot codes) {
    byte[] h = HexFormat.of().parseHex(image.sha256());
    List<CodeEntry> subTypes = codes.selectableCodes(CodeGroups.SUB_TYPE);
    if (subTypes.isEmpty()) {
      throw new AiProviderException("NO_CODES", "고를 수 있는 세부 종류가 없어요.");
    }
    CodeEntry subType = pick(subTypes, h[0]);
    CodeEntry color = pick(codes.selectableCodes(CodeGroups.COLOR), h[1]);
    CodeEntry pattern = pick(codes.selectableCodes(CodeGroups.PATTERN), h[2]);
    List<String> seasons = pickSome(codes.selectableCodes(CodeGroups.SEASON), h[3], h[4]);
    List<String> styles = pickSome(codes.selectableCodes(CodeGroups.STYLE), h[5], h[6]);
    int formality = 1 + Math.floorMod(h[7], 3);
    double confidence = 0.6 + Math.floorMod(h[8], 36) / 100.0; // 0.60 ~ 0.95
    String name = (color.label() + " " + subType.label()).strip();
    AiSuggestion suggestion =
        new AiSuggestion(
            name,
            subType.parent(),
            subType.code(),
            color.code(),
            pattern.code(),
            seasons,
            styles,
            formality,
            Math.round(confidence * 100) / 100.0);
    return new AiResult(suggestion, null, null, null);
  }

  /** 호출한 쪽이 만든 결정적 응답(mockOutput)을 그대로 돌려준다 (규칙에 맞는 후보는 호출한 쪽이 만든다) */
  @Override
  public AiJsonResult generateJson(AiJsonPrompt prompt) {
    if (prompt.mockOutput() == null) {
      throw new AiProviderException("NO_MOCK_OUTPUT", "목업 응답이 없는 요청이에요.");
    }
    return new AiJsonResult(prompt.mockOutput(), null, null, null);
  }

  private static CodeEntry pick(List<CodeEntry> values, byte b) {
    if (values.isEmpty()) {
      throw new AiProviderException("NO_CODES", "고를 수 있는 코드가 없어요.");
    }
    return values.get(Math.floorMod(b, values.size()));
  }

  /** 1~2개를 코드 순서대로 고른다 */
  private static List<String> pickSome(List<CodeEntry> values, byte first, byte second) {
    if (values.isEmpty()) {
      return List.of();
    }
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
