import type { IApiErrorBody, IFieldError, TErrorParams } from '@/api/types';

import { errorText } from '@/lib/errorText';

/**
 * 서버 오류 코드 (기준: backend/src/main/resources/error-codes.json) 가운데 이 사이트가 코드로 분기하는 것 + 이 사이트가 만드는 코드.
 * 화면 문구는 lib/errorText.ts가 code·params로 정한다 (서버 응답에 문장이 없다).
 */
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
  /** params.usages: [{ kind: ITEM|PROFILE|SUB_TYPE|PLAN, count }] */
  | 'CODE_IN_USE'
  | 'PROVIDER_EXISTS'
  | 'IMAGE_TOO_LARGE'
  /** params.feature: ITEM_ANALYZE · OUTFIT_RECOMMEND */
  | 'AI_UNAVAILABLE'
  /** 07 6장: 비밀번호 재확인 실패 (403, 로그인 상태는 그대로) */
  | 'REAUTH_FAILED'
  /** 07 6장: 저장 전 유효성 확인에서 키가 거절됨 (400). params.reason: API_401 · TIMEOUT … */
  | 'API_KEY_INVALID'
  /** 07 6장: 서버에 WEARLY_SECRET_KEY가 없어 웹 키를 저장할 수 없음 (409) */
  | 'SECRET_KEY_MISSING'
  /** doc/17 6장: 날씨를 이미 받는 중 (409) */
  | 'WEATHER_REFRESH_RUNNING'
  /** doc/17 6장: 쓸 수 있는 날씨 공급자가 없음 (409). 시험 호출이면 params.reason KEY_MISSING(keyName) · NOT_IMPLEMENTED */
  | 'WEATHER_NO_PROVIDER'
  /** doc/22: 마지막 관리자는 해제할 수 없음 (409) */
  | 'LAST_ADMIN'
  /** doc/22: 2단계 인증을 거치지 않은 세션으로 관리자 API를 부름 (403) → 로그인 상태를 지운다 */
  | 'MFA_REQUIRED'
  /** doc/22: 대기 토큰 만료·무효 (401) → 로그인 첫 화면으로 */
  | 'MFA_TOKEN_INVALID'
  /** doc/22: 코드가 맞지 않음 (400). params.remainingAttempts */
  | 'MFA_CODE_INVALID'
  /** doc/22: 여러 번 틀려 잠김 (429). params.retryAfterSeconds */
  | 'MFA_LOCKED'
  | 'MFA_SETUP_REQUIRED'
  | 'MFA_ALREADY_ENROLLED'
  | 'MFA_NOT_ENROLLED'
  /** doc/22 4.2 회사 계정(SSO): 관리자 그룹이 아님 (403) */
  | 'SSO_NOT_ADMIN'
  /** 처음 로그인인데 IdP 이메일이 검증되지 않음 (403) */
  | 'SSO_EMAIL_UNVERIFIED'
  /** state 만료·재사용·다른 브라우저 (400) */
  | 'SSO_STATE_INVALID'
  /** 토큰 교환 거절·ID 토큰 확인 실패 (401) */
  | 'SSO_TOKEN_INVALID'
  /** IdP가 오류로 돌려보냄 (400). params.reason: access_denied 등 */
  | 'SSO_IDP_ERROR'
  /** IdP에 닿지 못함 (502) */
  | 'SSO_PROVIDER_UNAVAILABLE'
  /** 교환 코드 만료·재사용 (401) */
  | 'SSO_EXCHANGE_INVALID'
  | 'INTERNAL_ERROR'
  | 'SERVICE_UNAVAILABLE'
  /** 서버에 닿지 못함 (연결 실패·시간 초과 — params.reason TIMEOUT) */
  | 'NETWORK'
  /** 응답이 계약 형식이 아님 (params.reason PARSE: 성공 응답을 읽지 못함) */
  | 'HTTP_ERROR'
  /** 로그인한 계정이 관리자가 아님 (이 사이트가 판단) */
  | 'NOT_ADMIN';

export interface IApiErrorInit {
  params?: TErrorParams;
  fieldErrors?: IFieldError[];
  /** 서버 요청 추적 ID (본문 traceId 또는 응답 헤더 X-Request-Id) */
  traceId?: string | null;
}

export class ApiError extends Error {
  /** HTTP 상태. 네트워크 실패는 0 */
  readonly status: number;
  readonly code: TApiErrorCode | (string & {});
  /** 코드별 추가 값 (없으면 {}) */
  readonly params: TErrorParams;
  readonly fieldErrors: IFieldError[];
  readonly traceId: string | null;

  constructor(status: number, code: string, init: IApiErrorInit = {}) {
    // Error.message는 코드(디버깅용). 화면 문구는 errorMessage(e) / lib/errorText.ts
    super(code);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.params = init.params ?? {};
    this.fieldErrors = init.fieldErrors ?? [];
    this.traceId = init.traceId ?? null;
    Object.setPrototypeOf(this, ApiError.prototype);
  }
}

export const isApiError = (e: unknown): e is ApiError => {
  return e instanceof ApiError;
};

export const networkError = (cause: unknown, timedOut = false): ApiError => {
  const err = new ApiError(0, 'NETWORK', { params: timedOut ? { reason: 'TIMEOUT' } : {} });
  (err as { cause?: unknown }).cause = cause;
  return err;
};

/** 본문이 계약 형식이 아닐 때 상태로 정하는 코드 */
const FALLBACK_CODE: Record<number, TApiErrorCode> = {
  401: 'UNAUTHORIZED',
  403: 'FORBIDDEN',
  404: 'NOT_FOUND',
  413: 'IMAGE_TOO_LARGE',
  500: 'INTERNAL_ERROR',
  503: 'SERVICE_UNAVAILABLE',
};

const isErrorBody = (v: unknown): v is IApiErrorBody => {
  return typeof v === 'object' && v !== null && typeof (v as IApiErrorBody).code === 'string';
};

const isParams = (v: unknown): v is TErrorParams => typeof v === 'object' && v !== null && !Array.isArray(v);

const toFieldErrors = (raw: unknown): IFieldError[] => {
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((f): f is Record<string, unknown> => isParams(f) && typeof f.field === 'string')
    .map((f) => ({
      field: f.field as string,
      code: typeof f.code === 'string' ? f.code : '',
      params: isParams(f.params) ? f.params : {},
    }));
};

/**
 * 오류 응답 본문(JSON이 아닐 수도 있음)을 ApiError로 바꾼다.
 * requestId는 응답 헤더 X-Request-Id (본문에 traceId가 없을 때 — nginx 502 등).
 */
export const errorFromResponse = (status: number, rawText: string, requestId?: string | null): ApiError => {
  let parsed: unknown;
  try {
    parsed = rawText ? JSON.parse(rawText) : undefined;
  } catch {
    parsed = undefined;
  }
  if (isErrorBody(parsed)) {
    return new ApiError(status, parsed.code, {
      params: isParams(parsed.params) ? parsed.params : {},
      fieldErrors: toFieldErrors(parsed.fieldErrors),
      traceId: (typeof parsed.traceId === 'string' && parsed.traceId) || requestId || null,
    });
  }
  return new ApiError(status, FALLBACK_CODE[status] ?? 'HTTP_ERROR', { traceId: requestId || null });
};

/** 화면에 보여 줄 한 줄 오류 문구 (code·params → lib/errorText.ts) */
export const errorMessage = (e: unknown): string => {
  if (e instanceof ApiError) return errorText(e);
  if (e instanceof Error && e.message) return e.message;
  return '알 수 없는 오류가 났어요.';
};
