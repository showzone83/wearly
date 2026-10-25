package app.wearly.common.error;

import java.util.List;

/**
 * 05 문서 4장 + 07 문서 + 17·19 문서(날씨) + 18 문서(사진 저장) + 22 문서(관리자 2단계 인증·SSO) + 26 문서(옷장 도우미)의 오류 코드.
 * HTTP 상태 번호와 설명을 함께 둔다 (유스케이스가 쓰므로 Spring Web 타입은 쓰지 않는다).
 *
 * <p>응답에는 code(상수 이름)·params만 나가고 설명 문장은 나가지 않는다. 설명은 로그·문서용이며 앱·관리자의 권장 문구와 같다
 * (resources/error-codes.json의 message, 테스트가 맞춰 본다). 상수 이름은 앱·관리자가 쓰므로 바꾸지 말고 추가만 한다. 셋째 인자부터는 이
 * 코드의 params 이름이다 (없는 이름을 넣으면 BusinessException이 거부한다).
 */
public enum ErrorCode {
  VALIDATION_FAILED(400, "입력값을 확인해 주세요."),
  UNAUTHORIZED(401, "로그인이 필요해요."),
  TOKEN_EXPIRED(401, "로그인이 만료됐어요."),
  TOKEN_INVALID(401, "로그인 정보가 올바르지 않아요."),
  INVALID_CREDENTIALS(401, "이메일 또는 비밀번호가 맞지 않아요."),
  REFRESH_INVALID(401, "다시 로그인해 주세요."),
  REFRESH_REUSED(401, "보안을 위해 다시 로그인해 주세요."),
  FORBIDDEN(403, "접근할 수 없어요."),
  REAUTH_FAILED(403, "비밀번호가 맞지 않아요."),
  ACCOUNT_SUSPENDED(403, "이용이 정지된 계정이에요."),
  // 관리자 계정 보완 (22 문서 1단계): 마지막 관리자, 관리자 2단계 인증(TOTP)
  LAST_ADMIN(409, "마지막 관리자는 해제할 수 없어요."),
  MFA_REQUIRED(403, "2단계 인증을 마쳐야 관리자 기능을 쓸 수 있어요. 다시 로그인해 주세요."),
  MFA_TOKEN_INVALID(401, "인증 시간이 지났어요. 처음부터 다시 로그인해 주세요."),
  MFA_CODE_INVALID(400, "인증 코드가 맞지 않아요.", "remainingAttempts"),
  MFA_LOCKED(429, "인증 코드를 여러 번 틀려 잠시 잠겼어요. 잠시 후 다시 시도해 주세요.", "retryAfterSeconds"),
  MFA_SETUP_REQUIRED(409, "2단계 인증 등록을 먼저 시작해 주세요."),
  MFA_ALREADY_ENROLLED(409, "이미 2단계 인증을 등록했어요."),
  MFA_NOT_ENROLLED(409, "2단계 인증을 등록하지 않았어요."),
  // 관리자 사이트 SSO (22 문서 4.2 2단계): 회사 계정(OIDC) 로그인
  SSO_NOT_ADMIN(403, "관리자 그룹에 속한 회사 계정이 아니에요."),
  SSO_EMAIL_UNVERIFIED(403, "회사 계정의 이메일을 확인할 수 없어 관리자 계정과 연결하지 못했어요."),
  SSO_STATE_INVALID(400, "로그인 시간이 지났거나 다른 창에서 시작한 로그인이에요. 다시 시도해 주세요."),
  SSO_TOKEN_INVALID(401, "회사 계정 로그인 결과를 확인하지 못했어요. 다시 시도해 주세요."),
  SSO_IDP_ERROR(400, "회사 계정 로그인이 취소됐거나 실패했어요.", "reason"),
  SSO_PROVIDER_UNAVAILABLE(502, "회사 계정 로그인 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요."),
  SSO_EXCHANGE_INVALID(401, "로그인 시간이 지났어요. 다시 로그인해 주세요."),
  NOT_FOUND(404, "찾을 수 없어요."),
  METHOD_NOT_ALLOWED(405, "지원하지 않는 요청이에요."),
  EMAIL_TAKEN(409, "이미 가입된 이메일이에요."),
  CODE_EXISTS(409, "이미 있는 코드예요."),
  CODE_IN_USE(409, "사용 중인 코드라 삭제할 수 없어요. 대신 비활성화해 주세요.", "usages"),
  PROVIDER_EXISTS(409, "이미 있는 공급자 ID예요."),
  SECRET_KEY_MISSING(409, "서버에 WEARLY_SECRET_KEY가 없어 웹에서 API 키를 넣을 수 없어요."),
  API_KEY_INVALID(400, "API 키를 확인하지 못했어요.", "reason"),
  WARDROBE_INSUFFICIENT(400, "코디를 만들 옷이 모자라요.", "missing", "alternatives"),
  IMAGE_INVALID(400, "사진을 처리할 수 없어요. JPEG·PNG 사진만 올릴 수 있어요.", "reason"),
  IMAGE_NOT_UPLOADED(400, "사진 올리기가 끝나지 않았어요. 다시 올려 주세요."),
  IMAGE_IN_USE(409, "다른 옷에 쓰고 있는 사진이에요."),
  IMAGE_UPLOAD_LIMIT(429, "오늘 올릴 수 있는 사진 수를 모두 썼어요."),
  IMAGE_TOO_LARGE(413, "사진은 10MB까지 올릴 수 있어요."),
  UNSUPPORTED_MEDIA_TYPE(415, "지원하지 않는 형식이에요."),
  AI_LIMIT_EXCEEDED(429, "오늘 AI 분석 횟수를 모두 썼어요.", "feature"),
  AI_UNAVAILABLE(502, "AI 분석을 지금 할 수 없어요. 잠시 후 다시 시도해 주세요.", "feature"),
  WEATHER_UNAVAILABLE(503, "날씨 정보를 아직 받지 못했어요. 잠시 후 다시 시도해 주세요.", "kind"),
  WEATHER_REFRESH_RUNNING(409, "날씨를 이미 받고 있어요. 끝난 뒤 다시 시도해 주세요."),
  WEATHER_LIMIT(429, "오늘 다른 장소 날씨를 볼 수 있는 횟수를 모두 썼어요. 내일 다시 시도해 주세요.", "kind"),
  WEATHER_NO_PROVIDER(
      409, "쓸 수 있는 날씨 공급자가 없어요. 공급자 사용 여부·키·차단 상태·월 한도를 확인해 주세요.", "reason", "keyName"),
  PLAN_LIMIT(409, "예정은 {max}개까지 저장할 수 있어요. 지난 예정을 지워 주세요.", "max"),
  // 옷장 도우미 연결 (26 문서)
  PAIRING_CODE_INVALID(404, "코드가 맞지 않거나 시간이 지났어요. 코드를 다시 받아 주세요."),
  PAIRING_SELF(400, "내가 만든 코드는 내가 입력할 수 없어요."),
  PAIRING_EXISTS(409, "이미 연결됐거나 승인을 기다리는 상대예요."),
  PAIRING_LIMIT(409, "연결할 수 있는 인원을 모두 채웠어요.", "side", "max"),
  PAIRING_TOO_MANY_ATTEMPTS(429, "코드를 여러 번 입력했어요. 잠시 후 다시 시도해 주세요."),
  INTERNAL_ERROR(500, "일시적인 오류가 발생했어요."),
  NOT_ACCEPTABLE(406, "지원하지 않는 응답 형식이에요."),
  CONFLICT(409, "다른 요청과 겹쳐 처리하지 못했어요. 다시 시도해 주세요."),
  SERVICE_UNAVAILABLE(503, "서비스를 잠시 쓸 수 없어요. 잠시 후 다시 시도해 주세요.");

  private final int status;
  private final String description;
  private final List<String> params;

  ErrorCode(int status, String description, String... params) {
    this.status = status;
    this.description = description;
    this.params = List.of(params);
  }

  public int status() {
    return status;
  }

  /** 로그·문서용 설명 (응답에는 쓰지 않는다) */
  public String description() {
    return description;
  }

  /** 이 코드가 쓸 수 있는 params 이름 */
  public List<String> params() {
    return params;
  }
}
