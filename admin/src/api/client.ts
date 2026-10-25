// Wearly 관리자 API 클라이언트.
// 토큰 규칙은 doc/05 8장과 같다: TOKEN_EXPIRED면 Refresh로 한 번 갱신하고(single-flight) 원래 요청을 한 번 다시 보낸다.
// 갱신이 실패하면 저장소를 비우고 onAuthLost()를 부른다 → 로그인 화면으로 간다.

import { ApiError, errorFromResponse, networkError } from '@/api/errors';
import { tokenStore } from '@/api/tokenStore';
import type { ITokens } from '@/api/tokenStore';
import type {
  IAdminCodesResponse,
  IAdminSession,
  IAdminUser,
  IAiLogQuery,
  IAiLogRow,
  IAiProvider,
  IApiKeyDeleteRequest,
  IApiKeySetRequest,
  IAppTheme,
  IAppThemeUpdate,
  IAuditLog,
  IAuditQuery,
  IAuthEvent,
  IAuthEventQuery,
  IAuthEventRow,
  IAuthResponse,
  ICodeCreate,
  ICodePatch,
  IDashboard,
  IInviteShareQuery,
  IInviteShareRow,
  IInviteSignupQuery,
  IInviteSignupRow,
  ILoginRequest,
  IMfaEnableResponse,
  IMfaPendingResponse,
  IMfaResetRequest,
  IMfaSetupResponse,
  IMfaVerifyRequest,
  IMfaVerifyResponse,
  IMyMfaStatus,
  IPage,
  IPageQuery,
  IPairingAction,
  IPairingQuery,
  IPairingRow,
  IPasswordChangeRequest,
  IProviderCreate,
  IProviderModel,
  IProviderModelList,
  IProviderOptionSpec,
  IProviderTestResult,
  IProviderTypeSpec,
  ISessionQuery,
  ISsoProvider,
  ISsoProvidersResponse,
  IUserDto,
  IUserPatch,
  IUserQuery,
  IUserSessions,
  IWeatherCall,
  IWeatherCallQuery,
  IWeatherCallSummary,
  IWeatherProvider,
  IWeatherProviderPatch,
  IWeatherRun,
  IWeatherRunDetail,
  IWeatherRunQuery,
  IWeatherSchedule,
  IWeatherSnapshot,
  IWeatherTestResult,
  IWithdrawalQuery,
  IWithdrawalRow,
  IWithdrawalSummary,
  TAiLogDetail,
  TKeySource,
  TLoginResponse,
  TLoginResult,
  TProviderPatch,
  TWeatherSettingUpdate,
} from '@/api/types';

type TMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type TQuery = Record<string, string | number | undefined | null>;

interface IRequestOptions {
  json?: unknown;
  /** multipart 본문. 재시도 때 다시 만들 수 있도록 함수로 받는다 */
  form?: () => FormData;
  query?: TQuery;
  /** Authorization 헤더를 붙이고 TOKEN_EXPIRED 시 갱신·재시도한다 */
  auth?: boolean;
  timeoutMs?: number;
}

const API_PREFIX = '/api/v1';
/** 서버가 로그인 세션의 종류(APP·ADMIN·WEB)를 정하는 헤더 (doc/16 4장). 모든 요청에 붙인다 */
const CLIENT_HEADER = { 'X-Wearly-Client': 'admin' } as const;
/** 요청 추적 ID 헤더. 오류 본문 traceId와 같다 (CORS exposedHeaders에 있음) */
const TRACE_HEADER = 'X-Request-Id';
const DEFAULT_TIMEOUT = 15_000;
/** 시험 분석은 실제 AI를 부르므로 넉넉히 */
const TEST_TIMEOUT = 120_000;
/** 키 저장은 서버가 각 사 모델 목록 조회로 유효성을 확인할 수 있어 조금 넉넉히 */
const API_KEY_TIMEOUT = 30_000;

export const API_URL: string = (import.meta.env.VITE_API_URL as string | undefined)?.trim() || 'http://localhost:8080';

const joinBase = (baseUrl: string): string => {
  const trimmed = baseUrl.replace(/\/+$/, '');
  return trimmed.endsWith(API_PREFIX) ? trimmed : trimmed + API_PREFIX;
};

const buildQuery = (query?: TQuery): string => {
  if (!query) return '';
  const parts: string[] = [];
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null || v === '') continue;
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  }
  return parts.length ? `?${parts.join('&')}` : '';
};

const enc = encodeURIComponent;

// ───────── 응답 정규화 (계약이 자세히 정하지 않은 부분을 너그럽게 받는다) ─────────

/** 목록은 `{ items, page, size, total }`가 원칙이지만, 배열로 오면 한 페이지로 감싼다 */
const toPage = <T>(raw: unknown, q: IPageQuery, map: (row: Record<string, unknown>) => T = (r) => r as T): IPage<T> => {
  if (Array.isArray(raw)) {
    return { items: raw.map((r) => map(r as Record<string, unknown>)), page: 0, size: raw.length, total: raw.length };
  }
  const p = (raw ?? {}) as Partial<IPage<Record<string, unknown>>>;
  const items = Array.isArray(p.items) ? p.items.map(map) : [];
  return { items, page: p.page ?? q.page ?? 0, size: p.size ?? q.size ?? items.length, total: p.total ?? items.length };
};

