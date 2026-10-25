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

// ───────── 관리자 2단계 인증 (TOTP, doc/22) ─────────

/**
 * 2단계 인증이 필요한 관리자의 로그인 응답 (`POST /auth/login`, X-Wearly-Client: admin).
 * 토큰·user가 없고, mfaToken(5분짜리 대기 토큰)으로 /auth/mfa/setup·verify만 부를 수 있다. 화면은 메모리에만 둔다.
 */
export interface IMfaPendingResponse {
  mfa: { required: boolean; enrolled: boolean };
  mfaToken: string;
  /** 대기 토큰 수명(초) */
  mfaTokenExpiresIn: number;
}

/** `POST /auth/login` 응답: 토큰(일반 회원·2단계 인증이 필요 없는 관리자) 또는 2단계 인증 대기 */
export type TLoginResponse = IAuthResponse | IMfaPendingResponse;

/** api.auth.login 결과. mfa면 토큰을 저장하지 않았다 */
export type TLoginResult =
  { kind: 'signedIn'; auth: IAuthResponse } | { kind: 'mfa'; mfaToken: string; enrolled: boolean; expiresIn: number };

/** TOTP 등록 시작 응답 (`POST /auth/mfa/setup`, `POST /admin/me/mfa/setup`). 다시 부르면 새 비밀이 나온다 */
export interface IMfaSetupResponse {
  /** 수동 입력용 BASE32 비밀 */
  secret: string;
  /** otpauth://totp/… — QR로 보여 준다 */
  otpauthUrl: string;
}

/** `POST /auth/mfa/verify` 본문. code(6자리) 또는 recoveryCode(등록된 관리자만) 하나 */
export interface IMfaVerifyRequest {
  mfaToken: string;
  code?: string;
  recoveryCode?: string;
}

/** `POST /auth/mfa/verify` 응답 */
export interface IMfaVerifyResponse extends IAuthResponse {
  /** 처음 등록할 때만 10개(한 번만 보여 준다). 그 밖에는 null */
  recoveryCodes: string[] | null;
  /** 남은 복구 코드 수. 3 이하이면 다시 등록하라고 알린다 */
  recoveryCodesLeft: number | null;
}

/** `GET /admin/me/mfa` */
export interface IMyMfaStatus {
  enrolled: boolean;
  enrolledAt: string | null;
  recoveryCodesLeft: number | null;
  /** 서버 설정 wearly.admin.mfa.required */
  required: boolean;
}

/** `POST /admin/me/password` */
export interface IPasswordChangeRequest {
  currentPassword: string;
  /** 8~64자, 영문과 숫자 포함 */
  newPassword: string;
}

/** `POST /admin/me/mfa/reset`. code 또는 recoveryCode 하나 */
export interface IMfaResetRequest {
  currentPassword: string;
  code?: string;
  recoveryCode?: string;
}

/** `POST /admin/me/mfa/enable` 응답 */
export interface IMfaEnableResponse {
  recoveryCodes: string[];
}

// ───────── 회사 계정 로그인 (SSO, doc/22 4.2) ─────────

/** 로그인 화면의 회사 계정 버튼 하나 (`GET /auth/sso/providers`) */
export interface ISsoProvider {
  /** 경로에 쓰는 공급자 ID (keycloak 등) */
  id: string;
  displayName: string;
}

