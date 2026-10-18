// Wearly 백엔드 API 클라이언트 (doc/05_백엔드_API.md 8장).
// 순수 TypeScript: react-native·expo-*·@/ 별칭을 쓰지 않는다. 연동 하네스가 Node 22에서 그대로 import한다.
// fetch·FormData·Blob·AbortController는 전역(Node 22, React Native, 브라우저 공통)을 쓴다.

import { ApiError, errorFromResponse, networkError } from './errors';
import type { Tokens, TokenStore } from './tokenStore';
import type {
  AnalyzeFile,
  AnalyzeImageRequest,
  AppConfigDto,
  AppConfigResult,
  AnalyzeResponse,
  AuthResponse,
  CodesResponse,
  CodesResult,
  ImageCompleteResponse,
  ItemDto,
  ItemFilter,
  ItemInput,
  ItemListResponse,
  LoginRequest,
  OutfitRecommendRequest,
  OutfitRecommendResponse,
  PlaceSearchResponse,
  PlanBulkResponse,
  PlanDto,
  PlanFilter,
  PlanInput,
  PlanListResponse,
  PlanPatch,
  ProfileDto,
  RetireRequest,
  RNFile,
  SignupRequest,
  UploadBody,
  UploadUrlRequest,
  UploadUrlResponse,
  UserDto,
  WardrobeStats,
  WeatherDayResponse,
  WeatherDayWhere,
  WeatherDto,
  WeatherResult,
} from './types';