const toArray = <T>(raw: unknown): T[] => {
  if (Array.isArray(raw)) return raw as T[];
  const items = (raw as { items?: unknown } | null)?.items;
  return Array.isArray(items) ? (items as T[]) : [];
};

const str = (v: unknown): string | null => (v === undefined || v === null ? null : String(v));
const num = (v: unknown): number | null =>
  typeof v === 'number' ? v : v === null || v === undefined || v === '' ? null : Number(v);

const toLogRow = (r: Record<string, unknown>): IAiLogRow => {
  return {
    id: String(r.id ?? r.logId ?? ''),
    createdAt: String(r.createdAt ?? ''),
    userEmail: str(r.userEmail ?? r.email),
    feature: String(r.feature ?? ''),
    providerId: String(r.providerId ?? r.provider ?? ''),
    model: str(r.model),
    status: String(r.status ?? ''),
    latencyMs: num(r.latencyMs),
    inputTokens: num(r.inputTokens),
    outputTokens: num(r.outputTokens),
    imageBytes: num(r.imageBytes),
    summary: str(r.summary ?? r.resultSummary),
    errorCode: str(r.errorCode),
  };
};

const KEY_SOURCES: readonly TKeySource[] = ['DB', 'ENV', 'NONE'];

/**
 * 공급자 행. 07 6장의 키 필드(keySource 등)가 없는 예전 서버면 keyConfigured로 출처를 추정한다
 * (웹 키 기능 전에는 서버 설정 키뿐이었으므로 있으면 'ENV', 없으면 'NONE').
 */
const toProvider = (r: Record<string, unknown>): IAiProvider => {
  const source = KEY_SOURCES.includes(r.keySource as TKeySource) ? (r.keySource as TKeySource) : null;
  const keyConfigured = typeof r.keyConfigured === 'boolean' ? r.keyConfigured : source ? source !== 'NONE' : false;
  const keySource: TKeySource = source ?? (keyConfigured ? 'ENV' : 'NONE');
  const isDb = keySource === 'DB';
  return {
    ...(r as unknown as IAiProvider),
    keyConfigured,
    keySource,
    // 마지막 4자만 보여 준다. 혹시 더 긴 값이 와도 4자로 자른다
    keyLast4: isDb ? (str(r.keyLast4)?.slice(-4) ?? null) : null,
    keyUpdatedAt: isDb ? str(r.keyUpdatedAt) : null,
    keyUpdatedBy: isDb ? str(r.keyUpdatedBy) : null,
    secretStoreEnabled: typeof r.secretStoreEnabled === 'boolean' ? r.secretStoreEnabled : null,
  };
};

/** 키 저장·삭제 응답(공급자 행). 본문이 비어 있으면 null — 화면은 목록을 다시 읽는다 */
const toProviderOrNull = (raw: unknown): IAiProvider | null => {
  return raw && typeof raw === 'object' && !Array.isArray(raw) ? toProvider(raw as Record<string, unknown>) : null;
};

const toAudit = (r: Record<string, unknown>): IAuditLog => {
  return {
    ...(r as unknown as IAuditLog),
    id: String(r.id ?? r.auditId ?? ''),
    targetId: str(r.targetId),
    adminUserId: str(r.adminUserId),
  };
};

/** 세션 한 줄 (doc/16 4장). id·사용자 정보가 다른 이름이나 중첩 객체(user)로 와도 받는다 */
const toSession = (r: Record<string, unknown>): IAdminSession => {
  const user = (r.user && typeof r.user === 'object' ? r.user : {}) as Record<string, unknown>;
  const role = str(r.role ?? user.role);
  return {
    sessionId: String(r.sessionId ?? r.id ?? ''),
    userId: String(r.userId ?? user.id ?? ''),
    email: str(r.email ?? user.email),
    name: String(r.name ?? user.name ?? ''),
    role: role === 'ADMIN' || role === 'USER' ? role : null,
    client: String(r.client ?? '').toUpperCase(),
    userAgent: str(r.userAgent),
    ip: str(r.ip),
    createdAt: str(r.createdAt ?? r.loginAt),
    lastRefreshAt: str(r.lastRefreshAt),
    lastSeenAt: str(r.lastSeenAt),
    current: r.current === true,
  };
};

const toAuthEvent = (r: Record<string, unknown>): IAuthEvent => {
  return {
    type: String(r.type ?? ''),
    sessionId: str(r.sessionId),
    client: str(r.client)?.toUpperCase() ?? null,
    ip: str(r.ip),
    userAgent: str(r.userAgent),
    createdAt: String(r.createdAt ?? ''),
    ssoProvider: str(r.ssoProvider),
    ssoAmr: str(r.ssoAmr),
  };
};

/** 관리자 로그인 기록 한 줄 (doc/22). 사용자 정보가 중첩 객체(user)로 와도 받는다 */
const toAuthEventRow = (r: Record<string, unknown>): IAuthEventRow => {
  const user = (r.user && typeof r.user === 'object' ? r.user : {}) as Record<string, unknown>;
  const role = str(r.role ?? user.role);
  return {
    ...toAuthEvent(r),
    id: String(r.id ?? ''),
    userId: String(r.userId ?? user.id ?? ''),
    email: str(r.email ?? user.email),
    name: str(r.name ?? user.name),
    role: role === 'ADMIN' || role === 'USER' ? role : null,
  };
};

