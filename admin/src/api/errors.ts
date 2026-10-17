import type { IApiErrorBody, IFieldError } from './types';

/** 서버 오류 코드 (05 4장, 07 2·4장) + 이 사이트가 만드는 코드 */
export type TApiErrorCode =
  | 'VALIDATION_FAILED'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'TOKEN_EXPIRED'
  | 'TOKEN_INVALID'
  | 'INVALID_CREDENTIALS'
  | 'ACCOUNT_SUSPENDED'
  | 'REFRESH_INVALID'
  | 'REFRESH_REUSED'
  | 'NOT_FOUND'
  | 'CODE_EXISTS'
  | 'CODE_IN_USE'
  | 'IMAGE_TOO_LARGE'
  | 'AI_UNAVAILABLE'
  /** 07 6장: 비밀번호 재확인 실패 (403, 로그인 상태는 그대로) */
  | 'REAUTH_FAILED'
  /** 07 6장: 저장 전 유효성 확인에서 키가 거절됨 (400) */
  | 'API_KEY_INVALID'
  /** 07 6장: 서버에 WEARLY_SECRET_KEY가 없어 웹 키를 저장할 수 없음 (409) */
  | 'SECRET_KEY_MISSING'
  /** 서버에 닿지 못함 (연결 실패·시간 초과) */
  | 'NETWORK'
  /** 응답이 계약 형식이 아님 */
  | 'HTTP_ERROR'
  /** 로그인한 계정이 관리자가 아님 (이 사이트가 판단) */
  | 'NOT_ADMIN';

export class ApiError extends Error {
  /** HTTP 상태. 네트워크 실패는 0 */
  readonly status: number;
  readonly code: TApiErrorCode | (string & {});
  readonly fieldErrors: IFieldError[];

  constructor(status: number, code: string, message: string, fieldErrors: IFieldError[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    Object.setPrototypeOf(this, ApiError.prototype);
  }
}

export const isApiError = (e: unknown): e is ApiError => {
  return e instanceof ApiError;
};

export const networkError = (cause: unknown, timedOut = false): ApiError => {
  const err = new ApiError(0, 'NETWORK', timedOut ? '서버 응답이 너무 늦어요.' : '서버에 연결할 수 없어요. 백엔드가 켜져 있는지 확인해 주세요.');
  (err as { cause?: unknown }).cause = cause;
  return err;
};

const FALLBACK_CODE: Record<number, TApiErrorCode> = {
  401: 'UNAUTHORIZED',
  403: 'FORBIDDEN',
  404: 'NOT_FOUND',
  413: 'IMAGE_TOO_LARGE',
};

const FALLBACK_MESSAGE: Record<number, string> = {
  400: '요청 내용을 확인해 주세요.',
  401: '로그인이 필요해요.',
  403: '권한이 없어요.',
  404: '찾을 수 없어요.',
  409: '다른 데이터와 충돌해요.',
  413: '사진이 너무 커요.',
};

const fallbackMessage = (status: number): string => {
  return FALLBACK_MESSAGE[status] ?? (status >= 500 ? `서버 오류가 났어요. (HTTP ${status})` : `요청을 처리하지 못했어요. (HTTP ${status})`);
};

const isErrorBody = (v: unknown): v is IApiErrorBody => {
  return typeof v === 'object' && v !== null && typeof (v as IApiErrorBody).code === 'string';
};

/** 오류 응답 본문(JSON이 아닐 수도 있음)을 ApiError로 바꾼다 */
export const errorFromResponse = (status: number, rawText: string): ApiError => {
  let parsed: unknown;
  try {
    parsed = rawText ? JSON.parse(rawText) : undefined;
  } catch {
    parsed = undefined;
  }
  if (isErrorBody(parsed)) {
    const fieldErrors = Array.isArray(parsed.fieldErrors)
      ? parsed.fieldErrors.filter((f): f is IFieldError => typeof f?.field === 'string' && typeof f?.message === 'string')
      : [];
    return new ApiError(status, parsed.code, parsed.message || fallbackMessage(status), fieldErrors);
  }
  return new ApiError(status, FALLBACK_CODE[status] ?? 'HTTP_ERROR', fallbackMessage(status));
};

/** 화면에 보여 줄 한 줄 오류 문구 */
export const errorMessage = (e: unknown): string => {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return '알 수 없는 오류가 났어요.';
};