export interface ApiClientOptions {
  /** 서버 주소. 예: `http://localhost:8080`. `/api/v1`로 끝나도 되고 아니어도 된다. */
  baseUrl: string;
  tokenStore: TokenStore;
  /**
   * Refresh 실패(또는 토큰 무효)로 로그인 상태를 잃었을 때. 저장소는 이미 비워진 뒤에 불린다.
   * reason.code: TOKEN_INVALID(강제 로그아웃·정지·탈퇴, doc/16 6장) · REFRESH_INVALID · REFRESH_REUSED 등
   */
  onAuthLost?: (reason: ApiError) => void;
  /** 요청 시간 제한(ms). 기본 15초. 0이면 제한 없음 */
  timeoutMs?: number;
  /** AI 분석 요청 시간 제한(ms). 기본 60초 */
  analyzeTimeoutMs?: number;
  /** AI 코디 추천 요청 시간 제한(ms). 기본 60초 */
  recommendTimeoutMs?: number;
  /** 사진 올리기(서명 주소 PUT)·complete 시간 제한(ms). 기본 60초 */
  uploadTimeoutMs?: number;
  /** 테스트용 fetch 교체 */
  fetch?: typeof fetch;
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/** 서버가 로그인 세션의 종류(APP·ADMIN·WEB)를 정하는 헤더 (doc/16 4장). 모든 요청에 붙인다 */
const CLIENT_HEADER = { 'X-Wearly-Client': 'app' } as const;

interface RequestOptions {
  /** JSON 본문 */
  json?: unknown;
  /** multipart 본문. 재시도 때 다시 만들 수 있도록 함수로 받는다 */
  form?: () => FormData;
  query?: Record<string, string | undefined>;
  /** Authorization 헤더를 붙이고 TOKEN_EXPIRED 시 갱신·재시도한다 */
  auth?: boolean;
  timeoutMs?: number;
  /** 추가 요청 헤더 (예: If-None-Match) */
  headers?: Record<string, string>;
}

const API_PREFIX = '/api/v1';

function joinBase(baseUrl: string): string {
  const trimmed = baseUrl.replace(/\/+$/, '');
  return trimmed.endsWith(API_PREFIX) ? trimmed : trimmed + API_PREFIX;
}

function buildQuery(query?: Record<string, string | undefined>): string {
  if (!query) return '';
  const parts: string[] = [];
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === '') continue;
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(v)}`);
  }
  return parts.length ? `?${parts.join('&')}` : '';
}

const EXT_BY_TYPE: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
  'image/heic': 'heic',
  'image/heif': 'heic',
};

function isBlob(v: unknown): v is Blob {
  if (typeof Blob !== 'undefined' && v instanceof Blob) return true;
  // expo-file-system의 File처럼 Blob을 상속하지 않고 인터페이스만 구현한 파일 객체.
  // Expo SDK 57의 전역 fetch(expo/fetch)는 이런 객체와 Blob만 멀티파트 파일로 보낼 수 있고,
  // RN 방식의 { uri, name, type } 파트는 'Unsupported FormDataPart implementation'으로 거부한다.
  return (
    typeof v === 'object' &&
    v !== null &&
    typeof (v as { bytes?: unknown }).bytes === 'function' &&
    typeof (v as { arrayBuffer?: unknown }).arrayBuffer === 'function'
  );
}

function isRNFile(v: unknown): v is RNFile {
  return typeof v === 'object' && v !== null && typeof (v as RNFile).uri === 'string';
}

/** FormData.append의 RN 확장(파일 객체)과 파일 이름 인자를 함께 쓰기 위한 느슨한 타입 */
type LooseFormData = { append(name: string, value: unknown, fileName?: string): void };

function analyzeForm(file: AnalyzeFile): FormData {
  const form = new FormData();
  const loose = form as unknown as LooseFormData;
  if (isBlob(file)) {
    const named = (file as Blob & { name?: unknown }).name;
    const name = typeof named === 'string' && named ? named : `photo.${EXT_BY_TYPE[file.type] ?? 'jpg'}`;
    loose.append('image', file, name);
  } else if (isRNFile(file)) {
    // React Native: { uri, name, type } 객체를 파일 파트로 보낸다
    loose.append('image', { uri: file.uri, name: file.name || 'photo.jpg', type: file.type || 'image/jpeg' });
  } else {
    throw new ApiError(0, 'VALIDATION_FAILED', '분석할 사진이 없어요.', [{ field: 'image', code: 'REQUIRED', message: '사진을 골라 주세요.' }]);
  }
  return form;
}

/** 업로드 본문을 바이트로 읽는다. Blob의 type이 Content-Type을 덮어쓰지 않도록(서명 불일치) 바이트로 보낸다 */
async function uploadBytes(body: UploadBody): Promise<Uint8Array<ArrayBuffer>> {
  if (body instanceof Uint8Array) return new Uint8Array(body);
  if (body instanceof ArrayBuffer) return new Uint8Array(body);
  if (typeof (body as { arrayBuffer?: unknown }).arrayBuffer === 'function') {
    return new Uint8Array(await (body as { arrayBuffer(): Promise<ArrayBuffer> }).arrayBuffer());
  }
  throw new ApiError(0, 'VALIDATION_FAILED', '올릴 사진이 없어요.');
}

export function createApiClient(options: ApiClientOptions) {
  const base = joinBase(options.baseUrl);
  const { tokenStore, onAuthLost } = options;
  const doFetch: typeof fetch = options.fetch ?? ((input, init) => fetch(input, init));
  const defaultTimeout = options.timeoutMs ?? 15_000;
  const analyzeTimeout = options.analyzeTimeoutMs ?? 60_000;
  const recommendTimeout = options.recommendTimeoutMs ?? 60_000;
  const uploadTimeout = options.uploadTimeoutMs ?? 60_000;

  /** 진행 중인 갱신. 동시에 여러 요청이 만료돼도 /auth/refresh는 한 번만 보낸다 (single-flight) */
  let refreshing: Promise<Tokens> | null = null;

  async function send(method: Method, path: string, opts: RequestOptions, accessToken?: string): Promise<Response> {
    const headers: Record<string, string> = { Accept: 'application/json', ...CLIENT_HEADER, ...opts.headers };
    let body: BodyInit | undefined;
    if (opts.form) {
      body = opts.form(); // Content-Type(boundary 포함)은 fetch가 정한다
    } else if (opts.json !== undefined) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(opts.json);
    }
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

    const timeout = opts.timeoutMs ?? defaultTimeout;
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : undefined;
    let timedOut = false;
    const timer =
      controller && timeout > 0
        ? setTimeout(() => {
            timedOut = true;
            controller.abort();
          }, timeout)
        : undefined;
    try {
      return await doFetch(base + path + buildQuery(opts.query), { method, headers, body, signal: controller?.signal });
    } catch (e) {
      throw networkError(e, timedOut);
    } finally {
      if (timer !== undefined) clearTimeout(timer);
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

  async function lose(reason: ApiError): Promise<void> {
    try {
      await tokenStore.clear();
    } finally {
      onAuthLost?.(reason);
    }
  }

  function refreshTokens(): Promise<Tokens> {
    if (!refreshing) {
      refreshing = (async () => {
        const current = await tokenStore.get();
        try {
          if (!current?.refreshToken) throw new ApiError(401, 'REFRESH_INVALID', '로그인이 만료됐어요.');
          const res = await send('POST', '/auth/refresh', { json: { refreshToken: current.refreshToken } });
          const auth = await readBody<AuthResponse>(res);
          const next = { accessToken: auth.accessToken, refreshToken: auth.refreshToken };
          await tokenStore.set(next);
          return next;
        } catch (e) {
          // 네트워크 실패는 Refresh 토큰이 아직 유효할 수 있으므로 로그인 상태를 지우지 않는다.
          // 그 밖의 실패(REFRESH_INVALID·REFRESH_REUSED 등)는 저장소를 비우고 onAuthLost를 한 번만 부른다.
          if (!(e instanceof ApiError && e.code === 'NETWORK')) {
            await lose(e instanceof ApiError ? e : new ApiError(401, 'REFRESH_INVALID', '로그인이 만료됐어요.'));
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

    // 갱신이 진행 중이면 끝난 뒤의 새 토큰으로 보낸다
    if (refreshing) await refreshing.catch(() => undefined);
    const used = (await tokenStore.get())?.accessToken;
    const first = await send(method, path, opts, used);
    if (first.status !== 401) return readBody<T>(first);

    let original: ApiError;
    try {
      return await readBody<T>(first); // 401이면 항상 throw
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
      original = e;
    }

    if (original.code === 'TOKEN_INVALID') {
      // 서명 오류·탈퇴·정지, 다른 곳에서 강제 로그아웃(세션 없음, doc/16): 갱신해도 소용없다
      if (used) await lose(original);
      throw original;
    }
    if (original.code !== 'TOKEN_EXPIRED') throw original;

    // 이 요청이 실패하는 사이 다른 요청이 이미 갱신했다면 새 토큰으로 바로 재시도
    const latest = (await tokenStore.get())?.accessToken;
    let fresh: string;
    if (latest && latest !== used) {
      fresh = latest;
    } else {
      try {
        fresh = (await refreshTokens()).accessToken;
      } catch (e) {
        if (e instanceof ApiError && e.code === 'NETWORK') throw e;
        throw original; // 저장소 비우기와 onAuthLost는 refreshTokens 안에서 이미 했다
      }
    }
    return readBody<T>(await send(method, path, opts, fresh)); // 재시도는 한 번만
  }

  /**
   * 서명 주소로 직접 올린다 (오브젝트 스토리지). 우리 서버가 아니므로 토큰·X-Wearly-Client를 붙이지 않고,
   * 응답에 준 헤더(서명에 들어간 Content-Type 등)만 그대로 붙인다.
   */
  async function putSigned(target: Pick<UploadUrlResponse, 'uploadUrl' | 'method' | 'headers'>, body: UploadBody): Promise<void> {
    const bytes = await uploadBytes(body);
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : undefined;
    let timedOut = false;
    const timer =
      controller && uploadTimeout > 0
        ? setTimeout(() => {
            timedOut = true;
            controller.abort();
          }, uploadTimeout)
        : undefined;
    let res: Response;
    try {
      res = await doFetch(target.uploadUrl, {
        method: target.method || 'PUT',
        headers: { ...target.headers },
        body: bytes,
        signal: controller?.signal,
      });
    } catch (e) {
      throw networkError(e, timedOut);
    } finally {
      if (timer !== undefined) clearTimeout(timer);
    }
    if (res.ok) {
      await res.text().catch(() => undefined); // 연결을 비운다
      return;
    }
    // S3 계열 오류는 XML이다. 서버 오류 형식이 아니므로 상태만 담는다
    const text = await res.text().catch(() => '');
    const code = /<Code>([^<]+)<\/Code>/.exec(text)?.[1];
    const err = new ApiError(res.status, 'UPLOAD_FAILED', `사진을 올리지 못했어요. (HTTP ${res.status}${code ? ` ${code}` : ''})`);
    (err as { cause?: unknown }).cause = text.slice(0, 500);
    throw err;
  }

  async function saveAuth(auth: AuthResponse): Promise<AuthResponse> {
    await tokenStore.set({ accessToken: auth.accessToken, refreshToken: auth.refreshToken });
    return auth;
  }

  return {
    codes: {
      /**
       * 옷 속성 코드 전체 (인증 불필요). etag를 주면 If-None-Match로 보내고,
       * 서버가 304를 주면 `{ changed: false }`를 돌려준다.
       */
      async list(etag?: string | null): Promise<CodesResult> {
        const res = await send('GET', '/codes', { headers: etag ? { 'If-None-Match': etag } : undefined });
        if (res.status === 304) return { changed: false };
        const codes = await readBody<CodesResponse>(res);
        if (!codes || !Array.isArray(codes.groups)) throw new ApiError(res.status, 'HTTP_ERROR', '코드 응답 형식이 달라요.');
        // 웹(CORS)에서는 ETag 헤더를 읽지 못할 수 있으니 본문의 version으로 같은 값을 만든다
        const header = res.headers?.get?.('ETag');
        return { changed: true, codes, etag: header || `"${codes.version}"` };
      },
    },
    app: {
      /**
       * 앱 설정 (인증 불필요, doc/12 3장). 지금은 테마 ID. etag를 주면 If-None-Match로 보내고,
       * 서버가 304를 주면 `{ changed: false }`를 돌려준다.
       */
      async config(etag?: string | null): Promise<AppConfigResult> {
        const res = await send('GET', '/app/config', { headers: etag ? { 'If-None-Match': etag } : undefined });
        if (res.status === 304) return { changed: false };
        const config = await readBody<AppConfigDto>(res);
        if (!config || typeof config.themeId !== 'string') {
          throw new ApiError(res.status, 'HTTP_ERROR', '앱 설정 응답 형식이 달라요.');
        }
        // 웹(CORS)에서는 ETag 헤더를 읽지 못할 수 있으니 본문의 version으로 같은 값을 만든다
        const header = res.headers?.get?.('ETag');
        return { changed: true, config, etag: header || `"${config.version}"` };
      },
    },
    weather: {
      /**
       * 서버에 저장된 날씨 (인증 불필요, doc/17 5장). region이 없으면 서울. etag를 주면 If-None-Match로 보내고,
       * 서버가 304를 주면 `{ changed: false }`를 돌려준다. 한 번도 받지 못한 지역은 503 WEATHER_UNAVAILABLE
       */
      async current(region?: string | null, etag?: string | null): Promise<WeatherResult> {
        const query = region ? `?region=${encodeURIComponent(region)}` : '';
        const res = await send('GET', `/weather${query}`, { headers: etag ? { 'If-None-Match': etag } : undefined });
        if (res.status === 304) return { changed: false };
        const weather = await readBody<WeatherDto>(res);
        if (!weather || !weather.current || !Array.isArray(weather.days)) {
          throw new ApiError(res.status, 'HTTP_ERROR', '날씨 응답 형식이 달라요.');
        }
        return { changed: true, weather, etag: res.headers?.get?.('ETag') ?? null };
      },
      /**
       * 한 장소·한 날짜의 날씨 (doc/19). 지역 코드는 인증 없이, 좌표는 로그인한 사용자만 부른다.
       * source: FORECAST(예보) · CLIMATE(그 달 평년값). date는 한국 날짜 YYYY-MM-DD, 오늘부터 1년 안.
       * 오류: 400 VALIDATION_FAILED(date·region) · 429 WEATHER_LIMIT(좌표 하루 한도) · 503 WEATHER_UNAVAILABLE.
       * 이 API가 없는 예전 서버는 404(로그인) 또는 401 UNAUTHORIZED(지역 코드, 공개 경로가 아님)를 준다
       */
      day: async (where: WeatherDayWhere, date: string): Promise<WeatherDayResponse> => {
        const query =
          'region' in where
            ? { region: where.region, date }
            : { lat: String(where.lat), lon: String(where.lon), date };
        const res = await request<WeatherDayResponse>('GET', '/weather/day', { auth: !('region' in where), query });
        if (!res || !res.day || typeof res.day.temp !== 'number' || (res.source !== 'FORECAST' && res.source !== 'CLIMATE')) {
          throw new ApiError(200, 'HTTP_ERROR', '날씨 응답 형식이 달라요.');
        }
        return res;
      },
      /**
       * 장소 검색 (로그인 필요, doc/19). q는 1~50자, 결과는 최대 10개.
       * 오류: 400 VALIDATION_FAILED(q) · 429 WEATHER_LIMIT. 예전 서버는 404
       */
      places: async (q: string): Promise<PlaceSearchResponse> => {
        const res = await request<PlaceSearchResponse>('GET', '/weather/places', { auth: true, query: { q } });
        if (!res || !Array.isArray(res.items)) throw new ApiError(200, 'HTTP_ERROR', '장소 검색 응답 형식이 달라요.');
        return res;
      },
    },
    auth: {
      /** 가입 + 토큰 저장 */
      async signup(req: SignupRequest): Promise<AuthResponse> {
        return saveAuth(await request<AuthResponse>('POST', '/auth/signup', { json: req }));
      },
      /** 로그인 + 토큰 저장 */
      async login(req: LoginRequest): Promise<AuthResponse> {
        return saveAuth(await request<AuthResponse>('POST', '/auth/login', { json: req }));
      },
      /** 서버에서 Refresh 토큰 폐기 + 저장소 비움. 서버 호출이 실패해도 저장소는 비운다 */
      async logout(): Promise<void> {
        try {
          const tokens = await tokenStore.get();
          if (tokens?.refreshToken) {
            await request<void>('POST', '/auth/logout', { json: { refreshToken: tokens.refreshToken } });
          }
        } catch {
          // 무시: 로그아웃은 기기에서 토큰을 지우는 것이 핵심이다
        } finally {
          await tokenStore.clear();
        }
      },
      /** 저장된 Refresh 토큰으로 직접 갱신 (보통은 자동으로 일어난다) */
      refresh(): Promise<Tokens> {
        return refreshTokens();
      },
    },
    users: {
      me(): Promise<UserDto> {
        return request<UserDto>('GET', '/users/me', { auth: true });
      },
      /** 회원 탈퇴 + 저장소 비움 */
      async deleteMe(): Promise<void> {
        await request<void>('DELETE', '/users/me', { auth: true });
        await tokenStore.clear();
      },
    },
    profile: {
      get(): Promise<ProfileDto> {
        return request<ProfileDto>('GET', '/users/me/profile', { auth: true });
      },
      put(dto: ProfileDto): Promise<ProfileDto> {
        return request<ProfileDto>('PUT', '/users/me/profile', { auth: true, json: dto });
      },
    },
    wardrobe: {
      list(filter: ItemFilter = {}): Promise<ItemListResponse> {
        return request<ItemListResponse>('GET', '/wardrobe/items', {
          auth: true,
          query: {
            category: filter.category,
            status: filter.status,
            color: filter.color,
            q: filter.q,
            sort: filter.sort,
            // 예전 서버는 모르는 파라미터를 무시하고 전체(=사용 중) 목록을 준다
            retired: filter.retired === undefined ? undefined : String(filter.retired),
          },
        });
      },
      get(id: string): Promise<ItemDto> {
        return request<ItemDto>('GET', `/wardrobe/items/${encodeURIComponent(id)}`, { auth: true });
      },
      create(input: ItemInput): Promise<ItemDto> {
        return request<ItemDto>('POST', '/wardrobe/items', { auth: true, json: input });
      },
      /** 최대 100개. 응답은 요청 순서대로 */
      bulkCreate(inputs: ItemInput[]): Promise<ItemListResponse> {
        return request<ItemListResponse>('POST', '/wardrobe/items/bulk', { auth: true, json: { items: inputs } });
      },
      update(id: string, patch: Partial<ItemInput>): Promise<ItemDto> {
        return request<ItemDto>('PATCH', `/wardrobe/items/${encodeURIComponent(id)}`, { auth: true, json: patch });
      },
      /** 완전 삭제 (doc/13). 되돌릴 수 없다 */
      remove(id: string): Promise<void> {
        return request<void>('DELETE', `/wardrobe/items/${encodeURIComponent(id)}`, { auth: true });
      },
      /**
       * 사용하지 않는 옷으로 옮긴다 (doc/13 3장). 이미 사용 중지면 이유·메모만 바뀌고 시각은 그대로다.
       * 오류: 400 VALIDATION_FAILED(reason·memo) · 404 NOT_FOUND. 이 API가 없는 예전 서버도 404를 준다
       */
      retire: (id: string, req: RetireRequest): Promise<ItemDto> =>
        request<ItemDto>('POST', `/wardrobe/items/${encodeURIComponent(id)}/retire`, { auth: true, json: req }),
      /** 다시 사용한다 (doc/13 3장). 사용 중이면 그대로 200 */
      restore: (id: string): Promise<ItemDto> =>
        request<ItemDto>('POST', `/wardrobe/items/${encodeURIComponent(id)}/restore`, { auth: true }),
      stats(): Promise<WardrobeStats> {
        return request<WardrobeStats>('GET', '/wardrobe/stats', { auth: true });
      },
      /**
       * 이미 올린 사진(complete까지 끝난 내 이미지)으로 AI 분석. 응답은 multipart 분석과 같다.
       * 오류: 404 NOT_FOUND(남의·없는 이미지) · 400 IMAGE_INVALID · 429 AI_LIMIT_EXCEEDED · 502 AI_UNAVAILABLE
       */
      analyzeImage: (req: AnalyzeImageRequest): Promise<AnalyzeResponse> =>
        request<AnalyzeResponse>('POST', '/wardrobe/items/analyze', { auth: true, json: req, timeoutMs: analyzeTimeout }),
      /** AI 사진 분석. file은 Blob(Node·웹) 또는 { uri, name, type }(React Native) */
      async analyze(file: AnalyzeFile): Promise<AnalyzeResponse> {
        const form = () => analyzeForm(file);
        form(); // 잘못된 입력은 요청 전에 바로 알린다
        return request<AnalyzeResponse>('POST', '/wardrobe/items/analyze', { auth: true, form, timeoutMs: analyzeTimeout });
      },
    },
    images: {
      /**
       * 사진 올릴 서명 주소 받기 (1단계). 사진 기능이 없는 예전 서버는 404를 준다.
       * 오류: 400 VALIDATION_FAILED(형식·크기) · 429 IMAGE_UPLOAD_LIMIT(하루 한도)
       */
      uploadUrl: (req: UploadUrlRequest): Promise<UploadUrlResponse> =>
        request<UploadUrlResponse>('POST', '/images/upload-url', { auth: true, json: req }),
      /**
       * 서명 주소로 파일 올리기 (2단계). body는 Blob·바이트·arrayBuffer()가 있는 파일 객체.
       * 오류: code UPLOAD_FAILED(저장소가 거절, 주소 만료 등) · NETWORK
       */
      put: (target: Pick<UploadUrlResponse, 'uploadUrl' | 'method' | 'headers'>, body: UploadBody): Promise<void> =>
        putSigned(target, body),
      /**
       * 올리기 끝 (3단계). 서버가 검증·메타데이터 제거·축소·썸네일을 만들고 READY로 바꾼다. 이미 READY면 같은 응답.
       * 오류: 400 IMAGE_INVALID · 400 IMAGE_NOT_UPLOADED · 404 NOT_FOUND(남의·없는 이미지)
       */
      complete: (imageId: string): Promise<ImageCompleteResponse> =>
        request<ImageCompleteResponse>('POST', `/images/${encodeURIComponent(imageId)}/complete`, {
          auth: true,
          timeoutMs: uploadTimeout,
        }),
    },
    plans: {
      /**
       * 예정 코디 목록 (로그인 필요). from·to(YYYY-MM-DD, 양 끝 포함)는 선택. 날짜 → 시간대 → id 순.
       * 오류: 400 VALIDATION_FAILED(from·to). 이 API가 없는 예전 서버는 404
       */
      list: async (filter: PlanFilter = {}): Promise<PlanListResponse> => {
        const res = await request<PlanListResponse>('GET', '/plans', {
          auth: true,
          query: { from: filter.from, to: filter.to },
        });
        if (!res || !Array.isArray(res.items)) throw new ApiError(200, 'HTTP_ERROR', '예정 목록 응답 형식이 달라요.');
        return res;
      },
      /**
       * 예정 만들기. 새로 만들면 201, 같은 clientId의 예정이 있으면 그것을 고치고 200 (응답 모양은 같다).
       * 오류: 400 VALIDATION_FAILED · 409 PLAN_LIMIT(회원당 500개, params.max)
       */
      create: (input: PlanInput): Promise<PlanDto> => request<PlanDto>('POST', '/plans', { auth: true, json: input }),
      /**
       * 기기에 있던 예정을 한 번에 (최대 100개, 항목마다 clientId 필수). 같은 clientId면 고친다.
       * 하나라도 틀리면 하나도 저장하지 않는다 (필드 이름은 items[i].title 꼴).
       * 오류: 400 VALIDATION_FAILED · 409 PLAN_LIMIT
       */
      bulk: async (items: PlanInput[]): Promise<PlanBulkResponse> => {
        const res = await request<PlanBulkResponse>('POST', '/plans/bulk', { auth: true, json: { items } });
        if (!res || !Array.isArray(res.items)) throw new ApiError(200, 'HTTP_ERROR', '예정 올리기 응답 형식이 달라요.');
        return res;
      },
      /**
       * 보낸 필드만 바꾼다. memo·place·outfit·weatherSource·weatherCheckedAt은 null이면 비운다. clientId는 무시한다.
       * 오류: 400 VALIDATION_FAILED · 404 NOT_FOUND(남의·없는 예정)
       */
      patch: (id: string, patch: PlanPatch): Promise<PlanDto> =>
        request<PlanDto>('PATCH', `/plans/${encodeURIComponent(id)}`, { auth: true, json: patch }),
      /** 지우기 (204). 오류: 404 NOT_FOUND(남의·없는 예정) */
      remove: (id: string): Promise<void> => request<void>('DELETE', `/plans/${encodeURIComponent(id)}`, { auth: true }),
    },
    outfits: {
      /**
       * AI 코디 후보 (doc/09 3장). 서버가 AI로 후보를 골라 검증한 뒤 돌려준다. 점수는 앱 엔진이 다시 매긴다.
       * 오류: 400 WARDROBE_INSUFFICIENT(옷 모자람) · 429 AI_LIMIT_EXCEEDED · 502 AI_UNAVAILABLE
       */
      async recommend(req: OutfitRecommendRequest): Promise<OutfitRecommendResponse> {
        const res = await request<OutfitRecommendResponse>('POST', '/outfits/recommendations', {
          auth: true,
          json: req,
          timeoutMs: recommendTimeout,
        });
        if (!res || !Array.isArray(res.candidates)) throw new ApiError(200, 'HTTP_ERROR', '추천 응답 형식이 달라요.');
        return res;
      },
    },
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
