// 관리자 API 타입 (doc/05_백엔드_API.md 6장 인증, doc/06_코드_관리.md, doc/07_관리자_사이트.md 4장).
// 앱(mobile/) 코드는 가져오지 않고 계약 문서만 보고 정의한다.

// ───────── 공통 ─────────

/** 오류 응답의 params (코드마다 다름, backend/src/main/resources/error-codes.json) */
export type TErrorParams = Record<string, unknown>;

/** 필드 오류 한 줄. 문장은 없다 — 화면 문구는 lib/errorText.ts의 fieldErrorText(code·params) */
export interface IFieldError {
  /** 입력 경로 (`attrs.hex`, `attrs.pairs[1]`, `body` …) */
  field: string;
  /** 필드 오류 코드 (REQUIRED, TOO_LONG, OUT_OF_RANGE …) */
  code: string;
  params: TErrorParams;
}

/**
 * 오류 응답 본문 `{ status, code, params?, fieldErrors: [{ field, code, params? }], traceId }` (2026-10-18부터 message 없음).
 * traceId는 응답 헤더 X-Request-Id와 같다.
 */
export interface IApiErrorBody {
  status: number;
  code: string;
  params?: TErrorParams;
  fieldErrors?: { field: string; code: string; params?: TErrorParams }[];
  traceId?: string;
}

/** 목록 응답 `{ items, page, size, total }` (07 4장). page는 0부터 */
export interface IPage<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface IPageQuery {
  page?: number;
  size?: number;
}

// ───────── 인증 ─────────

export type TUserRole = 'USER' | 'ADMIN';
export type TUserStatus = 'ACTIVE' | 'SUSPENDED';

export interface IUserDto {
  id: string;
  email: string | null;
  name: string;
  createdAt: string;
  /** 07 2장에서 추가. 예전 서버는 없을 수 있다 */
  role?: TUserRole;
}

export interface IAuthResponse {
  accessToken: string;
  accessTokenExpiresIn: number;
  refreshToken: string;
  refreshTokenExpiresIn: number;
  user: IUserDto;
}

export interface ILoginRequest {
  email: string;
  password: string;
}

// ───────── 대시보드 ─────────

export interface IProviderStat7d {
  providerId: string;
  success: number;
  failed: number;
  avgLatencyMs: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
}

export interface IDashboard {
  users: number;
  newUsers7d: number;
  items: number;
  aiToday: { total: number; success: number; failed: number };
  aiByProvider7d: IProviderStat7d[];
  /** doc/16 4장: 살아 있는 로그인 세션 수. 예전 서버는 없다 → 카드 생략 */
  activeSessions?: number | null;
  /** doc/16 4장: 24시간 안에 활동한 회원 수. 예전 서버는 없다 → 카드 생략 */
  activeUsers24h?: number | null;
}

// ───────── 코드 (06 문서) ─────────

export type TGroupCode = 'CATEGORY' | 'SUB_TYPE' | 'COLOR' | 'PATTERN' | 'SEASON' | 'STYLE' | 'ITEM_STATUS';
export type TSlot = 'TOP' | 'BOTTOM' | 'DRESS' | 'OUTER' | 'SHOES' | 'BAG';

/** 그룹마다 쓰는 키가 다르다 (06 3장). 모르는 키도 그대로 보존한다 */
export interface ICodeAttrs {
  slot?: TSlot | null;
  volume?: number;
  warmth?: number;
  shape?: string;
  hex?: string;
  neutral?: boolean;
  light?: number;
  pairs?: string[];
  recommendable?: boolean;
  [key: string]: unknown;
}

export interface IAdminCode {
  code: string;
  label: string;
  parent: string | null;
  sortOrder: number;
  active: boolean;
  attrs: ICodeAttrs;
  /** 그 코드를 쓰는 아이템 수 */
  usageCount: number;
}

export interface IAdminCodeGroup {
  group: string;
  name: string;
  description?: string | null;
  codes: IAdminCode[];
}

export interface IAdminCodesResponse {
  version: string;
  groups: IAdminCodeGroup[];
}

export interface ICodeCreate {
  code: string;
  label: string;
  parent?: string | null;
  sortOrder?: number;
  active?: boolean;
  attrs: ICodeAttrs;
}

