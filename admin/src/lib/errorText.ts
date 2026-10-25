// 서버 오류 코드 → 관리자 화면 한국어 문구 (기준: backend/src/main/resources/error-codes.json).
// 서버 오류 응답에는 문장(message)이 없다: { status, code, params?, fieldErrors: [{ field, code, params? }], traceId }.
// 화면은 code(+params)로 이 표에서 문구를 고르고, 모르는 코드는 HTTP 상태별 기본 문구를 쓴다.
// 카탈로그와 이 표가 어긋나지 않는지 tests/errorText.test.mjs가 확인한다.
// 값을 import하지 않는다 — node --test가 이 파일을 바로 읽는다(타입 import는 지워진다).

export type TErrorParams = Record<string, unknown>;

/** 문구 하나: 고정 문장 또는 params로 만드는 함수 */
type TText = string | ((p: TErrorParams) => string);

/** 필드 오류 한 줄 (서버 fieldErrors[]) */
export interface IFieldErrorLike {
  field: string;
  code: string;
  params?: TErrorParams;
}

/** 문구를 고르는 데 필요한 오류 모양 (api/errors.ts의 ApiError가 이 모양이다) */
export interface IErrorLike {
  /** HTTP 상태. 서버에 닿지 못했으면 0 */
  status: number;
  code: string;
  params?: TErrorParams;
  fieldErrors?: readonly IFieldErrorLike[];
  traceId?: string | null;
}

// ───────── params 읽기 ─────────

const str = (v: unknown): string | null => {
  if (v === undefined || v === null) return null;
  const s = String(v).trim();
  return s ? s : null;
};

const num = (v: unknown): number | null => {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null;
  if (typeof v === 'string' && v.trim() && Number.isFinite(Number(v))) return Number(v);
  return null;
};

const list = (v: unknown): string[] => {
  if (Array.isArray(v)) return v.map((x) => str(x)).filter((x): x is string => x !== null);
  const s = str(v);
  return s
    ? s
        .split(',')
        .map((x) => x.trim())
        .filter(Boolean)
    : [];
};

// ───────── HTTP 상태별 기본 문구 (모르는 코드·관리자와 무관한 코드) ─────────

/** 시스템 오류(5xx) 공통 문구. 자세한 내용은 서버 로그에서 traceId로 찾는다 */
const SYSTEM_TEXT = '일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.';

export const statusText = (status: number): string => {
  switch (status) {
    case 0:
      return '서버에 연결할 수 없어요. 백엔드가 켜져 있는지 확인해 주세요.';
    case 400:
      return '요청 내용을 확인해 주세요.';
    case 401:
      return '로그인이 필요해요.';
    case 403:
      return '권한이 없어요.';
    case 404:
      return '찾을 수 없어요.';
    case 405:
    case 406:
    case 415:
      return '지원하지 않는 요청이에요.';
    case 409:
      return '다른 데이터와 충돌해요. 새로 고친 뒤 다시 시도해 주세요.';
    case 413:
      return '보낸 파일이 너무 커요.';
    case 429:
      return '요청이 너무 많아요. 잠시 후 다시 시도해 주세요.';
    default:
      if (status >= 500) return SYSTEM_TEXT;
      return `요청을 처리하지 못했어요. (HTTP ${status})`;
  }
};

// ───────── params 해석 ─────────

