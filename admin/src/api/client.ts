// Wearly 관리자 API 클라이언트.
// 토큰 규칙은 doc/05 8장과 같다: TOKEN_EXPIRED면 Refresh로 한 번 갱신하고(single-flight) 원래 요청을 한 번 다시 보낸다.
// 갱신이 실패하면 저장소를 비우고 onAuthLost()를 부른다 → 로그인 화면으로 간다.

import { ApiError, errorFromResponse, networkError } from '@/api/errors';
import { tokenStore } from '@/api/tokenStore';
import type { ITokens } from '@/api/tokenStore';
import type {
  IAdminCodesResponse,
  IAdminUser,
  IAiLogQuery,
  IAiLogRow,
  IAiProvider,
  IApiKeyDeleteRequest,
  IApiKeySetRequest,
  IAuditLog,
  IAuditQuery,
  IAuthResponse,
  ICodeCreate,
  ICodePatch,
  IDashboard,
  ILoginRequest,
  IPage,
  IPageQuery,
  IProviderCreate,
  IProviderOptionSpec,
  IProviderTestResult,
  IProviderTypeSpec,
  IUserDto,
  IUserPatch,
  IUserQuery,
  TAiLogDetail,
  TKeySource,
  TProviderPatch,
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
 * (웹 키 기능 전에는 환경변수 키뿐이었으므로 있으면 'ENV', 없으면 'NONE').
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
    const headers: Record<string, string> = { Accept: 'application/json' };
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
    if (!res.ok) throw errorFromResponse(res.status, text);
    if (res.status === 204 || !text) return undefined as T;
    try {
      return JSON.parse(text) as T;
    } catch {
      throw new ApiError(res.status, 'HTTP_ERROR', '서버 응답을 읽지 못했어요.');
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
          if (!current?.refreshToken) throw new ApiError(401, 'REFRESH_INVALID', '로그인이 만료됐어요.');
          const auth = await readBody<IAuthResponse>(
            await send('POST', '/auth/refresh', { json: { refreshToken: current.refreshToken } }),
          );
          if (auth.user?.role && auth.user.role !== 'ADMIN') {
            throw new ApiError(403, 'NOT_ADMIN', '관리자 계정이 아닙니다.');
          }
          const next = { accessToken: auth.accessToken, refreshToken: auth.refreshToken };
          tokenStore.set(next);
          if (auth.user) tokenStore.setUser(auth.user);
          return next;
        } catch (e) {
          // 네트워크 실패는 Refresh가 아직 유효할 수 있으므로 로그인 상태를 지우지 않는다
          if (!(e instanceof ApiError && e.code === 'NETWORK')) {
            lose(e instanceof ApiError ? e : new ApiError(401, 'REFRESH_INVALID', '로그인이 만료됐어요.'));
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
      const err = new ApiError(401, 'UNAUTHORIZED', '로그인이 필요해요.');
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
      if (original.code === 'FORBIDDEN') lose(original);
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

  return {
    auth: {
      /** 로그인. role이 ADMIN이 아니면 서버 토큰을 바로 폐기하고 NOT_ADMIN 오류를 던진다 */
      login: async (req: ILoginRequest): Promise<IAuthResponse> => {
        const auth = await request<IAuthResponse>('POST', '/auth/login', { json: req });
        if (auth.user?.role !== 'ADMIN') {
          // 관리자가 아닌 계정의 토큰은 저장하지 않고 서버에서도 폐기한다
          await request<void>('POST', '/auth/logout', { json: { refreshToken: auth.refreshToken } }).catch(
            () => undefined,
          );
          throw new ApiError(403, 'NOT_ADMIN', '관리자 계정이 아닙니다.');
        }
        tokenStore.set({ accessToken: auth.accessToken, refreshToken: auth.refreshToken });
        tokenStore.setUser(auth.user);
        return auth;
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
      /** 웹에서 넣은 키를 지운다 → 환경변수 키로 되돌아감. DELETE지만 JSON 본문(currentPassword)을 보낸다 */
      deleteApiKey: async (providerId: string, body: IApiKeyDeleteRequest): Promise<IAiProvider | null> => {
        const raw = await request<unknown>('DELETE', `/admin/ai/providers/${enc(providerId)}/api-key`, {
          auth: true,
          json: { currentPassword: body.currentPassword },
        });
        return toProviderOrNull(raw);
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
        return toPage<IAdminUser>(await get<unknown>('/admin/users', { ...q }), q);
      },
      update: (id: string, patch: IUserPatch) => {
        return request<IAdminUser>('PATCH', `/admin/users/${enc(id)}`, { auth: true, json: patch });
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
