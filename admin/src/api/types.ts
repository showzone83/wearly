// 관리자 API 타입 (doc/05_백엔드_API.md 6장 인증, doc/06_코드_관리.md, doc/07_관리자_사이트.md 4장).
// 앱(mobile/) 코드는 가져오지 않고 계약 문서만 보고 정의한다.

// ───────── 공통 ─────────

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  fieldErrors?: FieldError[];
}

/** 목록 응답 `{ items, page, size, total }` (07 4장). page는 0부터 */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface PageQuery {
  page?: number;
  size?: number;
}

// ───────── 인증 ─────────

export type UserRole = 'USER' | 'ADMIN';
export type UserStatus = 'ACTIVE' | 'SUSPENDED';

export interface UserDto {
  id: string;
  email: string | null;
  name: string;
  createdAt: string;
  /** 07 2장에서 추가. 예전 서버는 없을 수 있다 */
  role?: UserRole;
}

export interface AuthResponse {
  accessToken: string;
  accessTokenExpiresIn: number;
  refreshToken: string;
  refreshTokenExpiresIn: number;
  user: UserDto;
}

export interface LoginRequest {
  email: string;
  password: string;
}

// ───────── 대시보드 ─────────

export interface ProviderStat7d {
  providerId: string;
  success: number;
  failed: number;
  avgLatencyMs: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
}

export interface Dashboard {
  users: number;
  newUsers7d: number;
  items: number;
  aiToday: { total: number; success: number; failed: number };
  aiByProvider7d: ProviderStat7d[];
}

// ───────── 코드 (06 문서) ─────────

export type GroupCode = 'CATEGORY' | 'SUB_TYPE' | 'COLOR' | 'PATTERN' | 'SEASON' | 'STYLE' | 'ITEM_STATUS';
export type Slot = 'TOP' | 'BOTTOM' | 'DRESS' | 'OUTER' | 'SHOES' | 'BAG';

/** 그룹마다 쓰는 키가 다르다 (06 3장). 모르는 키도 그대로 보존한다 */
export interface CodeAttrs {
  slot?: Slot | null;
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

export interface AdminCode {
  code: string;
  label: string;
  parent: string | null;
  sortOrder: number;
  active: boolean;
  attrs: CodeAttrs;
  /** 그 코드를 쓰는 아이템 수 */
  usageCount: number;
}

export interface AdminCodeGroup {
  group: string;
  name: string;
  description?: string | null;
  codes: AdminCode[];
}

export interface AdminCodesResponse {
  version: string;
  groups: AdminCodeGroup[];
}

export interface CodeCreate {
  code: string;
  label: string;
  parent?: string | null;
  sortOrder?: number;
  active?: boolean;
  attrs: CodeAttrs;
}

export interface CodePatch {
  label?: string;
  parent?: string | null;
  sortOrder?: number;
  active?: boolean;
  attrs?: CodeAttrs;
}

// ───────── AI 공급자 ─────────

export type ProviderType = 'CLAUDE' | 'OPENAI' | 'GEMINI' | 'MOCK';

export interface AiProvider {
  providerId: string;
  type: ProviderType | (string & {});
  displayName: string;
  model: string | null;
  apiKeyEnv: string;
  /** 키 환경변수에 값이 있는지. 키 값 자체는 내려오지 않는다 */
  keyConfigured: boolean;
  enabled: boolean;
  priority: number;
  options: Record<string, unknown> | null;
  /** 사용·모델·키 모두 갖춤 */
  usable: boolean;
  today: { success: number; failed: number };
}

export interface ProviderCreate {
  providerId: string;
  type: string;
  displayName: string;
  model?: string | null;
  apiKeyEnv: string;
  enabled?: boolean;
  priority?: number;
  options?: Record<string, unknown>;
}

export type ProviderPatch = Partial<Omit<ProviderCreate, 'providerId'>>;

/** `/admin/ai/types`의 옵션 설명 하나 (화면 폼용). 서버 형식이 정해지지 않아 느슨하게 받는다 */
export interface ProviderOptionSpec {
  key: string;
  /** number | integer | string | boolean | enum 등 */
  type?: string;
  description?: string;
  default?: unknown;
  min?: number;
  max?: number;
  values?: string[];
}

export interface ProviderTypeSpec {
  type: string;
  label?: string;
  description?: string;
  defaultApiKeyEnv?: string;
  options: ProviderOptionSpec[];
}

export interface AiSuggestion {
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

export interface ProviderTestResult {
  ok: boolean;
  suggestion?: AiSuggestion | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  latencyMs: number;
  inputTokens?: number | null;
  outputTokens?: number | null;
  model: string | null;
}

// ───────── AI 분석 기록 ─────────

export type AiLogStatus = 'SUCCESS' | 'FAILED';

export interface AiLogRow {
  id: string;
  createdAt: string;
  userEmail: string | null;
  /** ITEM_ANALYZE · ADMIN_TEST */
  feature: string;
  providerId: string;
  model: string | null;
  status: AiLogStatus | (string & {});
  latencyMs: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
  imageBytes: number | null;
  /** 결과 요약 (서버가 만든 짧은 문자열) */
  summary: string | null;
  errorCode: string | null;
}

export interface AiLogQuery extends PageQuery {
  providerId?: string;
  status?: string;
  userEmail?: string;
  /** YYYY-MM-DD */
  from?: string;
  to?: string;
}

/** 상세: 행 정보 + 결과 JSON 전체 */
export type AiLogDetail = Partial<AiLogRow> & { result?: unknown; [key: string]: unknown };

// ───────── 회원 ─────────

export interface AdminUser {
  id: string;
  email: string | null;
  name: string;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  itemCount: number;
  aiToday: number;
}

export interface UserQuery extends PageQuery {
  q?: string;
  status?: string;
  role?: string;
}

export interface UserPatch {
  status?: UserStatus;
  role?: UserRole;
}

// ───────── 변경 이력 ─────────

export interface AuditLog {
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

export interface AuditQuery extends PageQuery {
  targetType?: string;
}