export interface ICodePatch {
  label?: string;
  parent?: string | null;
  sortOrder?: number;
  active?: boolean;
  attrs?: ICodeAttrs;
}

// ───────── AI 공급자 ─────────

export type TProviderType = 'CLAUDE' | 'OPENAI' | 'GEMINI' | 'MOCK';

/** 지금 실제로 쓰는 키의 출처 (07 6장). DB = 웹에서 넣은 키, ENV = 서버 설정 키 `apiKeyEnv` (backend/config/application-local.yml의 wearly.ai.keys.* 또는 같은 이름 환경변수) */
export type TKeySource = 'DB' | 'ENV' | 'NONE';

export interface IAiProvider {
  providerId: string;
  type: TProviderType | (string & {});
  displayName: string;
  model: string | null;
  apiKeyEnv: string;
  /** 쓸 수 있는 키가 있는지 (= keySource !== 'NONE'). 키 값 자체는 내려오지 않는다 */
  keyConfigured: boolean;
  /** 예전 서버처럼 내려오지 않으면 keyConfigured로 추정한다 ('ENV' / 'NONE') */
  keySource: TKeySource;
  /** DB 키일 때만. 키의 마지막 4자 */
  keyLast4: string | null;
  /** DB 키일 때만 */
  keyUpdatedAt: string | null;
  /** DB 키를 넣은 관리자 이메일 */
  keyUpdatedBy: string | null;
  /** 서버에 WEARLY_SECRET_KEY가 있어 웹 키를 쓸 수 있는지. 예전 서버라 모르면 null */
  secretStoreEnabled: boolean | null;
  enabled: boolean;
  priority: number;
  options: Record<string, unknown> | null;
  /** 사용·모델·키 모두 갖춤 */
  usable: boolean;
  today: { success: number; failed: number };
}

export interface IProviderCreate {
  providerId: string;
  type: string;
  displayName: string;
  model?: string | null;
  apiKeyEnv: string;
  enabled?: boolean;
  priority?: number;
  options?: Record<string, unknown>;
}

export type TProviderPatch = Partial<Omit<IProviderCreate, 'providerId'>>;

/**
 * `PUT /admin/ai/providers/{id}/api-key` 본문 (07 6장). 쓰기 전용 — 이 값은 mutation 변수로만 지나가고
 * URL·쿼리 문자열·브라우저 저장소·쿼리 캐시 키·로그에 남기지 않는다.
 */
export interface IApiKeySetRequest {
  apiKey: string;
  /** 요청한 관리자의 비밀번호 (재확인) */
  currentPassword: string;
  /** 저장 전에 그 키로 모델 목록을 한 번 조회해 유효한지 본다. 서버 기본 true */
  verify?: boolean;
}

/** `DELETE /admin/ai/providers/{id}/api-key` 본문 */
export interface IApiKeyDeleteRequest {
  currentPassword: string;
}

/** `/admin/ai/types`의 옵션 설명 하나 (화면 폼용). 서버 형식이 정해지지 않아 느슨하게 받는다 */
export interface IProviderOptionSpec {
  key: string;
  /** number | integer | string | boolean | enum 등 */
  type?: string;
  description?: string;
  default?: unknown;
  min?: number;
  max?: number;
  values?: string[];
}

export interface IProviderTypeSpec {
  type: string;
  label?: string;
  description?: string;
  defaultApiKeyEnv?: string;
  options: IProviderOptionSpec[];
  /** 내장 모델 목록 (서버 설정 wearly.ai.model-catalog, 추천 먼저). 예전 서버면 없다 */
  models?: IProviderModel[];
}

/** 모델 목록 출처: LIVE = 제공사에서 받은 목록, CATALOG = 서버 내장 목록 */
export type TModelSource = 'LIVE' | 'CATALOG';

/** 모델 드롭다운 한 줄 */
export interface IProviderModel {
  id: string;
  displayName: string;
  /** 제공사가 알려 준 출시 시각. 모르면 null (Gemini, 내장 목록) */
  createdAt: string | null;
  /** 내장 목록에서 추천으로 표시한 모델 */
  recommended: boolean;
}

