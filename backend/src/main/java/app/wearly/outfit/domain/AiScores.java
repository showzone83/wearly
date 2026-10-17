package app.wearly.outfit.domain;

import java.util.List;

/** AI가 매긴 5개 기준 0~100 예상 점수 (참고용. 화면 점수는 앱 엔진이 다시 매긴다) */
public record AiScores(int color, int occasion, int weather, int taste, int diversity) {

  public static final List<String> FIELDS =
      List.of("color", "occasion", "weather", "taste", "diversity");
}
