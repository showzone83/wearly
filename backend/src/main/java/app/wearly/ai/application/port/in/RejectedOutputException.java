package app.wearly.ai.application.port.in;

/** 공급자 응답이 형식은 맞지만 쓸 수 없을 때 (예: 검증 뒤 남은 후보 0개). 실패로 기록하고 다음 공급자로 넘어간다 */
public class RejectedOutputException extends RuntimeException {

  /** tb_ai_analysis_log.error_code에 남는 값 */
  public static final String REASON = "REJECTED_OUTPUT";

  public RejectedOutputException(String message) {
    super(message);
  }
}
