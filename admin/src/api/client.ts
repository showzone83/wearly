// Wearly 관리자 API 클라이언트.
// 토큰 규칙은 doc/05 8장과 같다: TOKEN_EXPIRED면 Refresh로 한 번 갱신하고(single-flight) 원래 요청을 한 번 다시 보낸다.
// 갱신이 실패하면 저장소를 비우고 onAuthLost()를 부른다 → 로그인 화면으로 간다.

import { ApiError, errorFromResponse, networkError } from './errors';
import { tokenStore, type Tokens } from './tokenStore';
import type {
  AdminCodesResponse,
  AdminUser,
  AiLogDetail,
  AiLogQuery,
  AiLogRow,
  AiProvider,
  AuditLog,
  AuditQuery,
  AuthResponse,
  CodeCreate,
  CodePatch,
  Dashboard,
  LoginRequest,
  Page,
  PageQuery,
  ProviderCreate,
  ProviderOptionSpec,
  ProviderPatch,
  ProviderTestResult,
  ProviderTypeSpec,
  UserDto,
  UserPatch,
  UserQuery,
} from './types';

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type Query = Record<string, string | number | undefined | null>;

interface RequestOptions {
  json?: unknown;
  /** multipart 본문. 재시도 때 다시 만들 수 있도록 함수로 받는다 */
  form?: () => FormData;
  query?: Query;
  /** Authorization 헤더를 붙이고 TOKEN_EXPIRED 시 갱신·재시도한다 */
  auth?: boolean;
  timeoutMs?: number;
}

const API_PREFIX = '/api/v1';
const DEFAULT_TIMEOUT = 15_000;
/** 시험 분석은 실제 AI를 부르므로 넉넉히 */
const TEST_TIMEOUT = 120_000;

export const API_URL: string = (import.meta.env.VITE_API_URL as string | undefined)?.trim() || 'http://localhost:8080';

function joinBase(baseUrl: string): string {
  const trimmed = baseUrl.replace(/\/+$/, '');
  return trimmed.endsWith(API_PREFIX) ? trimmed : trimmed + API_PREFIX;
}

function buildQuery(query?: Query): string {
  if (!query) return '';
  const parts: string[] = [];
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null || v === '') continue;
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  }
  return parts.length ? `?${parts.join('&')}` : '';
}

const enc = encodeURIComponent;

// ───────── 응답 정규화 (계약이 자세히 정하지 않은 부분을 너그럽게 받는다) ─────────

/** 목록은 `{ items, page, size, total }`가 원칙이지만, 배열로 오면 한 페이지로 감싼다 */
function toPage<T>(raw: unknown, q: PageQuery, map: (row: Record<string, unknown>) => T = (r) => r as T): Page<T> {
  if (Array.isArray(raw)) {
    return { items: raw.map((r) => map(r as Record<string, unknown>)), page: 0, size: raw.length, total: raw.length };
  }
  const p = (raw ?? {}) as Partial<Page<Record<string, unknown>>>;
  const items = Array.isArray(p.items) ? p.items.map(map) : [];
  return { items, page: p.page ?? q.page ?? 0, size: p.size ?? q.size ?? items.length, total: p.total ?? items.length };
}

function toArray<T>(raw: unknown): T[] {
  if (Array.isArray(raw)) return raw as T[];
  const items = (raw as { items?: unknown } | null)?.items;
  return Array.isArray(items) ? (items as T[]) : [];
}

const str = (v: unknown): string | null => (v === undefined || v === null ? null : String(v));
const num = (v: unknown): number | null => (typeof v === 'number' ? v : v === null || v === undefined || v === '' ? null : Number(v));

function toLogRow(r: Record<string, unknown>): AiLogRow {
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
}

function toAudit(r: Record<string, unknown>): AuditLog {
  return {
    ...(r as unknown as AuditLog),
    id: String(r.id ?? r.auditId ?? ''),
    targetId: str(r.targetId),
    adminUserId: str(r.adminUserId),
  };
}