/** 회원 행. mfaEnrolled·invitedCount가 없는 예전 서버면 false·0 */
const toAdminUser = (r: Record<string, unknown>): IAdminUser => {
  return {
    ...(r as unknown as IAdminUser),
    id: String(r.id ?? ''),
    mfaEnrolled: r.mfaEnrolled === true,
    invitedCount: typeof r.invitedCount === 'number' ? r.invitedCount : 0,
  };
};

/** 로그인 응답이 2단계 인증 대기인가 (토큰 대신 mfaToken이 온다) */
const isMfaPending = (raw: TLoginResponse): raw is IMfaPendingResponse => {
  return typeof (raw as Partial<IMfaPendingResponse>).mfaToken === 'string';
};

/** 회사 계정(SSO) 공급자 목록 응답 → 버튼 목록 (id 없는 항목은 뺀다) */
const toSsoProviders = (raw: unknown): ISsoProvider[] => {
  const list = (raw as Partial<ISsoProvidersResponse> | null)?.providers;
  if (!Array.isArray(list)) return [];
  return list
    .map((p) => ({ id: String(p?.id ?? ''), displayName: String(p?.displayName ?? p?.id ?? '') }))
    .filter((p) => p.id);
};

/** 내 2단계 인증 상태 */
const toMyMfaStatus = (raw: unknown): IMyMfaStatus => {
  const r = (raw ?? {}) as Record<string, unknown>;
  return {
    enrolled: r.enrolled === true,
    enrolledAt: str(r.enrolledAt),
    recoveryCodesLeft: num(r.recoveryCodesLeft),
    required: r.required === true,
  };
};

/** 회원 한 명의 세션 + 최근 이력. 배열만 오면 세션 목록으로 본다 */
const toUserSessions = (raw: unknown): IUserSessions => {
  if (Array.isArray(raw)) return { sessions: toArray<Record<string, unknown>>(raw).map(toSession), events: [] };
  const r = (raw ?? {}) as Record<string, unknown>;
  return {
    sessions: toArray<Record<string, unknown>>(r.sessions).map(toSession),
    events: toArray<Record<string, unknown>>(r.events ?? r.history).map(toAuthEvent),
  };
};

/** 앱 테마 설정. updatedBy가 객체(관리자 정보)로 와도 이메일 문자열로 바꾼다 */
const toAppTheme = (raw: unknown): IAppTheme => {
  const r = (raw ?? {}) as Record<string, unknown>;
  const by = r.updatedBy;
  const byObj = by && typeof by === 'object' ? (by as Record<string, unknown>) : null;
  return {
    themeId: String(r.themeId ?? ''),
    seasonMode: str(r.seasonMode),
    updatedAt: str(r.updatedAt),
    updatedBy: byObj ? str(byObj.email ?? byObj.name) : str(by),
  };
};

/** 모델 한 줄. 화면 이름이 없으면 ID */
const toModel = (r: Record<string, unknown>): IProviderModel => {
  const id = String(r.id ?? '');
  return {
    id,
    displayName: str(r.displayName)?.trim() || id,
    createdAt: str(r.createdAt),
    recommended: r.recommended === true,
  };
};

const toModels = (raw: unknown): IProviderModel[] => {
  return toArray<Record<string, unknown>>(raw)
    .map(toModel)
    .filter((m) => m.id);
};

/** 모델 목록 응답. 모르는 source는 내장 목록(CATALOG)으로 본다 */
const toModelList = (raw: unknown): IProviderModelList => {
  const r = (raw ?? {}) as Record<string, unknown>;
  const err = r.error && typeof r.error === 'object' ? (r.error as Record<string, unknown>) : null;
  return {
    source: r.source === 'LIVE' ? 'LIVE' : 'CATALOG',
    fetchedAt: str(r.fetchedAt),
    models: toModels(r.models),
    error: err ? { code: String(err.code ?? ''), message: String(err.message ?? '') } : null,
  };
};

/** `/admin/ai/types`: 배열 또는 `{ CLAUDE: {...} }` 객체, options는 배열 또는 `{ key: 설명 }` 객체 모두 받는다 */
const toTypeSpecs = (raw: unknown): IProviderTypeSpec[] => {
  // 백엔드 실제 형식: { types: [{ type, description, options: [{ name, kind, description, defaultValue, min, max, values }] }],
  //                   baseUrlAllowed, mockMode }. baseUrlAllowed가 false면 baseUrl 입력칸을 숨긴다(보내면 400).
  const wrapped = raw as { types?: unknown; baseUrlAllowed?: unknown } | null;
  if (wrapped && !Array.isArray(wrapped) && Array.isArray(wrapped.types)) {
    const specs = toTypeSpecs(wrapped.types);
    if (wrapped.baseUrlAllowed === false)
      for (const t of specs) t.options = t.options.filter((o) => o.key !== 'baseUrl');
    return specs;
  }
  const list: Record<string, unknown>[] = Array.isArray(raw)
    ? (raw as Record<string, unknown>[])
    : Array.isArray((raw as { items?: unknown })?.items)
      ? (raw as { items: Record<string, unknown>[] }).items
      : Object.entries((raw ?? {}) as Record<string, unknown>).map(([type, v]) => ({ type, ...(v as object) }));
  return list.map((t) => {
    const rawOpts = t.options;
    let options: IProviderOptionSpec[] = [];
    if (Array.isArray(rawOpts)) {
      options = rawOpts.map((o) => {
        const obj = (typeof o === 'string' ? { key: o } : o) as Record<string, unknown>;
        return {
          ...(obj as object),
          key: String(obj.key ?? obj.name ?? ''),
          type: (obj.type ?? obj.kind) as string | undefined,
          default: obj.default ?? obj.defaultValue,
        } as IProviderOptionSpec;
      });
    } else if (rawOpts && typeof rawOpts === 'object') {
      options = Object.entries(rawOpts as Record<string, unknown>).map(([key, v]) =>
        typeof v === 'string' ? { key, description: v } : ({ ...(v as object), key } as IProviderOptionSpec),
      );
    }
    return {
      type: String(t.type ?? ''),
      label: str(t.label ?? t.displayName) ?? undefined,
      description: str(t.description) ?? undefined,
      defaultApiKeyEnv: str(t.defaultApiKeyEnv ?? t.apiKeyEnv) ?? undefined,
      options: options.filter((o) => o.key),
      // 예전 서버면 models가 없다 → 모델 칸은 직접 입력만
      models: Array.isArray(t.models) ? toModels(t.models) : undefined,
    };
  });
};