/** `GET /auth/sso/providers`. SSO가 꺼져 있으면 enabled false, providers 빈 목록 */
export interface ISsoProvidersResponse {
  enabled: boolean;
  providers: ISsoProvider[];
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

export type TGroupCode =
  | 'CATEGORY'
  | 'SUB_TYPE'
  | 'COLOR'
  | 'PATTERN'
  | 'SEASON'
  | 'STYLE'
  | 'ITEM_STATUS'
  // 나의 정보 (프로필): 연령대·체형·핏 선호
  | 'AGE_RANGE'
  | 'BODY_TYPE'
  | 'FIT';
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
  /** BODY_TYPE: 짧은 설명 */
  description?: string;
  /** BODY_TYPE: 고르기를 돕는 한 줄 특징 */
  hint?: string;
  /** MALL: 앱이 웹 화면으로 처음 여는 주소 (https) */
  url?: string;
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
  /** 주 색상 (= colors[0]) */
  color?: string;
  /** 색 1~3개, 첫 번째가 주 색상 (이슈 #4). 예전 서버는 없다 */
  colors?: string[];
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
  /** 나의 정보 (조회만, 없으면 null. 예전 서버는 필드 없음): 연령대 AGE_RANGE 코드 */
  ageRange?: string | null;
  /** 체형 BODY_TYPE 코드 */
  bodyType?: string | null;
  /** 키 (cm) */
  heightCm?: number | null;
  /** 핏 선호 FIT 코드 */
  fitPreference?: string | null;
  /** TOTP를 등록했는지 (관리자만 의미 있음). 예전 서버면 false */
  mfaEnrolled: boolean;
  /** 이 회원을 초대한 추천인 (doc/25, 없으면 null. 예전 서버는 필드 없음) */
  referrerId?: string | null;
  referrerEmail?: string | null;
  referrerName?: string | null;
  /** 이 회원의 초대 코드로 가입한 친구 수. 예전 서버면 0 */
  invitedCount: number;
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

// ───────── 친구 초대 (doc/25) ─────────

/** 초대 코드 가입 결과: 추천인 연결 · 없는 코드(추천인 없이 가입) */
export type TInviteSignupResult = 'LINKED' | 'UNKNOWN_CODE' | (string & {});

/** 초대를 보낸 방법: 공유 시트(카카오톡 등) · 문자 · 코드 복사 */
export type TInviteChannel = 'SHARE' | 'SMS' | 'COPY' | (string & {});

/** `GET /admin/invites/signups` 한 줄: 초대 코드를 넣고 가입한 회원 */
export interface IInviteSignupRow {
  id: string;
  createdAt: string;
  userId: string;
  userEmail: string | null;
  userName: string;
  /** 가입자가 입력한 코드 (공백·하이픈을 빼고 대문자로 정리한 값) */
  enteredCode: string;
  result: TInviteSignupResult;
  /** 추천인 (UNKNOWN_CODE거나 추천인이 탈퇴하면 null) */
  referrerId: string | null;
  referrerEmail: string | null;
  referrerName: string | null;
}

/** `GET /admin/invites/shares` 한 줄: 앱에서 초대를 보낸 기록 */
export interface IInviteShareRow {
  id: string;
  createdAt: string;
  userId: string;
  userEmail: string | null;
  userName: string;
  channel: TInviteChannel;
}

export interface IInviteSignupQuery extends IPageQuery {
  /** 가입자·추천인 이메일·이름, 입력 코드 */
  q?: string;
  result?: string;
}

export interface IInviteShareQuery extends IPageQuery {
  /** 회원 이메일·이름 */
  q?: string;
  channel?: string;
}

// ───────── 옷장 도우미 (doc/26 6단계) ─────────

/** 연결 상태: 주인 승인 대기 · 연결됨 · 끝남 */
export type TPairingStatus = 'PENDING' | 'ACTIVE' | 'ENDED' | (string & {});

/** 돕는 사람 권한: 보기 · 등록·수정 */
export type TPairingPermission = 'VIEW' | 'EDIT' | (string & {});

/** 연결을 끝낸 쪽: 주인 · 돕는 사람 · 승인 대기 만료 · 관리자 */
export type TPairingEndedBy = 'OWNER' | 'HELPER' | 'EXPIRED' | 'ADMIN' | 'WITHDRAWN' | (string & {});

/** 돕는 사람이 한 일 */
export type TProxyAction = 'ITEM_CREATE' | 'ITEM_UPDATE' | 'ITEM_RETIRE' | 'ITEM_RESTORE' | (string & {});

/** `GET /admin/pairings` 한 줄: 주인과 돕는 사람의 연결 */
export interface IPairingRow {
  id: string;
  status: TPairingStatus;
  /** 승인 전이면 null */
  permission: TPairingPermission | null;
  /** 연결 코드를 만든 쪽 (OWNER · HELPER) */
  issuedBy: string;
  notifyOwner: boolean;
  createdAt: string;
  approvedAt: string | null;
  endedAt: string | null;
  endedBy: TPairingEndedBy | null;
  ownerId: string;
  ownerEmail: string | null;
  ownerName: string;
  helperId: string;
  helperEmail: string | null;
  helperName: string;
  /** 돕는 사람이 한 일 수 */
  actionCount: number;
  lastActionAt: string | null;
}

/** `GET /admin/pairings/{id}/actions` 한 줄 */
export interface IPairingAction {
  id: string;
  action: TProxyAction;
  /** 주인이 완전 삭제했으면 지금은 없을 수 있다 */
  itemId: string;
  /** 기록 당시 옷 이름 */
  itemName: string | null;
  createdAt: string;
}

export interface IPairingQuery extends IPageQuery {
  /** 주인·돕는 사람 이메일·이름 */
  q?: string;
  status?: string;
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

export type TAuthEventType =
  | 'LOGIN'
  | 'LOGOUT'
  | 'REFRESH_REUSED'
  | 'FORCED_LOGOUT'
  | 'LOGIN_FAILED'
  | 'MFA_SUCCESS'
  | 'MFA_FAILED'
  | 'MFA_LOCKED'
  /** 회사 계정(SSO)으로 로그인 (doc/22 4.2) */
  | 'SSO_LOGIN'
  /** IdP 인증은 됐지만 관리자 그룹이 아니라 거부 (연결된 회원만) */
  | 'SSO_DENIED'
  | (string & {});

/** 회원별 최근 로그인 이력 (`tb_auth_event`) */
export interface IAuthEvent {
  type: TAuthEventType;
  sessionId: string | null;
  client: TSessionClient | null;
  ip: string | null;
  userAgent: string | null;
  createdAt: string;
  /** SSO 로그인·거부의 공급자 ID (그 밖은 null, 예전 서버도 null) */
  ssoProvider: string | null;
  /** SSO 로그인 때 IdP가 알려 준 인증 방법 amr (예: pwd,otp). 없으면 null */
  ssoAmr: string | null;
}

/** `GET /admin/users/{id}/sessions` */
export interface IUserSessions {
  sessions: IAdminSession[];
  /** 최근 이력 (최신순, 최대 20개) */
  events: IAuthEvent[];
}

// ───────── 관리자 로그인 기록 (doc/22) ─────────

/** `GET /admin/auth-events` 한 줄: 지금 관리자인 회원의 이벤트 + 관리자 사이트에서 일어난 모든 이벤트 */
export interface IAuthEventRow extends IAuthEvent {
  id: string;
  userId: string;
  email: string | null;
  name: string | null;
  role: TUserRole | null;
}

export interface IAuthEventQuery extends IPageQuery {
  /** 회원 ID (숫자 문자열) */
  userId?: string;
  /** TAuthEventType 하나 */
  type?: string;
  /** YYYY-MM-DD (한국 날짜, to 포함) */
  from?: string;
  to?: string;
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
