package app.wearly.ai.domain;

import app.wearly.sharedkernel.code.CodeEntry;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 목업 실행(WEARLY_AI_PROVIDER=mock)의 사진 분석 응답. 사진 바이트의 SHA-256으로 활성 코드 중에서 값을 고른다 (같은 사진 + 같은 코드 = 같은
 * 결과). 세부 종류를 먼저 고르고 종류는 그 parent로 정하므로 항상 짝이 맞는다. 공급자 응답과 같은 JSON 모양이라 같은 검증을 거친다.
 */
public final class MockSuggestions {

  private MockSuggestions() {}

  public static Map<String, Object> plan(String sha256, CodeSnapshot codes) {
    byte[] h = HexFormat.of().parseHex(sha256);
    List<CodeEntry> subTypes = codes.selectableCodes(CodeGroups.SUB_TYPE);
    if (subTypes.isEmpty()) {
      throw new AiProviderException("NO_CODES", "고를 수 있는 세부 종류가 없어요.");
    }
    CodeEntry subType = pick(subTypes, h[0]);
    CodeEntry color = pick(codes.selectableCodes(CodeGroups.COLOR), h[1]);
    CodeEntry pattern = pick(codes.selectableCodes(CodeGroups.PATTERN), h[2]);
    double confidence = 0.6 + Math.floorMod(h[8], 36) / 100.0; // 0.60 ~ 0.95
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("name", (color.label() + " " + subType.label()).strip());
    out.put("category", subType.parent());
    out.put("subType", subType.code());
    out.put("color", color.code());
    out.put("pattern", pattern.code());
    out.put("seasons", pickSome(codes.selectableCodes(CodeGroups.SEASON), h[3], h[4]));
    out.put("styles", pickSome(codes.selectableCodes(CodeGroups.STYLE), h[5], h[6]));
    out.put("formality", 1 + Math.floorMod(h[7], 3));
    out.put("confidence", Math.round(confidence * 100) / 100.0);
    return out;
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
