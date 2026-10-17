package app.wearly.ai.application.port.in;

import java.util.Map;

/** 공급자가 만든 JSON 객체를 검증해 결과로 바꾼다. 받아들일 수 없으면 RejectedOutputException (다음 공급자로 넘어간다) */
@FunctionalInterface
public interface JsonOutputHandler<T> {

  Accepted<T> accept(Map<String, Object> json);

  /**
   * 받아들인 결과.
   *
   * @param logResult tb_ai_analysis_log.result에 남길 값 (JSON으로 바꿀 수 있는 자바 값)
   */
  record Accepted<T>(T value, Object logResult) {}
}
