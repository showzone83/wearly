package app.wearly.common.error;

import app.wearly.common.domain.FieldError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 오류 응답 본문 { status, code, params?, fieldErrors: [{ field, code, params? }], traceId }.
 * 문장(message)은 넣지 않는다: 화면 문구는 앱·관리자가 code별로 정한다. params는 값이 있을 때만 넣는다.
 */
public record ErrorResponse(
    int status,
    String code,
    Map<String, Object> params,
    List<FieldError> fieldErrors,
    String traceId) {

  public static ErrorResponse of(ErrorCode code) {
    return of(code, Map.of(), List.of());
  }

  public static ErrorResponse of(
      ErrorCode code, Map<String, Object> params, List<FieldError> fieldErrors) {
    return new ErrorResponse(code.status(), code.name(), params, fieldErrors, TraceIds.current());
  }

  /** JSON으로 쓸 모양 (필드 순서 고정, 빈 params는 뺀다) */
  public Map<String, Object> body() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", status);
    out.put("code", code);
    if (params != null && !params.isEmpty()) {
      out.put("params", params);
    }
    out.put("fieldErrors", fieldErrors.stream().map(ErrorResponse::fieldBody).toList());
    out.put("traceId", traceId);
    return out;
  }

  private static Map<String, Object> fieldBody(FieldError e) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("field", e.field());
    out.put("code", e.code().name());
    if (!e.params().isEmpty()) {
      out.put("params", e.params());
    }
    return out;
  }
}