/** `GET /admin/ai/providers/{providerId}/models` (07 4장) */
export interface IProviderModelList {
  source: TModelSource;
  /** LIVE면 제공사에서 받은 시각(캐시면 처음 받은 시각), CATALOG면 응답 시각 */
  fetchedAt: string | null;
  /** 추천 먼저, 최신순, ID 순 */
  models: IProviderModel[];
  /** CATALOG일 때 이유. code: NO_API_KEY(키 없음) · MODEL_LIST_FAILED(조회 실패) */
  error: { code: string; message: string } | null;
}

export interface IAiSuggestion {
  name?: string;
  category?: string;
  subType?: string;
  color?: string;
  pattern?: string;
  seasons?: string[];
  styles?: string[];
  formality?: number;
  confidence?: number;
  [key: string]: unknown;
}

export interface IProviderTestResult {
  ok: boolean;
  suggestion?: IAiSuggestion | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  latencyMs: number;
  inputTokens?: number | null;
  outputTokens?: number | null;
  model: string | null;
}

// ───────── AI 분석 기록 ─────────

export type TAiLogStatus = 'SUCCESS' | 'FAILED';

export interface IAiLogRow {
  id: string;
  createdAt: string;
  userEmail: string | null;
  /** ITEM_ANALYZE · ADMIN_TEST */
  feature: string;
  providerId: string;
  model: string | null;
  status: TAiLogStatus | (string & {});
  latencyMs: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
  imageBytes: number | null;
  /** 결과 요약 (서버가 만든 짧은 문자열) */
  summary: string | null;
  errorCode: string | null;
}

export interface IAiLogQuery extends IPageQuery {
  providerId?: string;
  status?: string;
  userEmail?: string;
  /** YYYY-MM-DD */
  from?: string;
  to?: string;
}

/** 상세: 행 정보 + 결과 JSON 전체 */
export type TAiLogDetail = Partial<IAiLogRow> & { result?: unknown; [key: string]: unknown };

// ───────── 회원 ─────────

export interface IAdminUser {
  id: string;
  email: string | null;
  name: string;
  role: TUserRole;
  status: TUserStatus;
  createdAt: string;
  itemCount: number;
  aiToday: number;
}

export interface IUserQuery extends IPageQuery {
  q?: string;
  status?: string;
  role?: string;
}

export interface IUserPatch {
  status?: TUserStatus;
  role?: TUserRole;
}

// ───────── 로그인 세션 (doc/16 4장) ─────────
// 백엔드 최종 응답 모양이 다르면 이 절과 api/client.ts의 toSession·toAuthEvent·toUserSessions만 고치면 된다.

/** 로그인한 곳. 서버가 X-Wearly-Client 헤더(없으면 User-Agent)로 정한다. 모르는 값도 그대로 받는다 */
export type TSessionClient = 'APP' | 'ADMIN' | 'WEB' | (string & {});

/** `GET /admin/sessions`, `GET /admin/users/{id}/sessions`의 세션 한 줄 */
export interface IAdminSession {
  sessionId: string;
  userId: string;
  email: string | null;
  name: string;
  role: TUserRole | null;
  client: TSessionClient;
  userAgent: string | null;
  ip: string | null;
  /** 로그인 시각 (ISO) */
  createdAt: string | null;
  lastRefreshAt: string | null;
  /** 마지막 인증 요청 시각 (서버는 1분에 한 번만 갱신) */
  lastSeenAt: string | null;
  /** 요청한 관리자 자신의 지금 세션 — 끊을 수 없다(400) */
  current: boolean;
}

export interface ISessionQuery extends IPageQuery {
  /** 이메일·이름 검색 */
  q?: string;
  /** APP · ADMIN · WEB */
  client?: string;
}

export type TAuthEventType = 'LOGIN' | 'LOGOUT' | 'REFRESH_REUSED' | 'FORCED_LOGOUT' | (string & {});

/** 회원별 최근 로그인 이력 (`tb_auth_event`) */
export interface IAuthEvent {
  type: TAuthEventType;
  sessionId: string | null;
  client: TSessionClient | null;
  ip: string | null;
  userAgent: string | null;
  createdAt: string;
}