/** API_KEY_INVALID.reason: 공급자 사유 코드 (API_<HTTP 상태>, TIMEOUT, API_FAILED, API_CLIENT, INVALID_RESPONSE, BAD_RESPONSE) */
export const apiKeyReasonText = (reason: unknown): string => {
  const r = str(reason);
  if (!r) return '제공사에서 키를 확인하지 못했어요.';
  switch (r) {
    case 'API_401':
      return '제공사가 키를 거절했어요 (401). 키가 맞는지, 지워지거나 만료되지 않았는지 확인해 주세요.';
    case 'API_403':
      return '제공사가 이 키의 접근을 막았어요 (403). 키 권한·조직·결제 상태를 확인해 주세요.';
    case 'API_404':
      return '제공사 API 주소를 찾지 못했어요 (404). 공급자 종류와 API 주소를 확인해 주세요.';
    case 'API_429':
      return '제공사 호출 한도에 걸렸어요 (429). 잠시 후 다시 시도하거나 한도·결제 상태를 확인해 주세요.';
    case 'TIMEOUT':
      return '제공사 응답이 너무 늦어요. 잠시 후 다시 시도하거나 "저장 전 유효성 확인"을 끄고 저장해 주세요.';
    case 'API_CLIENT':
      return '제공사에 연결하지 못했어요. 서버의 네트워크·API 주소를 확인해 주세요.';
    case 'API_FAILED':
      return '제공사 호출이 실패했어요.';
    case 'INVALID_RESPONSE':
    case 'BAD_RESPONSE':
      return '제공사 응답을 읽지 못했어요.';
    default: {
      const m = /^API_(\d{3})$/.exec(r);
      if (m) {
        const code = Number(m[1]);
        return code >= 500
          ? `제공사 서버 오류예요 (${code}). 잠시 후 다시 시도해 주세요.`
          : `제공사가 요청을 거절했어요 (${code}).`;
      }
      return `제공사에서 키를 확인하지 못했어요 (${r}).`;
    }
  }
};

/** MFA_CODE_INVALID.remainingAttempts → " (남은 횟수 3번)". 없으면 빈 문자열 */
export const remainingAttemptsText = (v: unknown): string => {
  const n = num(v);
  return n !== null && n >= 0 ? ` (남은 횟수 ${Math.floor(n)}번)` : '';
};

/** MFA_LOCKED.retryAfterSeconds → "15분" (분 단위 올림, 최소 1분). 없으면 null */
export const retryAfterMinutes = (v: unknown): number | null => {
  const n = num(v);
  return n !== null && n > 0 ? Math.max(1, Math.ceil(n / 60)) : null;
};

const USAGE_KIND_TEXT: Record<string, (n: number) => string> = {
  ITEM: (n) => `옷 ${n.toLocaleString('ko-KR')}개`,
  PROFILE: (n) => `회원 프로필 ${n.toLocaleString('ko-KR')}개`,
  SUB_TYPE: (n) => `세부 종류 ${n.toLocaleString('ko-KR')}개`,
  PLAN: (n) => `예정 코디 ${n.toLocaleString('ko-KR')}개`,
};

/** CODE_IN_USE.usages: [{ kind: ITEM|PROFILE|SUB_TYPE|PLAN, count }] → "옷 12개 · 회원 프로필 3개". 없으면 빈 문자열 */
export const codeUsagesText = (usages: unknown): string => {
  if (!Array.isArray(usages)) return '';
  return usages
    .map((u) => {
      const o = (u ?? {}) as Record<string, unknown>;
      const kind = str(o.kind) ?? '';
      const count = num(o.count) ?? 0;
      if (count <= 0) return null;
      return USAGE_KIND_TEXT[kind]?.(count) ?? `${kind} ${count.toLocaleString('ko-KR')}개`;
    })
    .filter((x): x is string => x !== null)
    .join(' · ');
};

const AI_FEATURE_TEXT: Record<string, string> = {
  ITEM_ANALYZE: '사진 분석',
  OUTFIT_RECOMMEND: '코디 추천',
};

const featureText = (v: unknown): string => {
  const f = str(v);
  return f ? (AI_FEATURE_TEXT[f] ?? f) : 'AI 기능';
};

/** INVALID_FORMAT.format → 기대 형식 설명 */
const FORMAT_TEXT: Record<string, string> = {
  'YYYY-MM-DD': 'YYYY-MM-DD 날짜',
  'date-time': '날짜·시각(ISO 8601)',
  '#RRGGBB': '#RRGGBB 색 (예: #1F3A5F)',
  url: 'URL (https://…)',
  json: 'JSON',
  lower: '영문 소문자·숫자·밑줄',
  upper: '영문 대문자·숫자·밑줄',
};

/** INVALID_TYPE.expected → 값 종류 */
const TYPE_TEXT: Record<string, string> = {
  boolean: '참/거짓',
  string: '문자열',
  integer: '정수',
  number: '숫자',
  object: '객체',
  array: '배열',
};

const MB = 1024 * 1024;