/** `/admin/ai/types`: 배열 또는 `{ CLAUDE: {...} }` 객체, options는 배열 또는 `{ key: 설명 }` 객체 모두 받는다 */
function toTypeSpecs(raw: unknown): ProviderTypeSpec[] {
  const list: Record<string, unknown>[] = Array.isArray(raw)
    ? (raw as Record<string, unknown>[])
    : Array.isArray((raw as { items?: unknown })?.items)
      ? ((raw as { items: Record<string, unknown>[] }).items)
      : Object.entries((raw ?? {}) as Record<string, unknown>).map(([type, v]) => ({ type, ...(v as object) }));
  return list.map((t) => {
    const rawOpts = t.options;
    let options: ProviderOptionSpec[] = [];
    if (Array.isArray(rawOpts)) {
      options = rawOpts.map((o) => {
        const obj = (typeof o === 'string' ? { key: o } : o) as Record<string, unknown>;
        return { ...(obj as object), key: String(obj.key ?? obj.name ?? '') } as ProviderOptionSpec;
      });
    } else if (rawOpts && typeof rawOpts === 'object') {
      options = Object.entries(rawOpts as Record<string, unknown>).map(([key, v]) =>
        typeof v === 'string' ? { key, description: v } : ({ ...(v as object), key } as ProviderOptionSpec),
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
}

// ───────── 클라이언트 ─────────

export interface AdminApiOptions {
  baseUrl: string;
  /** 갱신 실패 등으로 로그인 상태를 잃었을 때. 저장소는 이미 비워진 뒤에 불린다 */
  onAuthLost?: (reason: ApiError) => void;
}

export function createAdminApi(options: AdminApiOptions) {
  const base = joinBase(options.baseUrl);
  let refreshing: Promise<Tokens> | null = null;

  async function send(method: Method, path: string, opts: RequestOptions, accessToken?: string): Promise<Response> {
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
  }

  async function readBody<T>(res: Response): Promise<T> {
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
  }

  function lose(reason: ApiError): void {
    tokenStore.clear();
    options.onAuthLost?.(reason);
  }

  /** 진행 중인 갱신은 하나만 (single-flight) */
  function refreshTokens(): Promise<Tokens> {
    if (!refreshing) {
      refreshing = (async () => {
        const current = tokenStore.get();
        try {
          if (!current?.refreshToken) throw new ApiError(401, 'REFRESH_INVALID', '로그인이 만료됐어요.');
          const auth = await readBody<AuthResponse>(await send('POST', '/auth/refresh', { json: { refreshToken: current.refreshToken } }));
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
  }

  async function request<T>(method: Method, path: string, opts: RequestOptions = {}): Promise<T> {
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
  }

  const get = <T>(path: string, query?: Query) => request<T>('GET', path, { auth: true, query });

  return {
    auth: {
      /** 로그인. role이 ADMIN이 아니면 서버 토큰을 바로 폐기하고 NOT_ADMIN 오류를 던진다 */
      async login(req: LoginRequest): Promise<AuthResponse> {
        const auth = await request<AuthResponse>('POST', '/auth/login', { json: req });
        if (auth.user?.role !== 'ADMIN') {
          // 관리자가 아닌 계정의 토큰은 저장하지 않고 서버에서도 폐기한다
          await request<void>('POST', '/auth/logout', { json: { refreshToken: auth.refreshToken } }).catch(() => undefined);
          throw new ApiError(403, 'NOT_ADMIN', '관리자 계정이 아닙니다.');
        }
        tokenStore.set({ accessToken: auth.accessToken, refreshToken: auth.refreshToken });
        tokenStore.setUser(auth.user);
        return auth;
      },
      async logout(): Promise<void> {
        const tokens = tokenStore.get();
        try {
          if (tokens?.refreshToken) await request<void>('POST', '/auth/logout', { json: { refreshToken: tokens.refreshToken } });
        } catch {
          // 서버 호출이 실패해도 이 탭의 토큰은 지운다
        } finally {
          tokenStore.clear();
        }
      },
      me(): Promise<UserDto> {
        return request<UserDto>('GET', '/users/me', { auth: true });
      },
    },

    dashboard(): Promise<Dashboard> {
      return get<Dashboard>('/admin/dashboard');
    },

    codes: {
      list(): Promise<AdminCodesResponse> {
        return get<AdminCodesResponse>('/admin/codes');
      },
      create(group: string, body: CodeCreate) {
        return request<unknown>('POST', `/admin/codes/${enc(group)}`, { auth: true, json: body });
      },
      update(group: string, code: string, patch: CodePatch) {
        return request<unknown>('PATCH', `/admin/codes/${enc(group)}/${enc(code)}`, { auth: true, json: patch });
      },
      reorder(group: string, codes: string[]) {
        return request<unknown>('PUT', `/admin/codes/${enc(group)}/order`, { auth: true, json: { codes } });
      },
      remove(group: string, code: string) {
        return request<void>('DELETE', `/admin/codes/${enc(group)}/${enc(code)}`, { auth: true });
      },
    },

    providers: {
      async list(): Promise<AiProvider[]> {
        return toArray<AiProvider>(await get<unknown>('/admin/ai/providers'));
      },
      async types(): Promise<ProviderTypeSpec[]> {
        return toTypeSpecs(await get<unknown>('/admin/ai/types'));
      },
      create(body: ProviderCreate) {
        return request<AiProvider>('POST', '/admin/ai/providers', { auth: true, json: body });
      },
      update(providerId: string, patch: ProviderPatch) {
        return request<AiProvider>('PATCH', `/admin/ai/providers/${enc(providerId)}`, { auth: true, json: patch });
      },
      remove(providerId: string) {
        return request<void>('DELETE', `/admin/ai/providers/${enc(providerId)}`, { auth: true });
      },
      /** 사진 한 장으로 그 공급자를 한 번 호출 (사용 여부와 관계없이) */
      test(providerId: string, image: File): Promise<ProviderTestResult> {
        const form = () => {
          const fd = new FormData();
          fd.append('image', image, image.name || 'photo.jpg');
          return fd;
        };
        return request<ProviderTestResult>('POST', `/admin/ai/providers/${enc(providerId)}/test`, { auth: true, form, timeoutMs: TEST_TIMEOUT });
      },
    },

    aiLogs: {
      async list(q: AiLogQuery): Promise<Page<AiLogRow>> {
        const raw = await get<unknown>('/admin/ai/logs', { ...q });
        return toPage(raw, q, toLogRow);
      },
      get(id: string): Promise<AiLogDetail> {
        return get<AiLogDetail>(`/admin/ai/logs/${enc(id)}`);
      },
    },

    users: {
      async list(q: UserQuery): Promise<Page<AdminUser>> {
        return toPage<AdminUser>(await get<unknown>('/admin/users', { ...q }), q);
      },
      update(id: string, patch: UserPatch) {
        return request<AdminUser>('PATCH', `/admin/users/${enc(id)}`, { auth: true, json: patch });
      },
    },

    audit: {
      async list(q: AuditQuery): Promise<Page<AuditLog>> {
        return toPage(await get<unknown>('/admin/audit-logs', { ...q }), q, toAudit);
      },
    },
  };
}

export type AdminApi = ReturnType<typeof createAdminApi>;