/** `GET /admin/users/{id}/sessions` */
export interface IUserSessions {
  sessions: IAdminSession[];
  /** 최근 이력 (최신순, 최대 20개) */
  events: IAuthEvent[];
}

// ───────── 변경 이력 ─────────

export interface IAuditLog {
  id: string;
  adminUserId: string | null;
  adminEmail?: string | null;
  adminName?: string | null;
  action: string;
  targetType: string;
  targetId: string | null;
  before: unknown;
  after: unknown;
  createdAt: string;
}

export interface IAuditQuery extends IPageQuery {
  targetType?: string;
}

// ───────── 앱 설정 (doc/12 3장) ─────────

/**
 * `GET·PUT /admin/app/theme` 응답. 서버는 어떤 테마를 쓸지만 저장한다(테마 값은 앱 코드에 있다).
 * themeId는 계약상 'nyang'·'closeta'지만, 이 폴더는 앱 코드를 가져오지 않으므로 문자열로 받고 화면이 확인한다.
 */
export interface IAppTheme {
  themeId: string;
  /**
   * 계절 테마 모드 (이슈 #6): OFF · AUTO · SPRING · SUMMER · FALL · WINTER.
   * 서버가 아직 모르면(옛 서버) null — 화면은 계절 모드 고르기를 막는다
   */
  seasonMode: string | null;
  /** 마지막 변경 시각 (ISO). 초기값이면 null일 수 있다 */
  updatedAt: string | null;
  /** 바꾼 관리자 이메일. 초기값이거나 계정이 지워졌으면 null */
  updatedBy: string | null;
}

/** PUT /admin/app/theme 본문. 보낸 값만 바뀐다(빠진 값은 그대로) */
export interface IAppThemeUpdate {
  themeId?: string;
  seasonMode?: string;
}

// ───────── 날씨 (doc/17 6장) ─────────

export type TWeatherCond = 'SUNNY' | 'CLOUDY' | 'RAIN';
/** 키 상태. 키 값은 어떤 응답에도 없다 */
export type TWeatherKeyState = 'CONFIGURED' | 'MISSING' | 'MOCK' | 'UNSUPPORTED';
/** OK 정상 · OPEN 차단 중 · HALF_OPEN 다음 실행에서 시험 · KEY_INVALID 키 확인 필요 */
export type TWeatherProviderStatus = 'OK' | 'OPEN' | 'HALF_OPEN' | 'KEY_INVALID';
export type TWeatherTrigger = 'SCHEDULE' | 'MANUAL' | 'STARTUP' | 'TEST';
export type TWeatherRunStatus = 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'SKIPPED';

export interface IWeatherCurrent {
  temp: number;
  feelsLike: number | null;
  cond: TWeatherCond;
  condText: string | null;
  observedAt: string | null;
}

export interface IWeatherDay {
  /** YYYY-MM-DD (그 지역 날짜) */
  date: string;
  temp: number;
  min: number;
  max: number;
  rainProb: number;
  cond: TWeatherCond;
  condText: string | null;
}

export interface IWeatherSetting {
  enabled: boolean;
  intervalMinutes: number;
  forecastDays: number;
  failureThreshold: number;
  cooldownMinutes: number;
  updatedBy: string | null;
  updatedAt: string | null;
}

export type TWeatherSettingUpdate = Omit<IWeatherSetting, 'updatedBy' | 'updatedAt'>;

export interface IWeatherRun {
  runId: string;
  trigger: TWeatherTrigger;
  requestedBy: string | null;
  requestedByEmail: string | null;
  /** 한 지역만 실행했으면 그 지역 */
  regionCode: string | null;
  status: TWeatherRunStatus;
  skipReason: 'DISABLED' | 'NO_PROVIDER' | 'LOCKED' | null;
  successCount: number;
  failCount: number;
  /** 1순위가 아닌 공급자로 받은 지역 수 (예비 공급자를 붙이면 쓴다) */
  fallbackCount: number;
  startedAt: string;
  finishedAt: string | null;
}

