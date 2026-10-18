import type { ApiErrorBody, FieldError } from './types';

/** 서버 오류 코드 (계약 4장) + 앱이 만드는 코드 */
export type ApiErrorCode =
  | 'VALIDATION_FAILED'
  | 'UNAUTHORIZED'
  | 'TOKEN_EXPIRED'
  | 'TOKEN_INVALID'
  | 'INVALID_CREDENTIALS'
  | 'REFRESH_INVALID'
  | 'REFRESH_REUSED'
  | 'NOT_FOUND'
  | 'EMAIL_TAKEN'
  | 'IMAGE_TOO_LARGE'
  | 'AI_LIMIT_EXCEEDED'
  | 'AI_UNAVAILABLE'
  /** 추천 가능한 옷이 모자라 코디를 만들 수 없음. params.missing·params.alternatives에 모자란 칸 (doc/09 3장) */
  | 'WARDROBE_INSUFFICIENT'
  /** 사진 형식·크기·디코딩 오류 (complete). params.reason: FORMAT·TOO_LARGE·PIXELS·DECODE */
  | 'IMAGE_INVALID'
  /** complete인데 저장소에 올린 파일이 없음 */
  | 'IMAGE_NOT_UPLOADED'
  /** 다른 아이템에 이미 붙은 이미지 */
  | 'IMAGE_IN_USE'
  /** 하루 사진 업로드 한도 초과 */
  | 'IMAGE_UPLOAD_LIMIT'
  /** 서명 주소로 올리기(PUT) 실패. 오브젝트 스토리지 응답이라 서버 오류 형식이 아니다 (앱이 만든다) */
  | 'UPLOAD_FAILED'
  /** 서버가 그 지역 날씨를 아직 한 번도 받지 못함 (doc/17 5장). 장소 검색을 지금 할 수 없으면 params.kind: SEARCH */
  | 'WEATHER_UNAVAILABLE'
  /** 장소 날씨 하루 한도 초과 (doc/19). params.kind: ON_DEMAND(좌표 날씨, 캐시·평년값도 없음) · SEARCH(장소 검색) */
  | 'WEATHER_LIMIT'
  /** 예정 코디 저장 개수 상한 초과 (409, params.max = 500). POST /plans·/plans/bulk */
  | 'PLAN_LIMIT'
  /** 서버에 닿지 못함 (연결 실패·시간 초과) */
  | 'NETWORK'
  /** 응답이 계약 형식이 아님 (JSON이 아닌 오류 본문 등) */
  | 'HTTP_ERROR';

export class ApiError extends Error {
  /** HTTP 상태. 네트워크 실패는 0 */
  readonly status: number;
  readonly code: ApiErrorCode | (string & {});
  readonly fieldErrors: FieldError[];
  /** 서버 오류 코드의 상세 값 (없으면 빈 객체) */
  readonly params: Record<string, unknown>;
  /** 서버 요청 추적 ID (본문 traceId = 응답 헤더 X-Request-Id). 서버 응답이 아니면 없다 */
  readonly traceId?: string;

  constructor(
    status: number,
    code: string,
    message: string,
    fieldErrors: FieldError[] = [],
    params: Record<string, unknown> = {},
    traceId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.params = params;
    this.traceId = traceId;
    // 트랜스파일 환경에 따라 Error 상속의 프로토타입이 끊기는 경우를 막는다
    Object.setPrototypeOf(this, ApiError.prototype);
  }

  get isNetwork(): boolean {
    return this.code === 'NETWORK';
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError || (typeof e === 'object' && e !== null && (e as { name?: unknown }).name === 'ApiError');
}

export function networkError(cause?: unknown, timedOut = false): ApiError {
  const err = new ApiError(0, 'NETWORK', timedOut ? '서버 응답이 너무 늦어요.' : '서버에 연결할 수 없어요.');
  if (cause !== undefined) (err as { cause?: unknown }).cause = cause;
  return err;
}

/** 본문이 계약 형식이 아닐 때 HTTP 상태만으로 정할 수 있는 코드 */
const FALLBACK_CODE: Record<number, ApiErrorCode> = {
  401: 'UNAUTHORIZED',
  404: 'NOT_FOUND',
  413: 'IMAGE_TOO_LARGE',
  429: 'AI_LIMIT_EXCEEDED',
};

const FALLBACK_MESSAGE: Record<number, string> = {
  400: '요청 내용을 확인해 주세요.',
  401: '로그인이 필요해요.',
  403: '권한이 없어요.',
  404: '찾을 수 없어요.',
  413: '사진이 너무 커요.',
  429: '요청이 너무 많아요. 잠시 후 다시 시도해 주세요.',
};

function isErrorBody(v: unknown): v is ApiErrorBody {
  return typeof v === 'object' && v !== null && typeof (v as ApiErrorBody).code === 'string';
}

/** 오류 응답 본문(JSON이 아닐 수도 있음)을 ApiError로 바꾼다 */
export function errorFromResponse(status: number, rawText: string): ApiError {
  let parsed: unknown = undefined;
  if (rawText) {
    try {
      parsed = JSON.parse(rawText);
    } catch {
      parsed = undefined;
    }
  }
  if (isErrorBody(parsed)) {
    // 서버는 문장(message)을 보내지 않는다: message 자리에는 code를 담아 둔다 (코드별 화면 문구 표는 따로 정한다)
    const fieldErrors: FieldError[] = [];
    for (const f of Array.isArray(parsed.fieldErrors) ? parsed.fieldErrors : []) {
      if (typeof f?.field !== 'string') continue;
      const code = typeof f.code === 'string' && f.code ? f.code : undefined;
      const message = typeof f.message === 'string' && f.message ? f.message : code;
      if (!message) continue;
      fieldErrors.push({ field: f.field, code: code ?? 'INVALID', ...(isRecord(f.params) ? { params: f.params } : {}), message });
    }
    const message = typeof parsed.message === 'string' && parsed.message ? parsed.message : parsed.code;
    const params = isRecord(parsed.params) ? parsed.params : {};
    const traceId = typeof parsed.traceId === 'string' && parsed.traceId ? parsed.traceId : undefined;
    return new ApiError(status, parsed.code, message, fieldErrors, params, traceId);
  }
  return new ApiError(status, FALLBACK_CODE[status] ?? 'HTTP_ERROR', fallbackMessage(status));
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function fallbackMessage(status: number): string {
  return FALLBACK_MESSAGE[status] ?? (status >= 500 ? `서버 오류가 났어요. (HTTP ${status})` : `요청을 처리하지 못했어요. (HTTP ${status})`);
}
