package app.wearly.outfit.application.service;

import app.wearly.outfit.domain.AiScores;
import app.wearly.outfit.domain.OutfitCandidate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 코디 추천 호출 기록(tb_ai_analysis_log.result)의 JSON 모양 (L4: 도메인은 기록 형식을 모른다). 키와 순서는 예전과 같다:
 * {candidates: [{items, reason, aiScores:{color, occasion, weather, taste, diversity}}], dropped}
 */
final class OutfitLogResults {

  private OutfitLogResults() {}

  static Map<String, Object> of(List<OutfitCandidate> accepted, List<String> dropped) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("candidates", accepted.stream().map(OutfitLogResults::candidate).toList());
    m.put("dropped", dropped);
    return m;
  }

  private static Map<String, Object> candidate(OutfitCandidate c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("items", new LinkedHashMap<>(c.items()));
    m.put("reason", c.reason());
    m.put("aiScores", scores(c.aiScores()));
    return m;
  }

  private static Map<String, Object> scores(AiScores s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("color", s.color());
    m.put("occasion", s.occasion());
    m.put("weather", s.weather());
    m.put("taste", s.taste());
    m.put("diversity", s.diversity());
    return m;
  }
}