/** `GET·PUT /admin/weather/settings` */
export interface IWeatherSchedule {
  setting: IWeatherSetting;
  /** 꺼져 있거나 서버 주기 실행이 꺼져 있거나 아직 실행한 적이 없으면 null */
  nextRunAt: string | null;
  running: boolean;
  lastRun: IWeatherRun | null;
  activeRegions: number;
  /** 서버 설정 wearly.weather.scheduler-enabled */
  schedulerEnabled: boolean;
}

export interface IWeatherProvider {
  code: string;
  displayName: string;
  enabled: boolean;
  priority: number;
  /** 서버 설정의 키 이름 (wearly.weather.keys.<keyName>) */
  keyName: string;
  keyState: TWeatherKeyState;
  status: TWeatherProviderStatus;
  consecutiveFailures: number;
  circuitOpenUntil: string | null;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  lastError: string | null;
  monthUsage: number;
  /** null이면 한도 없음 */
  monthlyQuota: number | null;
  maxForecastDays: number;
  callsPerFetch: number;
  usable: boolean;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface IWeatherProviderPatch {
  enabled?: boolean;
  priority?: number;
  monthlyQuota?: number | null;
  maxForecastDays?: number;
}

export interface IWeatherTestResult {
  runId: string;
  providerCode: string;
  regionCode: string;
  success: boolean;
  errorCode: string | null;
  errorMessage: string | null;
  httpStatus: number | null;
  durationMs: number;
  locationName: string | null;
  current: IWeatherCurrent | null;
  days: IWeatherDay[];
  raw: Record<string, unknown>;
}

/**
 * 날씨 공급자 HTTP 호출 한 번 (tb_weather_call_log). 실행 밖 호출(callKind ON_DEMAND·SEARCH·CLIMATE, doc/19)은
 * runId·trigger가 null이다.
 */
export interface IWeatherCall {
  id: string;
  /** 실행 밖 호출이면 null */
  runId: string | null;
  /** 실행 밖 호출이면 null */
  trigger: TWeatherTrigger | null;
  /** FORECAST는 REGION 코드, ON_DEMAND·CLIMATE는 격자 키("35.7,139.7"), SEARCH는 null */
  regionCode: string | null;
  /** WEATHERAPI 등 공급자 표의 코드, 또는 보조 공급자 OPEN_METEO(평년값)·OPEN_METEO_GEO(장소 검색) */
  providerCode: string;
  /** FORECAST · ON_DEMAND · SEARCH · CLIMATE (lib/weather.ts의 TCallKind) */
  callKind: string;
  fallback: boolean;
  httpStatus: number | null;
  success: boolean;
  errorCode: string | null;
  errorMessage: string | null;
  durationMs: number;
  responseBytes: number | null;
  calledAt: string;
}

export interface IWeatherRunDetail {
  run: IWeatherRun;
  calls: IWeatherCall[];
}

export interface IWeatherRunQuery extends IPageQuery {
  trigger?: string;
  status?: string;
}

/** GET /admin/weather/calls 필터. callKind는 서버가 거른다 */
export interface IWeatherCallQuery extends IPageQuery {
  providerCode?: string;
  /** FORECAST · ON_DEMAND · SEARCH · CLIMATE */
  callKind?: string;
  regionCode?: string;
  /** 'true' | 'false' */
  success?: string;
  fallback?: string;
  errorCode?: string;
  /** YYYY-MM-DD (한국 날짜) */
  from?: string;
  to?: string;
}

export interface IWeatherCallSummary {
  today: { calls: number; failures: number; fallbacks: number; avgDurationMs: number | null };
  months: { providerCode: string; displayName: string; used: number; quota: number | null }[];
  /** 공급자 표 밖 보조 공급자(OPEN_METEO·OPEN_METEO_GEO)의 이번 달 호출 수. 0이어도 항상 온다 */
  auxiliaryMonths?: { providerCode: string; displayName: string; used: number }[];
}

export interface IWeatherSnapshot {
  regionCode: string;
  regionLabel: string;
  regionActive: boolean;
  providerCode: string | null;
  current: IWeatherCurrent | null;
  days: IWeatherDay[];
  locationName: string | null;
  fetchedAt: string | null;
  stale: boolean;
  lastError: string | null;
  lastErrorAt: string | null;
  /** 상세에서만 */
  raw: Record<string, unknown> | null;
}