/** 바이트 → "10MB" */
const mbText = (bytes: number): string => {
  const mb = bytes / MB;
  return `${Number.isInteger(mb) ? mb : mb.toFixed(1)}MB`;
};

/** OUT_OF_RANGE: min·max 중 정해진 것만 온다. date는 YYYY-MM-DD, 사진 size는 바이트 */
const rangeText = (field: string, p: TErrorParams): string => {
  const base = field.replace(/\[\d+\]/g, '');
  const isDate = base === 'date' || base.endsWith('.date');
  const minRaw = str(p.min);
  const maxRaw = str(p.max);
  const maxNum = num(p.max);
  // 사진 크기(바이트). 목록의 size(쪽 크기)는 값이 작아 숫자 그대로 보인다
  if (base === 'size' && maxNum !== null && maxNum >= MB) {
    return `${mbText(maxNum)}까지 올릴 수 있어요.`;
  }
  // min·max는 포함 범위
  const what = isDate ? '날짜여야' : '값이어야';
  if (minRaw && maxRaw) return `${minRaw}~${maxRaw} 사이 ${what} 해요.`;
  if (minRaw) return isDate ? `${minRaw} 또는 그 뒤 날짜여야 해요.` : `${minRaw} 이상이어야 해요.`;
  if (maxRaw) return isDate ? `${maxRaw} 또는 그 앞 날짜여야 해요.` : `${maxRaw} 이하여야 해요.`;
  return '허용 범위를 벗어났어요.';
};

// ───────── 업무 오류 코드 표 ─────────

/**
 * error-codes.json의 errorCodes 전부. 값이 null이면 관리자 화면과 무관한 코드(앱 전용)라
 * HTTP 상태별 기본 문구를 쓴다 — 그래도 표에는 명시적으로 둔다(카탈로그 일치 테스트).
 */
/**
 * 로그인·2단계 인증 화면의 SECRET_KEY_MISSING. TOTP 비밀도 AI 키와 같은 마스터 키로 암호화하므로 같은 코드가 오지만,
 * 표의 문구(AI 키 웹 입력 안내)는 이 화면에 맞지 않는다. 키가 없을 때뿐 아니라 형식이 틀려도(base64 32바이트가 아님) 서버는 없다고 본다.
 */
export const MFA_SECRET_KEY_MISSING_TEXT =
  '서버 마스터 키(wearly.secret-key / WEARLY_SECRET_KEY)가 없거나 형식이 틀려 2단계 인증을 쓸 수 없어요. ' +
  '서버에 openssl rand -base64 32로 만든 값(44자)을 넣고 다시 시작해 주세요.';

