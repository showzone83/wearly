package app.wearly.outfit.application.port.in;

/** outfit 컨텍스트가 AI 호출 기록·일일 한도에 쓰는 기능 이름 (L6: 기능 이름은 호출하는 컨텍스트가 소유한다) */
public final class OutfitFeatures {

  /** AI 코디 추천 (09 문서). 사진 분석과 따로 센다 */
  public static final String OUTFIT_RECOMMEND = "OUTFIT_RECOMMEND";

  private OutfitFeatures() {}
}