// ───────── 클라이언트 ─────────

export interface IAdminApiOptions {
  baseUrl: string;
  /** 갱신 실패 등으로 로그인 상태를 잃었을 때. 저장소는 이미 비워진 뒤에 불린다 */
  onAuthLost?: (reason: ApiError) => void;
}

export const createAdminApi = (options: IAdminApiOptions) => {
  const base = joinBase(options.baseUrl);
  let refreshing: Promise<ITokens> | null = null;

  const send = async (
    method: TMethod,
    path: string,
    opts: IRequestOptions,
    accessToken?: string,
  ): Promise<Response> => {
    const headers: Record<string, string> = { Accept: 'application/json', ...CLIENT_HEADER };
    let body: BodyInit | undefined;
    if (opts.form) {
      body = opts.form();
    } else if (opts.json !== undefined) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(opts.json);
    }
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

    const timeout = opts.timeoutMs ?? DEFAULT_TIMEOUT;
    const controller = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, timeout);
    try {
      return await fetch(base + path + buildQuery(opts.query), { method, headers, body, signal: controller.signal });
    } catch (e) {
      throw networkError(e, timedOut);
    } finally {
      clearTimeout(timer);
    }
  };

  const readBody = async <T>(res: Response): Promise<T> => {
    let text = '';
    try {
      text = await res.text();
    } catch (e) {
      throw networkError(e);
    }
    if (!res.ok) throw errorFromResponse(res.status, text, res.headers.get(TRACE_HEADER));
    if (res.status === 204 || !text) return undefined as T;
    try {
      return JSON.parse(text) as T;
    } catch {
      throw new ApiError(res.status, 'HTTP_ERROR', {
        params: { reason: 'PARSE' },
        traceId: res.headers.get(TRACE_HEADER),
      });
    }
  };

  const lose = (reason: ApiError): void => {
    tokenStore.clear();
    options.onAuthLost?.(reason);
  };

  /** 진행 중인 갱신은 하나만 (single-flight) */
  const refreshTokens = (): Promise<ITokens> => {
    if (!refreshing) {
      refreshing = (async () => {
        const current = tokenStore.get();
        try {
          if (!current?.refreshToken) throw new ApiError(401, 'REFRESH_INVALID');
          const auth = await readBody<IAuthResponse>(
            await send('POST', '/auth/refresh', { json: { refreshToken: current.refreshToken } }),
          );
          if (auth.user?.role && auth.user.role !== 'ADMIN') {
            throw new ApiError(403, 'NOT_ADMIN');
          }
          const next = { accessToken: auth.accessToken, refreshToken: auth.refreshToken };
          tokenStore.set(next);
          if (auth.user) tokenStore.setUser(auth.user);
          return next;
        } catch (e) {
          // 네트워크 실패는 Refresh가 아직 유효할 수 있으므로 로그인 상태를 지우지 않는다
          if (!(e instanceof ApiError && e.code === 'NETWORK')) {
            lose(e instanceof ApiError ? e : new ApiError(401, 'REFRESH_INVALID'));
          }
          throw e;
        }
      })().finally(() => {
        refreshing = null;
      });
    }
    return refreshing;
  };

  const request = async <T>(method: TMethod, path: string, opts: IRequestOptions = {}): Promise<T> => {
    if (!opts.auth) return readBody<T>(await send(method, path, opts));

    if (refreshing) await refreshing.catch(() => undefined);
    const used = tokenStore.get()?.accessToken;
    if (!used) {
      const err = new ApiError(401, 'UNAUTHORIZED');
      lose(err);
      throw err;
    }
    const first = await send(method, path, opts, used);
    if (first.status !== 401 && first.status !== 403) return readBody<T>(first);

    let original: ApiError;
    try {
      return await readBody<T>(first);
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
      original = e;
    }

    if (original.status === 403) {
      // 관리자 API의 403 FORBIDDEN = 이 계정이 더는 관리자가 아니다 (역할이 내려감)
      // 403 MFA_REQUIRED = 2단계 인증을 거치지 않은 세션 (doc/22) → 다시 로그인해 코드를 확인해야 한다
      // (REAUTH_FAILED 같은 다른 403은 로그인 상태를 그대로 둔다)
      if (original.code === 'FORBIDDEN' || original.code === 'MFA_REQUIRED') lose(original);
      throw original;
    }
    if (original.code !== 'TOKEN_EXPIRED') {
      // TOKEN_INVALID(정지·탈퇴·서명 오류), UNAUTHORIZED: 갱신해도 소용없다
      lose(original);
      throw original;
    }

    const latest = tokenStore.get()?.accessToken;
    let fresh: string;
    if (latest && latest !== used) {
      fresh = latest;
    } else {
      try {
        fresh = (await refreshTokens()).accessToken;
      } catch (e) {
        if (e instanceof ApiError && e.code === 'NETWORK') throw e;
        throw original;
      }
    }
    return readBody<T>(await send(method, path, opts, fresh));
  };

  const get = <T>(path: string, query?: TQuery) => request<T>('GET', path, { auth: true, query });

  /** 로그인 응답(토큰)을 저장한다. 관리자가 아니면 서버 토큰을 바로 폐기하고 NOT_ADMIN 오류를 던진다 */
  const acceptAuth = async (auth: IAuthResponse): Promise<void> => {
    if (auth.user?.role !== 'ADMIN') {
      // 관리자가 아닌 계정의 토큰은 저장하지 않고 서버에서도 폐기한다
      await request<void>('POST', '/auth/logout', { json: { refreshToken: auth.refreshToken } }).catch(() => undefined);
      throw new ApiError(403, 'NOT_ADMIN');
    }
    tokenStore.set({ accessToken: auth.accessToken, refreshToken: auth.refreshToken });
    tokenStore.setUser(auth.user);
  };

  /** 로그인·SSO 교환 응답 → 결과. 2단계 인증 대기면 토큰을 저장하지 않는다 */
  const toLoginResult = async (raw: TLoginResponse): Promise<TLoginResult> => {
    if (isMfaPending(raw)) {
      return {
        kind: 'mfa',
        mfaToken: raw.mfaToken,
        enrolled: raw.mfa?.enrolled === true,
        expiresIn: num(raw.mfaTokenExpiresIn) ?? 300,
      };
    }
    await acceptAuth(raw);
    return { kind: 'signedIn', auth: raw };
  };

  return {
    auth: {
      /**
       * 로그인. 2단계 인증이 필요한 관리자면 토큰 없이 대기 정보(kind 'mfa')를 돌려준다 — 토큰은 저장하지 않는다.
       * role이 ADMIN이 아니면 서버 토큰을 바로 폐기하고 NOT_ADMIN 오류를 던진다.
       * 오류: 401 INVALID_CREDENTIALS · 403 ACCOUNT_SUSPENDED · 429 MFA_LOCKED(retryAfterSeconds)
       */
      login: async (req: ILoginRequest): Promise<TLoginResult> => {
        return toLoginResult(await request<TLoginResponse>('POST', '/auth/login', { json: req }));
      },
      /** 회사 계정 버튼 (doc/22 4.2). SSO가 꺼져 있거나 예전 서버(404)면 빈 목록 */
      ssoProviders: async (): Promise<ISsoProvider[]> => {
        try {
          return toSsoProviders(await request<unknown>('GET', '/auth/sso/providers'));
        } catch (e) {
          if (e instanceof ApiError && e.status === 404) return [];
          throw e;
        }
      },
      /** 브라우저가 이동할 SSO 시작 주소. redirect = 이 사이트의 /sso/callback (서버 허용 목록과 같아야 한다) */
      ssoStartUrl: (providerId: string, redirect: string): string => {
        return `${base}/auth/sso/${enc(providerId)}/start${buildQuery({ redirect })}`;
      },
      /** IdP 로그아웃 주소. 끝나면 redirect(이 사이트의 /login)로 돌아온다 */
      ssoLogoutUrl: (providerId: string, redirect: string): string => {
        return `${base}/auth/sso/${enc(providerId)}/logout${buildQuery({ redirect })}`;
      },
      /**
       * 콜백의 한 번만 쓰는 교환 코드 → 토큰 (또는 2단계 인증 대기: 서버 wearly.sso.trust-idp-mfa=false일 때).
       * 오류: 401 SSO_EXCHANGE_INVALID · 403 SSO_NOT_ADMIN · 403 ACCOUNT_SUSPENDED · 404(SSO 꺼짐)
       */
      ssoExchange: async (code: string): Promise<TLoginResult> => {
        return toLoginResult(await request<TLoginResponse>('POST', '/auth/sso/exchange', { json: { code } }));
      },
      /**
       * TOTP 등록 시작 (대기 토큰, Authorization 없음). 다시 부르면 새 비밀이 나온다(앞의 것 무효).
       * 오류: 401 MFA_TOKEN_INVALID · 409 MFA_ALREADY_ENROLLED · 409 SECRET_KEY_MISSING · 429 MFA_LOCKED
       */
      mfaSetup: (mfaToken: string): Promise<IMfaSetupResponse> => {
        return request<IMfaSetupResponse>('POST', '/auth/mfa/setup', { json: { mfaToken } });
      },
      /**
       * 코드 확인 → 토큰 발급·저장. recoveryCodes는 처음 등록할 때만 온다.
       * 오류: 400 MFA_CODE_INVALID(remainingAttempts) · 401 MFA_TOKEN_INVALID · 409 MFA_SETUP_REQUIRED · 429 MFA_LOCKED
       */
      mfaVerify: async (req: IMfaVerifyRequest): Promise<IMfaVerifyResponse> => {
        const raw = await request<IMfaVerifyResponse>('POST', '/auth/mfa/verify', { json: req });
        await acceptAuth(raw);
        return {
          ...raw,
          recoveryCodes: Array.isArray(raw.recoveryCodes) ? raw.recoveryCodes.map(String) : null,
          recoveryCodesLeft: num(raw.recoveryCodesLeft),
        };
      },
      logout: async (): Promise<void> => {
        const tokens = tokenStore.get();
        try {
          if (tokens?.refreshToken)
            await request<void>('POST', '/auth/logout', { json: { refreshToken: tokens.refreshToken } });
        } catch {
          // 서버 호출이 실패해도 이 탭의 토큰은 지운다
        } finally {
          tokenStore.clear();
        }
      },
      me: (): Promise<IUserDto> => {
        return request<IUserDto>('GET', '/users/me', { auth: true });
      },
    },

    /** 내 관리자 계정 (doc/22). 비밀번호 변경·2단계 인증 초기화는 다른 기기의 내 세션을 끊고 지금 세션은 둔다 */
    me: {
      mfaStatus: async (): Promise<IMyMfaStatus> => {
        return toMyMfaStatus(await get<unknown>('/admin/me/mfa'));
      },
      /** 204. 오류: 400 VALIDATION_FAILED(currentPassword·newPassword) · 403 REAUTH_FAILED */
      changePassword: (body: IPasswordChangeRequest): Promise<void> => {
        return request<void>('POST', '/admin/me/password', { auth: true, json: body });
      },
      /** 내 TOTP·복구 코드 삭제 (204). 이어서 mfaSetup → mfaEnable로 다시 등록한다 */
      mfaReset: (body: IMfaResetRequest): Promise<void> => {
        return request<void>('POST', '/admin/me/mfa/reset', { auth: true, json: body });
      },
      mfaSetup: (): Promise<IMfaSetupResponse> => {
        return request<IMfaSetupResponse>('POST', '/admin/me/mfa/setup', { auth: true });
      },
      mfaEnable: async (code: string): Promise<IMfaEnableResponse> => {
        const raw = await request<IMfaEnableResponse>('POST', '/admin/me/mfa/enable', { auth: true, json: { code } });
        return { recoveryCodes: Array.isArray(raw?.recoveryCodes) ? raw.recoveryCodes.map(String) : [] };
      },
    },

    dashboard: (): Promise<IDashboard> => {
      return get<IDashboard>('/admin/dashboard');
    },

    codes: {
      list: (): Promise<IAdminCodesResponse> => {
        return get<IAdminCodesResponse>('/admin/codes');
      },
      create: (group: string, body: ICodeCreate) => {
        return request<unknown>('POST', `/admin/codes/${enc(group)}`, { auth: true, json: body });
      },
      update: (group: string, code: string, patch: ICodePatch) => {
        return request<unknown>('PATCH', `/admin/codes/${enc(group)}/${enc(code)}`, { auth: true, json: patch });
      },
      reorder: (group: string, codes: string[]) => {
        return request<unknown>('PUT', `/admin/codes/${enc(group)}/order`, { auth: true, json: { codes } });
      },
      remove: (group: string, code: string) => {
        return request<void>('DELETE', `/admin/codes/${enc(group)}/${enc(code)}`, { auth: true });
      },
    },

    providers: {
      list: async (): Promise<IAiProvider[]> => {
        return toArray<Record<string, unknown>>(await get<unknown>('/admin/ai/providers')).map(toProvider);
      },
      types: async (): Promise<IProviderTypeSpec[]> => {
        return toTypeSpecs(await get<unknown>('/admin/ai/types'));
      },
      create: (body: IProviderCreate) => {
        return request<IAiProvider>('POST', '/admin/ai/providers', { auth: true, json: body });
      },
      update: (providerId: string, patch: TProviderPatch) => {
        return request<IAiProvider>('PATCH', `/admin/ai/providers/${enc(providerId)}`, { auth: true, json: patch });
      },
      remove: (providerId: string) => {
        return request<void>('DELETE', `/admin/ai/providers/${enc(providerId)}`, { auth: true });
      },
      /**
       * API 키를 넣거나 바꾼다 (07 6장, 쓰기 전용). 키는 JSON 본문으로만 보낸다 — 경로·쿼리에 넣지 않는다.
       * 오류: 403 REAUTH_FAILED · 400 API_KEY_INVALID · 409 SECRET_KEY_MISSING · 400 VALIDATION_FAILED(apiKey)
       */
      setApiKey: async (providerId: string, body: IApiKeySetRequest): Promise<IAiProvider | null> => {
        const raw = await request<unknown>('PUT', `/admin/ai/providers/${enc(providerId)}/api-key`, {
          auth: true,
          json: { apiKey: body.apiKey, currentPassword: body.currentPassword, verify: body.verify ?? true },
          timeoutMs: API_KEY_TIMEOUT,
        });
        return toProviderOrNull(raw);
      },
      /** 웹에서 넣은 키를 지운다 → 서버 설정 키로 되돌아감. DELETE지만 JSON 본문(currentPassword)을 보낸다 */
      deleteApiKey: async (providerId: string, body: IApiKeyDeleteRequest): Promise<IAiProvider | null> => {
        const raw = await request<unknown>('DELETE', `/admin/ai/providers/${enc(providerId)}/api-key`, {
          auth: true,
          json: { currentPassword: body.currentPassword },
        });
        return toProviderOrNull(raw);
      },
      /**
       * 모델 드롭다운 목록 (07 4장). 키가 있으면 제공사 목록(LIVE, 서버 10분 캐시), 없거나 실패하면 내장 목록(CATALOG + error).
       * refresh면 서버 캐시를 무시하고 제공사에서 다시 받는다. 서버가 이 API를 모르면 404
       */
      models: async (providerId: string, refresh = false): Promise<IProviderModelList> => {
        const raw = await get<unknown>(
          `/admin/ai/providers/${enc(providerId)}/models`,
          refresh ? { refresh: 'true' } : undefined,
        );
        return toModelList(raw);
      },
      /** 사진 한 장으로 그 공급자를 한 번 호출 (사용 여부와 관계없이) */
      test: (providerId: string, image: File): Promise<IProviderTestResult> => {
        const form = () => {
          const fd = new FormData();
          fd.append('image', image, image.name || 'photo.jpg');
          return fd;
        };
        return request<IProviderTestResult>('POST', `/admin/ai/providers/${enc(providerId)}/test`, {
          auth: true,
          form,
          timeoutMs: TEST_TIMEOUT,
        });
      },
    },

    aiLogs: {
      list: async (q: IAiLogQuery): Promise<IPage<IAiLogRow>> => {
        const raw = await get<unknown>('/admin/ai/logs', { ...q });
        return toPage(raw, q, toLogRow);
      },
      get: (id: string): Promise<TAiLogDetail> => {
        return get<TAiLogDetail>(`/admin/ai/logs/${enc(id)}`);
      },
    },

    users: {
      list: async (q: IUserQuery): Promise<IPage<IAdminUser>> => {
        return toPage(await get<unknown>('/admin/users', { ...q }), q, toAdminUser);
      },
      /** 정지·해제, 역할 지정·해제. 역할 해제 오류: 400 role SELF_NOT_ALLOWED · 400 role CONFLICT · 409 LAST_ADMIN */
      update: (id: string, patch: IUserPatch) => {
        return request<IAdminUser>('PATCH', `/admin/users/${enc(id)}`, { auth: true, json: patch });
      },
      /** 다른 관리자의 2단계 인증 초기화 (204). 그 회원의 세션도 모두 끊긴다. 자기 자신은 400 id SELF_NOT_ALLOWED */
      resetMfa: (id: string): Promise<void> => {
        return request<void>('DELETE', `/admin/users/${enc(id)}/mfa`, { auth: true });
      },
    },

    /** 친구 초대 이력 (doc/25, 최신순) */
    invites: {
      /** 초대 코드 가입 이력 */
      signups: async (q: IInviteSignupQuery): Promise<IPage<IInviteSignupRow>> => {
        return toPage(await get<unknown>('/admin/invites/signups', { ...q }), q);
      },
      /** 초대한 이력 */
      shares: async (q: IInviteShareQuery): Promise<IPage<IInviteShareRow>> => {
        return toPage(await get<unknown>('/admin/invites/shares', { ...q }), q);
      },
    },

    /** 탈퇴 기록 (doc/27 4단계, 최근 신청순) */
    withdrawals: {
      list: async (q: IWithdrawalQuery): Promise<IPage<IWithdrawalRow>> => {
        return toPage(await get<unknown>('/admin/withdrawals', { ...q }), q);
      },
      /** 최근 days일 요약 (기본 30) */
      summary: (days?: number): Promise<IWithdrawalSummary> => {
        return get<IWithdrawalSummary>('/admin/withdrawals/summary', days ? { days } : {});
      },
      /** 탈퇴 취소 (204). 탈퇴 예정이 아니면 404 */
      cancel: (userId: string): Promise<void> => {
        return request<void>('POST', `/admin/withdrawals/${enc(userId)}/cancel`, { auth: true });
      },
      /** 바로 삭제 (204, 되돌릴 수 없음). 탈퇴 예정이 아니면 404 */
      purge: (userId: string): Promise<void> => {
        return request<void>('POST', `/admin/withdrawals/${enc(userId)}/purge`, { auth: true });
      },
    },

    /** 옷장 도우미 연결 (doc/26 6단계, 최근 요청순) */
    pairings: {
      list: async (q: IPairingQuery): Promise<IPage<IPairingRow>> => {
        return toPage(await get<unknown>('/admin/pairings', { ...q }), q);
      },
      /** 돕는 사람이 한 일 (최신순 최대 100건) */
      actions: async (id: string): Promise<IPairingAction[]> => {
        return toArray<IPairingAction>(await get<unknown>(`/admin/pairings/${enc(id)}/actions`));
      },
      /** 강제 해제 (204). 없거나 이미 끝났으면 404 */
      end: (id: string): Promise<void> => {
        return request<void>('POST', `/admin/pairings/${enc(id)}/end`, { auth: true });
      },
    },

    /** 관리자 로그인 기록 (doc/22, 최신순) */
    authEvents: {
      list: async (q: IAuthEventQuery): Promise<IPage<IAuthEventRow>> => {
        return toPage(await get<unknown>('/admin/auth-events', { ...q }), q, toAuthEventRow);
      },
    },

    /** 로그인 세션 (doc/16 4장). 끊긴 기기는 다음 요청에서 401 TOKEN_INVALID를 받는다 */
    sessions: {
      /** 살아 있는 세션 목록 (마지막 활동 최신순) */
      list: async (q: ISessionQuery): Promise<IPage<IAdminSession>> => {
        return toPage(await get<unknown>('/admin/sessions', { ...q }), q, toSession);
      },
      /** 회원 한 명의 세션과 최근 로그인 이력 */
      ofUser: async (userId: string): Promise<IUserSessions> => {
        return toUserSessions(await get<unknown>(`/admin/users/${enc(userId)}/sessions`));
      },
      /** 세션 하나 끊기 (204). 내 지금 세션이면 400 */
      revoke: (sessionId: string) => {
        return request<void>('DELETE', `/admin/sessions/${enc(sessionId)}`, { auth: true });
      },
      /** 회원의 모든 세션 끊기 (204). 자기 자신이면 400 */
      revokeUser: (userId: string) => {
        return request<void>('DELETE', `/admin/users/${enc(userId)}/sessions`, { auth: true });
      },
    },

    appSettings: {
      /** 지금 전체 사용자에게 적용 중인 앱 테마 */
      theme: async (): Promise<IAppTheme> => {
        return toAppTheme(await get<unknown>('/admin/app/theme'));
      },
      /** 앱 테마·계절 모드 바꾸기 (보낸 값만). 허용 값 밖이면 400 VALIDATION_FAILED(themeId · seasonMode) */
      setTheme: async (body: IAppThemeUpdate): Promise<IAppTheme> => {
        return toAppTheme(await request<unknown>('PUT', '/admin/app/theme', { auth: true, json: body }));
      },
    },

    /** 날씨 (doc/17 6장). 서버가 주기적으로 받아 저장하고, 앱은 저장값만 읽는다 */
    weather: {
      schedule: (): Promise<IWeatherSchedule> => {
        return get<IWeatherSchedule>('/admin/weather/settings');
      },
      /** 범위를 벗어나면 400 VALIDATION_FAILED(fieldErrors) */
      updateSetting: (body: TWeatherSettingUpdate): Promise<IWeatherSchedule> => {
        return request<IWeatherSchedule>('PUT', '/admin/weather/settings', { auth: true, json: body });
      },
      providers: async (): Promise<IWeatherProvider[]> => {
        return toArray<IWeatherProvider>(await get<unknown>('/admin/weather/providers'));
      },
      updateProvider: (code: string, patch: IWeatherProviderPatch): Promise<IWeatherProvider> => {
        return request<IWeatherProvider>('PATCH', `/admin/weather/providers/${enc(code)}`, {
          auth: true,
          json: patch,
        });
      },
      /** 차단 해제 (연속 실패·차단을 비운다) */
      resetProvider: (code: string): Promise<IWeatherProvider> => {
        return request<IWeatherProvider>('POST', `/admin/weather/providers/${enc(code)}/reset`, { auth: true });
      },
      /** 그 공급자로 한 지역을 불러 본다. 저장된 날씨·공급자 상태는 바뀌지 않는다 */
      test: (code: string, regionCode: string): Promise<IWeatherTestResult> => {
        return request<IWeatherTestResult>('POST', `/admin/weather/providers/${enc(code)}/test`, {
          auth: true,
          json: { regionCode },
          timeoutMs: TEST_TIMEOUT,
        });
      },
      /** 지금 실행 (202 + runId). 실행 중이면 409 WEATHER_REFRESH_RUNNING, 공급자가 없으면 409 WEATHER_NO_PROVIDER */
      refresh: (regionCode?: string): Promise<{ runId: string }> => {
        return request<{ runId: string }>('POST', '/admin/weather/refresh', {
          auth: true,
          json: regionCode ? { regionCode } : {},
        });
      },
      runs: async (q: IWeatherRunQuery): Promise<IPage<IWeatherRun>> => {
        return toPage<IWeatherRun>(await get<unknown>('/admin/weather/runs', { ...q }), q);
      },
      run: (runId: string): Promise<IWeatherRunDetail> => {
        return get<IWeatherRunDetail>(`/admin/weather/runs/${enc(runId)}`);
      },
      calls: async (q: IWeatherCallQuery): Promise<IPage<IWeatherCall>> => {
        return toPage<IWeatherCall>(await get<unknown>('/admin/weather/calls', { ...q }), q);
      },
      callSummary: (): Promise<IWeatherCallSummary> => {
        return get<IWeatherCallSummary>('/admin/weather/calls/summary');
      },
      snapshots: async (): Promise<IWeatherSnapshot[]> => {
        return toArray<IWeatherSnapshot>(await get<unknown>('/admin/weather/snapshots'));
      },
      snapshot: (regionCode: string): Promise<IWeatherSnapshot> => {
        return get<IWeatherSnapshot>(`/admin/weather/snapshots/${enc(regionCode)}`);
      },
    },

    audit: {
      list: async (q: IAuditQuery): Promise<IPage<IAuditLog>> => {
        return toPage(await get<unknown>('/admin/audit-logs', { ...q }), q, toAudit);
      },
    },
  };
};

export type TAdminApi = ReturnType<typeof createAdminApi>;