export const SERVER_ERROR_TEXT: Record<string, TText | null> = {
  VALIDATION_FAILED: '입력값을 확인해 주세요.',
  UNAUTHORIZED: '로그인이 필요해요.',
  TOKEN_EXPIRED: '로그인이 만료됐어요. 다시 로그인해 주세요.',
  TOKEN_INVALID: '로그인 정보가 올바르지 않아요. 다른 곳에서 로그아웃됐거나 계정 상태가 바뀌었을 수 있어요.',
  INVALID_CREDENTIALS: '이메일 또는 비밀번호가 맞지 않아요.',
  REFRESH_INVALID: '로그인이 만료됐어요. 다시 로그인해 주세요.',
  REFRESH_REUSED: '보안을 위해 이 세션을 끊었어요. 다시 로그인해 주세요.',
  FORBIDDEN: '관리자 권한이 없어요.',
  REAUTH_FAILED: '비밀번호가 맞지 않아요. 내 관리자 비밀번호를 다시 입력해 주세요.',
  ACCOUNT_SUSPENDED: '이용이 정지된 계정이에요.',
  ACCOUNT_WITHDRAWN: '탈퇴를 신청한 계정이에요. 앱에서 다시 로그인해 탈퇴를 취소한 뒤 쓸 수 있어요.',
  NOT_FOUND: '찾을 수 없어요. 이미 삭제됐거나 서버가 이 기능을 아직 모를 수 있어요.',
  METHOD_NOT_ALLOWED: '서버가 이 요청 방식을 지원하지 않아요. 백엔드 버전을 확인해 주세요.',
  EMAIL_TAKEN: null, // 앱 가입
  CODE_EXISTS: '이미 있는 코드예요.',
  CODE_IN_USE: (p) => {
    const used = codeUsagesText(p.usages);
    return `사용 중인 코드라 삭제할 수 없어요${used ? ` (${used})` : ''}. 대신 비활성화해 주세요.`;
  },
  PROVIDER_EXISTS: '이미 있는 공급자 ID예요.',
  SECRET_KEY_MISSING:
    '서버에 마스터 키(wearly.secret-key / WEARLY_SECRET_KEY)가 없어 웹에서 API 키를 넣을 수 없어요. ' +
    '서버 설정 키(application-local.yml의 wearly.ai.keys.*)를 쓰거나 서버에 마스터 키를 설정해 주세요.',
  API_KEY_INVALID: (p) => `API 키를 확인하지 못했어요. ${apiKeyReasonText(p.reason)}`,
  WARDROBE_INSUFFICIENT: null, // 앱 코디 추천
  IMAGE_INVALID: null, // 앱 사진 올리기
  IMAGE_NOT_UPLOADED: null, // 앱 사진 올리기
  IMAGE_IN_USE: null, // 앱 사진 올리기
  IMAGE_UPLOAD_LIMIT: null, // 앱 사진 올리기
  IMAGE_TOO_LARGE: '사진이 너무 커요. 10MB 이하 사진을 올려 주세요.',
  UNSUPPORTED_MEDIA_TYPE: '지원하지 않는 형식이에요. JPEG·PNG·WebP·HEIC 사진을 올려 주세요.',
  PLAN_LIMIT: null, // 앱 예정 코디 개수 한도
  PAIRING_CODE_INVALID: null, // 앱 옷장 도우미 연결 (doc/26)
  PAIRING_SELF: null, // 앱 옷장 도우미 연결
  PAIRING_EXISTS: null, // 앱 옷장 도우미 연결
  PAIRING_LIMIT: null, // 앱 옷장 도우미 연결
  PAIRING_TOO_MANY_ATTEMPTS: null, // 앱 옷장 도우미 연결
  AI_LIMIT_EXCEEDED: null, // 앱 회원 일일 한도 (관리자 시험 분석은 한도에 세지 않는다)
  AI_UNAVAILABLE: (p) =>
    `AI ${featureText(p.feature)}을(를) 지금 할 수 없어요. AI 공급자의 사용 여부·키·오늘 실패를 확인해 주세요.`,
  WEATHER_UNAVAILABLE: null, // 앱 날씨 조회
  WEATHER_REFRESH_RUNNING: '날씨를 이미 받고 있어요. 지금 실행이 끝난 뒤 다시 시도해 주세요.',
  WEATHER_LIMIT: null, // 앱 회원 하루 한도
  // 관리자 계정 (doc/22)
  LAST_ADMIN: '마지막 관리자는 해제할 수 없어요. 다른 회원을 먼저 관리자로 지정해 주세요.',
  MFA_REQUIRED: '2단계 인증을 마쳐야 관리자 기능을 쓸 수 있어요. 다시 로그인해 주세요.',
  MFA_TOKEN_INVALID: '인증 시간이 지났어요. 처음부터 다시 로그인해 주세요.',
  MFA_CODE_INVALID: (p) => `인증 코드가 맞지 않아요.${remainingAttemptsText(p.remainingAttempts)}`,
  MFA_LOCKED: (p) => {
    const min = retryAfterMinutes(p.retryAfterSeconds);
    return `인증 코드를 여러 번 틀려 잠시 잠겼어요. ${min ? `${min}분 뒤 다시 시도해 주세요.` : '잠시 후 다시 시도해 주세요.'}`;
  },
  MFA_SETUP_REQUIRED: '2단계 인증 등록을 먼저 시작해 주세요.',
  MFA_ALREADY_ENROLLED: '이미 2단계 인증을 등록했어요.',
  MFA_NOT_ENROLLED: '2단계 인증을 등록하지 않았어요.',
  // 회사 계정 로그인 (SSO, doc/22 4.2). 콜백 실패는 /sso/callback?error=…로 온다
  SSO_NOT_ADMIN:
    '관리자 그룹에 속한 회사 계정이 아니에요. 회사 계정 관리자에게 관리자 그룹(예: wearly-admins)을 요청해 주세요.',
  SSO_EMAIL_UNVERIFIED:
    '회사 계정의 이메일을 확인할 수 없어 관리자 계정과 연결하지 못했어요. 회사 계정에 확인된 이메일이 있는지 확인해 주세요.',
  SSO_STATE_INVALID: '로그인 시간이 지났거나 다른 창에서 시작한 로그인이에요. 다시 시도해 주세요.',
  SSO_TOKEN_INVALID: '회사 계정 로그인 결과를 확인하지 못했어요. 다시 시도해 주세요.',
  SSO_IDP_ERROR: (p) =>
    str(p.reason) === 'access_denied'
      ? '회사 계정 로그인을 취소했어요.'
      : `회사 계정 로그인이 실패했어요.${str(p.reason) ? ` (${str(p.reason)})` : ''}`,
  SSO_PROVIDER_UNAVAILABLE: '회사 계정 로그인 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.',
  SSO_EXCHANGE_INVALID: '로그인 시간이 지났어요. 다시 로그인해 주세요.',
  WEATHER_NO_PROVIDER: (p) => {
    const reason = str(p.reason);
    if (reason === 'KEY_MISSING') {
      const key = str(p.keyName);
      return `서버 설정에 날씨 API 키${key ? `(${key})` : ''}가 없어요. backend/config/application-local.yml에 넣고 서버를 다시 시작해 주세요.`;
    }
    if (reason === 'NOT_IMPLEMENTED') return '서버에 이 날씨 공급자 구현이 없어요.';
    return '쓸 수 있는 날씨 공급자가 없어요. 공급자 사용 여부·키·차단 상태·월 한도를 확인해 주세요.';
  },
  INTERNAL_ERROR: SYSTEM_TEXT,
  NOT_ACCEPTABLE: '서버가 이 응답 형식을 줄 수 없어요.',
  CONFLICT: '다른 요청과 겹쳐 처리하지 못했어요. 새로 고친 뒤 다시 시도해 주세요.',
  SERVICE_UNAVAILABLE: '서버가 DB·Valkey에 잠시 닿지 못해요. 잠시 후 다시 시도해 주세요.',
};

