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
  /** 서버에 닿지 못함 (연결 실패·시간 초과) */
  | 'NETWORK'
  /** 응답이 계약 형식이 아님 (JSON이 아닌 오류 본문 등) */
  | 'HTTP_ERROR';

export class ApiError extends Error {
  /** HTTP 상태. 네트워크 실패는 0 */
  readonly status: number;
  readonly code: ApiErrorCode | (string & {});
  readonly fieldErrors: FieldError[];

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
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
    const fieldErrors = Array.isArray(parsed.fieldErrors)
      ? parsed.fieldErrors.filter((f): f is FieldError => typeof f?.field === 'string' && typeof f?.message === 'string')
      : [];
    return new ApiError(status, parsed.code, typeof parsed.message === 'string' && parsed.message ? parsed.message : fallbackMessage(status), fieldErrors);
  }
  return new ApiError(status, FALLBACK_CODE[status] ?? 'HTTP_ERROR', fallbackMessage(status));
}

function fallbackMessage(status: number): string {
  return FALLBACK_MESSAGE[status] ?? (status >= 500 ? `서버 오류가 났어요. (HTTP ${status})` : `요청을 처리하지 못했어요. (HTTP ${status})`);
}
