package app.wearly.ai.domain;

/**
 * 공급자 응답이 JSON으로는 맞지만 사진 분석 규칙(활성 코드·짝·범위)에 맞지 않을 때 (도메인 거부). 운송 실패와 코드에서 구분할 수 있게 따로 두되,
 * 기록(error_code)은 예전과 같은 INVALID_OUTPUT이다.
 */
public class RejectedSuggestionException extends AiProviderException {

  public static final String REASON = "INVALID_OUTPUT";

  public RejectedSuggestionException(String message) {
    super(REASON, message);
  }
}