/** 이 사이트(api/client.ts)가 만드는 코드 — 카탈로그에 없다 */
export const CLIENT_ERROR_TEXT: Record<string, TText> = {
  /** 서버에 닿지 못함. params.reason TIMEOUT이면 시간 초과 */
  NETWORK: (p) =>
    str(p.reason) === 'TIMEOUT'
      ? '서버 응답이 너무 늦어요. 잠시 후 다시 시도해 주세요.'
      : '서버에 연결할 수 없어요. 백엔드가 켜져 있는지 확인해 주세요.',
  /** 응답이 계약 형식이 아님 (본문이 JSON 오류가 아니거나 성공 응답을 읽지 못함) */
  HTTP_ERROR: (p) => (str(p.reason) === 'PARSE' ? '서버 응답을 읽지 못했어요.' : ''),
  /** 로그인한 계정이 관리자가 아님 */
  NOT_ADMIN: '관리자 계정이 아닙니다.',
};

// ───────── 필드 오류 코드 표 ─────────

/** error-codes.json의 fieldErrorCodes 전부 */
export const FIELD_ERROR_TEXT: Record<string, TText> = {
  REQUIRED: '필수 값이에요.',
  TOO_SHORT: (p) => (num(p.min) !== null ? `${num(p.min)}자 이상 입력해 주세요.` : '너무 짧아요.'),
  TOO_LONG: (p) => (num(p.max) !== null ? `${num(p.max)}자까지 쓸 수 있어요.` : '너무 길어요.'),
  INVALID_FORMAT: (p) => {
    const f = str(p.format);
    return f ? `형식이 올바르지 않아요. ${FORMAT_TEXT[f] ?? f} 형식으로 입력해 주세요.` : '형식이 올바르지 않아요.';
  },
  INVALID_TYPE: (p) => {
    const t = str(p.expected);
    return t ? `값의 종류가 올바르지 않아요. ${TYPE_TEXT[t] ?? t}이어야 해요.` : '값의 종류가 올바르지 않아요.';
  },
  // OUT_OF_RANGE는 필드에 따라 다르다(rangeText) — fieldErrorText가 먼저 처리한다
  OUT_OF_RANGE: '허용 범위를 벗어났어요.',
  NOT_ALLOWED: (p) => {
    const allowed = list(p.allowed);
    return allowed.length ? `허용되지 않는 값이에요. 가능한 값: ${allowed.join(', ')}` : '허용되지 않는 값이에요.';
  },
  INACTIVE: '지금은 고를 수 없는(비활성) 값이에요.',
  DUPLICATE: '이미 있는 값이에요.',
  NOT_FOUND: '없는 값이에요.',
  TOO_MANY: (p) => (num(p.max) !== null ? `${num(p.max)}개까지 보낼 수 있어요.` : '항목이 너무 많아요.'),
  IMMUTABLE: '바꿀 수 없는 값이에요.',
  RESERVED: '예약된 이름이라 쓸 수 없어요.',
  SELF_NOT_ALLOWED: '자기 자신에게는 할 수 없어요.',
  INCOMPLETE: (p) =>
    num(p.expected) !== null
      ? `${num(p.expected)}개를 빠짐없이 한 번씩 보내 주세요. 목록을 새로 고친 뒤 다시 시도해 주세요.`
      : '목록이 빠졌어요. 새로 고친 뒤 다시 시도해 주세요.',
  ITEM_RETIRED: '사용하지 않는 옷이에요.',
  NOT_OWNED: '내 옷장에 없는 아이템이에요.',
  SLOT_MISMATCH: (p) =>
    str(p.slot) ? `${str(p.slot)} 칸에 넣을 수 없는 아이템이에요.` : '그 칸에 넣을 수 없는 아이템이에요.',
  SUBTYPE_MISMATCH: '종류와 맞지 않는 세부 종류예요.',
  CONFLICT: '함께 쓸 수 없는 값이에요.',
  DISABLED_BY_SERVER: '서버 설정에서 허용하지 않은 항목이에요.',
};

