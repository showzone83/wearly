package app.wearly.sharedkernel.code;

/**
 * 드레스 코드(격식) 고정 값 (앱 types.ts Formality, 09 문서 3장). 프로필 dressCodes(user)와 코디 추천
 * context.formality(outfit)가 같이 쓴다 (L5). JSON에는 이름(CASUAL 등)으로 나간다.
 */
public enum DressCode {
  CASUAL("편한 데일리", 1),
  BIZ_CASUAL("비즈니스 캐주얼", 2),
  FORMAL("포멀", 3);

  private final String label;
  private final int level;

  DressCode(String label, int level) {
    this.label = label;
    this.level = level;
  }

  /** 화면·프롬프트용 한국어 이름 */
  public String label() {
    return label;
  }

  /** 격식 단계 1~3 */
  public int level() {
    return level;
  }
}