/** 관리자 화면에서 더 자세히 알려 줄 필드별 문구 ("코드:필드", 필드의 [n]은 지운다) */
const FIELD_OVERRIDE_TEXT: Record<string, string> = {
  'RESERVED:providerId': '예약된 이름(mock)이라 공급자 ID로 쓸 수 없어요.',
  'IMMUTABLE:providerId': '공급자 ID는 바꿀 수 없어요.',
  'IMMUTABLE:code': '코드 문자열은 바꿀 수 없어요.',
  'SELF_NOT_ALLOWED:attrs.pairs': '자기 색은 어울리는 색에 넣을 수 없어요.',
  'SELF_NOT_ALLOWED:status': '자기 자신은 정지할 수 없어요.',
  'SELF_NOT_ALLOWED:role': '자기 자신의 관리자 역할은 내릴 수 없어요.',
  'CONFLICT:role': '정지된 회원은 관리자로 지정할 수 없어요. 먼저 정지를 해제해 주세요.',
  'SELF_NOT_ALLOWED:sessionId': '지금 쓰는 세션은 여기서 끊을 수 없어요. 로그아웃을 쓰세요.',
  // 세션 전체 끊기(DELETE /admin/users/{id}/sessions)와 2단계 인증 초기화(DELETE /admin/users/{id}/mfa)
  'SELF_NOT_ALLOWED:id': '자기 자신의 세션 전체 끊기·2단계 인증 초기화는 여기서 할 수 없어요.',
  'INVALID_FORMAT:newPassword': '8~64자로, 영문과 숫자를 함께 넣어 주세요.',
  'INVALID_FORMAT:code': '인증 앱의 6자리 숫자를 입력해 주세요.',
  'DISABLED_BY_SERVER:options.baseUrl': '서버 설정(wearly.ai.allow-base-url)이 꺼져 있어 API 주소를 바꿀 수 없어요.',
  'NOT_ALLOWED:attrs.pairs': '없는 색 코드가 들어 있어요.',
  'NOT_ALLOWED:parent': '상위 종류로 쓸 수 없는 코드예요.',
  'NOT_ALLOWED:options.unknown': '이 공급자 종류가 모르는 옵션이에요.',
  'INACTIVE:parent': '비활성화된 상위 종류예요.',
  'NOT_FOUND:regionCode': '없는 지역 코드예요.',
};

/** 필드 오류 한 줄 → 문구 (필드 이름은 붙이지 않는다) */
export const fieldErrorText = (f: IFieldErrorLike): string => {
  const p = f.params ?? {};
  const base = f.field.replace(/\[\d+\]/g, '');
  const override = FIELD_OVERRIDE_TEXT[`${f.code}:${base}`];
  if (override) return override;
  if (f.code === 'OUT_OF_RANGE') return rangeText(f.field, p);
  const t = FIELD_ERROR_TEXT[f.code];
  if (t === undefined) return '입력값을 확인해 주세요.';
  return typeof t === 'function' ? t(p) : t;
};

// ───────── 오류 한 건 → 문구 ─────────

const fromTable = (t: TText | null | undefined, p: TErrorParams): string | null => {
  if (t === null || t === undefined) return null;
  const s = typeof t === 'function' ? t(p) : t;
  return s || null;
};

/**
 * 화면에 보여 줄 한 줄 문구. VALIDATION_FAILED면 필드 오류를 "필드: 문구"로 덧붙인다
 * (입력칸 아래에 따로 보여 주는 화면은 fieldErrorText를 쓴다).
 */
export const errorText = (e: IErrorLike): string => {
  const p = e.params ?? {};
  const text =
    fromTable(SERVER_ERROR_TEXT[e.code], p) ?? fromTable(CLIENT_ERROR_TEXT[e.code], p) ?? statusText(e.status);
  const fields = e.fieldErrors ?? [];
  if (e.code === 'VALIDATION_FAILED' && fields.length) {
    return `${text} ${fields.map((f) => `${f.field}: ${fieldErrorText(f)}`).join(' / ')}`;
  }
  return text;
};

/** 시스템 오류(5xx)인가 — 문의할 때 쓸 오류 번호(traceId)를 보여 준다 */
export const isSystemError = (e: Pick<IErrorLike, 'status'>): boolean => e.status >= 500;

/** 시스템 오류면 전체 traceId, 아니면 null */
export const systemTraceId = (e: IErrorLike): string | null => (isSystemError(e) && e.traceId ? e.traceId : null);

/** 화면에 보이는 짧은 오류 번호 (traceId 앞 8자. 서버가 만든 값은 하이픈 없는 UUID 32자) */
export const shortTraceId = (traceId: string): string => traceId.slice(0, 8);

/** "오류 번호 ab12cd34" */
export const traceLabel = (traceId: string): string => `오류 번호 ${shortTraceId(traceId)}`;

/** 글자만 쓸 수 있는 곳(폼 안내 문구 등): 시스템 오류면 끝에 " (오류 번호 ab12cd34)" */
export const errorTextWithTrace = (e: IErrorLike): string => {
  const trace = systemTraceId(e);
  return trace ? `${errorText(e)} (${traceLabel(trace)})` : errorText(e);
};

/** 그 필드의 첫 오류 문구 (없으면 undefined) */
export const fieldErrorOf = (fields: readonly IFieldErrorLike[] | undefined, name: string): string | undefined => {
  const f = fields?.find((x) => x.field === name);
  return f ? fieldErrorText(f) : undefined;
};

/** 필드 오류를 입력칸별 문구로 (같은 칸은 첫 오류). key로 경로를 묶을 수 있다 (`attrs.pairs[1]` → `attrs.pairs`) */
export const fieldErrorMap = (
  fields: readonly IFieldErrorLike[],
  key: (field: string) => string = (f) => f,
): Map<string, string> => {
  const m = new Map<string, string>();
  for (const f of fields) {
    const k = key(f.field);
    if (!m.has(k)) m.set(k, fieldErrorText(f));
  }
  return m;
};

/** 입력칸이 없는 필드 오류를 한 줄로: "field: 문구 / field: 문구" */
export const fieldErrorsLine = (fields: readonly IFieldErrorLike[]): string => {
  return fields.map((f) => `${f.field}: ${fieldErrorText(f)}`).join(' / ');
};
