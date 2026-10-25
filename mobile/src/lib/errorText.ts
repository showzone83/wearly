// 오류 코드 → 화면 문구 표. 서버 오류 응답은 문장(message) 없이 { status, code, params?, fieldErrors, traceId }만 준다.
// 기준 카탈로그: backend/src/main/resources/error-codes.json (빠짐없이 맞는지 npm run check:errors가 확인한다).
// 순수 TS다 (React Native·Expo를 쓰지 않는다): 단위 점검을 Node에서 바로 돌린다.
// 서버 오류 상세는 서버 로그에만 있다. 화면은 여기 정한 문구와, 시스템 오류일 때만 오류 번호(traceId 앞 8자)를 보여 준다.
import { isApiError, type ApiError } from '@/api/errors';
import type { FieldError } from '@/api/types';

type TParams = Record<string, unknown>;
type TText = string | ((p: TParams) => string);
type TFieldText = string | ((p: TParams, field: string) => string);

/** 필드 오류 (서버 FieldError, message는 없어도 된다) */
export interface IFieldErrorLike {
  field: string;
  code?: string;
  params?: TParams;
  message?: string;
}

export interface IErrorTextContext {
  /** ApiError가 아닌 오류(기기 오류 등)에 보여 줄 문구 */
  fallback?: string;
  /** 서버 주소. 주면 연결 실패(NETWORK) 문구에 넣어 개발 중 원인을 알기 쉽게 한다 */
  baseUrl?: string;
}

const DEFAULT_FALLBACK = '잠시 후 다시 시도해 주세요.';

// ── 도우미 ──

const str = (v: unknown): string => (v === null || v === undefined ? '' : String(v));

const list = (v: unknown): string[] => (Array.isArray(v) ? v.map(str).filter(Boolean) : typeof v === 'string' && v ? [v] : []);

/** '{min}자 이상' 같은 자리에 params 값을 넣는다 */
const fill = (template: string, p: TParams): string => template.replace(/\{(\w+)\}/g, (_, k: string) => str(p[k]));

const has = (p: TParams, k: string): boolean => p[k] !== undefined && p[k] !== null && p[k] !== '';

/** 받침 유무로 조사를 고른다 (한글이 아니면 받침 없는 쪽). josa.ts와 같은 규칙 */
const withJosa = (word: string, withFinal: string, withoutFinal: string): string => {
  const hangul = word.replace(/[^가-힣]/g, '');
  const last = hangul.charCodeAt(hangul.length - 1);
  if (!hangul || last < 0xac00 || last > 0xd7a3) return `${word}${withoutFinal}`;
  return `${word}${(last - 0xac00) % 28 !== 0 ? withFinal : withoutFinal}`;
};

/** 옷 칸 코드 → 이름 (코드 테이블 이름을 쓰려면 slotListText에 label을 넘긴다) */
export const SLOT_LABEL: Record<string, string> = {
  TOP: '상의',
  BOTTOM: '하의',
  DRESS: '원피스',
  OUTER: '아우터',
  SHOES: '신발',
  BAG: '가방',
};

const defaultSlotLabel = (slot: string): string => SLOT_LABEL[slot] ?? slot;

/**
 * WARDROBE_INSUFFICIENT의 모자란 칸 → "신발이 모자라요." / "상의·하의(또는 원피스)가 모자라요."
 * alternatives(원피스)는 모자란 상의·하의를 대신할 수 있는 칸이다. 칸이 없으면 빈 문자열.
 */
export const slotListText = (missing: unknown, alternatives: unknown, label: (slot: string) => string = defaultSlotLabel): string => {
  const names = slotNames(missing, alternatives, label);
  return names ? `${withJosa(names, '이', '가')} 모자라요.` : '';
};

/** 모자란 칸 이름 목록: "상의·하의(또는 원피스), 신발". 칸이 없으면 빈 문자열 */
const slotNames = (missing: unknown, alternatives: unknown, label: (slot: string) => string = defaultSlotLabel): string => {
  const miss = list(missing);
  if (miss.length === 0) return '';
  const alts = list(alternatives);
  const pair = miss.filter((s) => s === 'TOP' || s === 'BOTTOM');
  const rest = miss.filter((s) => s !== 'TOP' && s !== 'BOTTOM');
  const parts: string[] = [];
  if (pair.length > 0) {
    const names = pair.map(label).join('·');
    parts.push(alts.length > 0 ? `${names}(또는 ${alts.map(label).join('·')})` : names);
  }
  parts.push(...rest.map(label));
  return parts.join(', ');
};

/** 바이트 → "10MB" / "500KB" */
const bytesText = (v: unknown): string => {
  const n = Number(v);
  if (!Number.isFinite(n)) return str(v);
  if (n >= 1024 * 1024) return `${Math.round((n / (1024 * 1024)) * 10) / 10}MB`;
  if (n >= 1024) return `${Math.round(n / 1024)}KB`;
  return `${n}바이트`;
};

/** YYYY-MM-DD 두 개 → 연도가 다를 때만 연도를 넣은 "10월 18일" 꼴 */
const dateRangeParts = (min: unknown, max: unknown): [string, string] => {
  const parse = (v: unknown) => /^(\d{4})-(\d{2})-(\d{2})$/.exec(str(v));
  const a = parse(min);
  const b = parse(max);
  const sameYear = !!a && !!b && a[1] === b[1];
  const fmt = (m: RegExpExecArray | null, raw: unknown) =>
    m ? `${sameYear ? '' : `${m[1]}년 `}${Number(m[2])}월 ${Number(m[3])}일` : str(raw);
  return [fmt(a, min), fmt(b, max)];
};

/**
 * 날짜 필드(min·max가 YYYY-MM-DD 문자열) 문구. verb: "볼"(날씨) · "고를"(구입일).
 * OUT_OF_RANGE·INVALID_FORMAT이 아니면 null (기본 문구)
 */
const dateFieldText = (code: string, p: TParams, verb: string): string | null => {
  if (code === 'OUT_OF_RANGE') {
    const [min, max] = dateRangeParts(p.min, p.max);
    if (has(p, 'min') && has(p, 'max')) return `${min}부터 ${max}까지의 날짜만 ${verb} 수 있어요.`;
    if (has(p, 'min')) return `${min}부터의 날짜만 ${verb} 수 있어요.`;
    if (has(p, 'max')) return `${max}까지의 날짜만 ${verb} 수 있어요.`;
    return `${verb} 수 없는 날짜예요.`;
  }
  if (code === 'INVALID_FORMAT') return '날짜 형식(YYYY-MM-DD)이 올바르지 않아요.';
  return null;
};

// ── 서버 오류 코드 (error-codes.json errorCodes, 36개) ──

const AI_FEATURE: Record<string, string> = { ITEM_ANALYZE: 'AI 사진 분석', OUTFIT_RECOMMEND: 'AI 코디 추천' };

const USAGE_KIND: Record<string, string> = { ITEM: '옷', PROFILE: '프로필', SUB_TYPE: '세부 종류' };

const API_KEY_REASON: Record<string, string> = {
  API_401: '키가 올바르지 않아요',
  API_403: '이 키로는 접근할 수 없어요',
  API_429: '공급자 요청 한도를 넘었어요',
  TIMEOUT: '공급자 응답이 너무 늦어요',
  API_FAILED: '공급자 호출에 실패했어요',
  API_CLIENT: '공급자 호출을 준비하지 못했어요',
  INVALID_RESPONSE: '공급자 응답을 이해하지 못했어요',
  BAD_RESPONSE: '공급자 응답을 이해하지 못했어요',
};

const IMAGE_REASON: Record<string, string> = {
  FORMAT: 'JPEG·PNG 사진만 올릴 수 있어요.',
  TOO_LARGE: '사진은 10MB까지 올릴 수 있어요.',
  PIXELS: '사진 해상도가 너무 커요. 4천만 화소 이하로 올려 주세요.',
  DECODE: '사진을 읽을 수 없어요. 다른 사진으로 시도해 주세요.',
};

export const SERVER_TEXT: Record<string, TText> = {
  VALIDATION_FAILED: '입력값을 확인해 주세요.',
  UNAUTHORIZED: '로그인이 필요해요.',
  TOKEN_EXPIRED: '로그인이 만료됐어요. 다시 로그인해 주세요.',
  TOKEN_INVALID: '로그인 정보가 올바르지 않아요. 다시 로그인해 주세요.',
  INVALID_CREDENTIALS: '이메일 또는 비밀번호가 맞지 않아요.',
  REFRESH_INVALID: '다시 로그인해 주세요.',
  REFRESH_REUSED: '보안을 위해 다시 로그인해 주세요.',
  FORBIDDEN: '접근할 수 없어요.',
  REAUTH_FAILED: '비밀번호가 맞지 않아요.',
  ACCOUNT_SUSPENDED: '이용이 정지된 계정이에요.',
  // 탈퇴 예정 계정 (doc/27): 로그인 화면은 이 코드로 탈퇴 취소를 묻는다
  ACCOUNT_WITHDRAWN: (p) => {
    const at = has(p, 'purgeAt') ? new Date(str(p.purgeAt)) : null;
    const when = at && !Number.isNaN(at.getTime()) ? `${at.getMonth() + 1}월 ${at.getDate()}일에 ` : '';
    return `탈퇴를 신청한 계정이에요. ${when}모든 정보가 삭제돼요.`;
  },
  // 관리자 계정 보완 (doc/22): 관리자 사이트 전용이라 앱에서는 거의 볼 일이 없다
  LAST_ADMIN: '마지막 관리자는 해제할 수 없어요.',
  MFA_REQUIRED: '2단계 인증을 마쳐야 관리자 기능을 쓸 수 있어요.',
  MFA_TOKEN_INVALID: '인증 시간이 지났어요. 처음부터 다시 로그인해 주세요.',
  MFA_CODE_INVALID: (p) => {
    const left = Number(p.remainingAttempts);
    return Number.isFinite(left) && left > 0 ? `인증 코드가 맞지 않아요. (남은 횟수 ${left}번)` : '인증 코드가 맞지 않아요.';
  },
  MFA_LOCKED: (p) => {
    const sec = Number(p.retryAfterSeconds);
    return Number.isFinite(sec) && sec > 0
      ? `인증 코드를 여러 번 틀려 잠시 잠겼어요. ${Math.ceil(sec / 60)}분 뒤 다시 시도해 주세요.`
      : '인증 코드를 여러 번 틀려 잠시 잠겼어요. 잠시 후 다시 시도해 주세요.';
  },
  MFA_SETUP_REQUIRED: '2단계 인증 등록을 먼저 시작해 주세요.',
  MFA_ALREADY_ENROLLED: '이미 2단계 인증을 등록했어요.',
  MFA_NOT_ENROLLED: '2단계 인증을 등록하지 않았어요.',
  // 관리자 사이트 회사 계정 로그인(SSO, doc/22 4.2): 관리자 사이트 전용이라 앱에서는 볼 일이 없다
  SSO_NOT_ADMIN: '관리자 그룹에 속한 회사 계정이 아니에요.',
  SSO_EMAIL_UNVERIFIED: '회사 계정의 이메일을 확인할 수 없어요.',
  SSO_STATE_INVALID: '로그인 시간이 지났어요. 다시 시도해 주세요.',
  SSO_TOKEN_INVALID: '회사 계정 로그인 결과를 확인하지 못했어요. 다시 시도해 주세요.',
  SSO_IDP_ERROR: '회사 계정 로그인이 취소됐거나 실패했어요.',
  SSO_PROVIDER_UNAVAILABLE: '회사 계정 로그인 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.',
  SSO_EXCHANGE_INVALID: '로그인 시간이 지났어요. 다시 로그인해 주세요.',
  NOT_FOUND: '찾을 수 없어요.',
  METHOD_NOT_ALLOWED: '지원하지 않는 요청이에요.',
  EMAIL_TAKEN: '이미 가입된 이메일이에요.',
  CODE_EXISTS: '이미 있는 코드예요.',
  CODE_IN_USE: (p) => {
    const usages = Array.isArray(p.usages) ? (p.usages as { kind?: unknown; count?: unknown }[]) : [];
    const detail = usages
      .filter((u) => u && Number(u.count) > 0)
      .map((u) => `${USAGE_KIND[str(u.kind)] ?? str(u.kind)} ${Number(u.count)}개`)
      .join(', ');
    return `사용 중인 코드라 삭제할 수 없어요${detail ? `(${detail})` : ''}. 대신 비활성화해 주세요.`;
  },
  PROVIDER_EXISTS: '이미 있는 공급자 ID예요.',
  SECRET_KEY_MISSING: '서버에 WEARLY_SECRET_KEY가 없어 웹에서 API 키를 넣을 수 없어요.',
  API_KEY_INVALID: (p) => {
    const reason = str(p.reason);
    if (!reason) return 'API 키를 확인하지 못했어요.';
    const why = API_KEY_REASON[reason] ?? (/^API_5\d\d$/.test(reason) ? '공급자 서버 오류예요' : '');
    return `API 키를 확인하지 못했어요. ${why ? `${why}. ` : ''}(${reason})`;
  },
  WARDROBE_INSUFFICIENT: (p) => {
    const names = slotNames(p.missing, p.alternatives);
    return names ? `${withJosa(names, '이', '가')} 모자라 코디를 만들 수 없어요.` : '코디를 만들 옷이 모자라요.';
  },
  IMAGE_INVALID: (p) => IMAGE_REASON[str(p.reason)] ?? '사진을 처리할 수 없어요. JPEG·PNG 사진만 올릴 수 있어요.',
  IMAGE_NOT_UPLOADED: '사진 올리기가 끝나지 않았어요. 다시 올려 주세요.',
  IMAGE_IN_USE: '다른 옷에 쓰고 있는 사진이에요.',
  IMAGE_UPLOAD_LIMIT: '오늘 올릴 수 있는 사진 수를 모두 썼어요. 내일 다시 시도해 주세요.',
  IMAGE_TOO_LARGE: '사진은 10MB까지 올릴 수 있어요.',
  UNSUPPORTED_MEDIA_TYPE: '지원하지 않는 형식이에요.',
  AI_LIMIT_EXCEEDED: (p) => {
    const feature = str(p.feature);
    if (feature === 'OUTFIT_RECOMMEND') return '오늘 AI 코디 추천을 모두 썼어요. 내일 다시 쓸 수 있어요.';
    if (feature === 'ITEM_ANALYZE') return '오늘 AI 사진 분석을 모두 썼어요. 내일 다시 쓸 수 있어요.';
    return '오늘 AI 분석 횟수를 모두 썼어요. 내일 다시 쓸 수 있어요.';
  },
  AI_UNAVAILABLE: (p) => `${AI_FEATURE[str(p.feature)] ?? 'AI 분석'}을 지금 할 수 없어요. 잠시 후 다시 시도해 주세요.`,
  WEATHER_UNAVAILABLE: (p) =>
    str(p.kind) === 'SEARCH'
      ? '장소 검색을 지금 할 수 없어요. 잠시 후 다시 시도해 주세요.'
      : '날씨 정보를 아직 받지 못했어요. 잠시 후 다시 시도해 주세요.',
  WEATHER_REFRESH_RUNNING: '날씨를 이미 받고 있어요. 끝난 뒤 다시 시도해 주세요.',
  WEATHER_LIMIT: (p) =>
    str(p.kind) === 'SEARCH'
      ? '오늘 장소 검색 횟수를 모두 썼어요. 내일 다시 시도해 주세요.'
      : '오늘 다른 장소 날씨를 볼 수 있는 횟수를 모두 썼어요. 내일 다시 시도해 주세요.',
  WEATHER_NO_PROVIDER: (p) => {
    const reason = str(p.reason);
    if (reason === 'KEY_MISSING') return `서버 설정에 날씨 API 키${has(p, 'keyName') ? `(${str(p.keyName)})` : ''}가 없어요.`;
    if (reason === 'NOT_IMPLEMENTED') return '서버에 이 날씨 공급자 구현이 없어요.';
    return '쓸 수 있는 날씨 공급자가 없어요. 공급자 사용 여부·키·차단 상태·월 한도를 확인해 주세요.';
  },
  PLAN_LIMIT: (p) => `예정은 ${has(p, 'max') ? str(p.max) : '500'}개까지 저장할 수 있어요. 지난 예정을 지워 주세요.`,
  // 옷장 도우미 연결 (doc/26)
  PAIRING_CODE_INVALID: '코드가 맞지 않거나 시간이 지났어요. 코드를 다시 받아 주세요.',
  PAIRING_SELF: '내가 만든 코드는 내가 입력할 수 없어요. 상대에게 코드를 보내 주세요.',
  PAIRING_EXISTS: '이미 연결됐거나 승인을 기다리는 상대예요.',
  PAIRING_LIMIT: (p) => {
    const max = has(p, 'max') ? str(p.max) : null;
    if (str(p.side) === 'OWNER') return `옷장 주인은 돕는 사람을 ${max ?? '정해진 수'}명까지 연결할 수 있어요.`;
    if (str(p.side) === 'HELPER') return `옷장은 ${max ?? '정해진 수'}곳까지 맡을 수 있어요.`;
    return '연결할 수 있는 인원을 모두 채웠어요.';
  },
  PAIRING_TOO_MANY_ATTEMPTS: '코드를 여러 번 입력했어요. 잠시 후 다시 시도해 주세요.',
  INTERNAL_ERROR: '일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.',
  NOT_ACCEPTABLE: '지원하지 않는 응답 형식이에요.',
  CONFLICT: '다른 요청과 겹쳐 처리하지 못했어요. 다시 시도해 주세요.',
  SERVICE_UNAVAILABLE: '서비스를 잠시 쓸 수 없어요. 잠시 후 다시 시도해 주세요.',
};

// ── 앱이 만드는 코드 (카탈로그에 없음) ──

export const APP_TEXT: Record<string, TText> = {
  /** 서명 주소로 올리기(PUT)를 저장소가 거절 (주소 만료 등) */
  UPLOAD_FAILED: '사진을 올리지 못했어요. 다시 시도해 주세요.',
  /** 서버에 닿지 못함 (연결 실패·시간 초과). baseUrl은 errorText가 따로 넣는다 */
  NETWORK: '서버에 연결할 수 없어요. 인터넷 연결을 확인하고 다시 시도해 주세요.',
  /** 응답이 계약 형식이 아님 → 상태별 기본 문구 (statusText) */
  HTTP_ERROR: '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',
  /** 서버에 사용 중지 API가 없음 (예전 서버) */
  RETIRE_UNSUPPORTED: "서버에 아직 '사용하지 않는 옷' 기능이 없어요. 서버를 최신으로 올린 뒤 다시 시도해 주세요.",
  /** 서버에 사진 API가 없음 (예전 서버) */
  IMAGES_UNSUPPORTED: '서버에 아직 사진 저장 기능이 없어요.',
};

// ── 필드 오류 코드 (error-codes.json fieldErrorCodes, 21개) ──

const TYPE_LABEL: Record<string, string> = {
  boolean: '켜기·끄기(true/false)',
  string: '글자',
  integer: '정수',
  number: '숫자',
  object: '객체',
  array: '목록',
};

const FORMAT_TEXT: Record<string, string> = {
  'YYYY-MM-DD': '날짜 형식(YYYY-MM-DD)이 올바르지 않아요.',
  'date-time': '날짜·시간 형식이 올바르지 않아요.',
  '#RRGGBB': '색 코드 형식(#RRGGBB)이 올바르지 않아요.',
  url: '주소 형식이 올바르지 않아요.',
  json: 'JSON 형식이 올바르지 않아요.',
  lower: '영문 소문자로 입력해 주세요.',
  upper: '영문 대문자로 입력해 주세요.',
};

export const FIELD_TEXT: Record<string, TFieldText> = {
  REQUIRED: '필수 값이에요.',
  TOO_SHORT: (p) => (has(p, 'min') ? fill('{min}자 이상 입력해 주세요.', p) : '너무 짧아요.'),
  TOO_LONG: (p) => (has(p, 'max') ? fill('{max}자까지 쓸 수 있어요.', p) : '너무 길어요.'),
  INVALID_FORMAT: (p) => FORMAT_TEXT[str(p.format)] ?? '형식이 올바르지 않아요.',
  INVALID_TYPE: (p) => (TYPE_LABEL[str(p.expected)] ? `${TYPE_LABEL[str(p.expected)]} 값이어야 해요.` : '값의 종류가 올바르지 않아요.'),
  OUT_OF_RANGE: (p) => {
    if (has(p, 'min') && has(p, 'max')) return fill('{min}~{max} 사이 값이어야 해요.', p);
    if (has(p, 'min')) return fill('{min} 이상이어야 해요.', p);
    if (has(p, 'max')) return fill('{max} 이하여야 해요.', p);
    return '허용 범위를 벗어난 값이에요.';
  },
  NOT_ALLOWED: (p) => {
    const allowed = list(p.allowed);
    return allowed.length > 0 ? `허용되지 않는 값이에요. (${allowed.join(', ')} 중 하나)` : '허용되지 않는 값이에요.';
  },
  INACTIVE: '지금 고를 수 없는 값이에요.',
  DUPLICATE: '이미 있는 값이에요.',
  NOT_FOUND: '없는 값이에요.',
  TOO_MANY: (p) => (has(p, 'max') ? fill('{max}개까지 보낼 수 있어요.', p) : '항목이 너무 많아요.'),
  IMMUTABLE: '바꿀 수 없는 값이에요.',
  RESERVED: '쓸 수 없는 이름이에요.',
  SELF_NOT_ALLOWED: '자기 자신에게는 할 수 없어요.',
  INCOMPLETE: (p) => (has(p, 'expected') ? fill('{expected}개를 빠짐없이 한 번씩 보내 주세요.', p) : '목록을 빠짐없이 한 번씩 보내 주세요.'),
  ITEM_RETIRED: '사용하지 않는 옷이에요.',
  NOT_OWNED: '내 옷장에 없는 아이템이에요.',
  SLOT_MISMATCH: (p) => (has(p, 'slot') ? `${defaultSlotLabel(str(p.slot))} 칸에 넣을 수 없는 아이템이에요.` : '그 칸에 넣을 수 없는 아이템이에요.'),
  SUBTYPE_MISMATCH: '카테고리와 맞지 않는 세부 종류예요.',
  CONFLICT: '함께 고를 수 없는 값이에요.',
  DISABLED_BY_SERVER: '서버 설정에서 허용하지 않은 항목이에요.',
};

/**
 * 필드별 예외 문구 (기본 문구가 어색한 곳). 문구 안에 무엇인지가 들어 있어 이름을 앞에 붙이지 않는다.
 * 키는 배열 번호를 뺀 필드 이름. null을 돌려주면 기본 문구를 쓴다.
 */
const FIELD_OVERRIDE: Record<string, (code: string, p: TParams) => string | null> = {
  /** GET /weather/day: min·max가 YYYY-MM-DD 문자열 */
  date: (code, p) => dateFieldText(code, p, '볼'),
  /** 옷 구입일: 1990-01-01 ~ 오늘(서울). min·max가 YYYY-MM-DD 문자열 */
  purchasedAt: (code, p) => dateFieldText(code, p, '고를'),
  /** 옷 색 목록 (colors[i]도 배열 번호를 빼고 여기로 온다) */
  colors: (code, p) => {
    if (code === 'TOO_MANY') return has(p, 'max') ? `색은 ${str(p.max)}개까지 고를 수 있어요.` : '색이 너무 많아요.';
    if (code === 'DUPLICATE') return '같은 색을 두 번 고를 수 없어요.';
    if (code === 'REQUIRED') return '색을 하나 이상 골라 주세요.';
    return null;
  },
  /** 사진 업로드 size는 바이트. 목록 조회의 size(쪽 크기)는 작은 숫자라 기본 문구 */
  size: (code, p) => {
    if (code !== 'OUT_OF_RANGE') return null;
    const max = Number(p.max);
    if (!(max >= 1024 * 1024)) return null;
    return `사진은 ${bytesText(max)}까지 올릴 수 있어요.`;
  },
  region: (code) => (code === 'NOT_FOUND' ? '없는 지역이에요.' : null),
  regionCode: (code) => (code === 'NOT_FOUND' ? '없는 지역이에요.' : null),
  email: (code) => (code === 'INVALID_FORMAT' ? '이메일 형식을 확인해 주세요.' : null),
  password: (code) => (code === 'INVALID_FORMAT' ? '비밀번호는 8~64자로, 영문과 숫자를 모두 넣어 주세요.' : null),
};

/** 필드 이름 (VALIDATION_FAILED 한 줄 문구 앞에 붙인다) */
const FIELD_LABEL: Record<string, string> = {
  name: '이름',
  displayName: '이름',
  label: '이름',
  email: '이메일',
  password: '비밀번호',
  brand: '브랜드',
  memo: '메모',
  'context.memo': '메모',
  date: '날짜',
  'context.date': '날짜',
  q: '검색어',
  category: '종류',
  subType: '세부 종류',
  color: '색',
  colors: '색',
  purchasedAt: '구입일',
  pattern: '패턴',
  seasons: '계절',
  styles: '스타일',
  formality: '격식',
  wornCount: '입은 횟수',
  workDays: '출근 요일',
  image: '사진',
  imageId: '사진',
  contentType: '사진 형식',
  lat: '위도',
  lon: '경도',
  region: '지역',
  regionCode: '지역',
  themeId: '테마',
  reason: '이유',
  'context.occasion': '상황',
  'context.avoidColors': '피하고 싶은 색',
  'context.anchorItemId': '중심 아이템',
  'context.fixed': '고정 아이템',
  'context.weather.temp': '기온',
  excludeItemIds: '제외할 옷',
  recent: '최근 코디',
  items: '옷 목록',
  apiKey: 'API 키',
  model: '모델',
  // 예정 코디 (POST·PATCH /plans, bulk는 items[i]. 를 앞에 붙인다)
  title: '제목',
  timeOfDay: '시간대',
  occasion: '상황',
  concepts: '컨셉',
  weather: '날씨',
  'weather.temp': '기온',
  'weather.min': '최저 기온',
  'weather.max': '최고 기온',
  'weather.rainProb': '강수 확률',
  'weather.cond': '날씨 상태',
  'place.label': '장소 이름',
  weatherSource: '날씨 출처',
  weatherCheckedAt: '날씨 확인 시각',
  clientId: '예정 ID',
  outfit: '코디',
  'outfit.items': '코디 아이템',
  from: '시작 날짜',
  to: '끝 날짜',
};

const normalizeField = (field: string): string => field.replace(/\[\d+\]/g, '');

export const fieldLabel = (field: string): string | null => {
  const f = normalizeField(field);
  if (FIELD_LABEL[f]) return FIELD_LABEL[f];
  // bulk 항목(items[3].weather.min)은 앞의 items. 를 떼고 다시 찾는다
  if (f.startsWith('items.') && FIELD_LABEL[f.slice(6)]) return FIELD_LABEL[f.slice(6)];
  if (f.startsWith('context.fixed')) return FIELD_LABEL['context.fixed'];
  if (f.startsWith('outfit.items') || f.startsWith('items.outfit.items')) return FIELD_LABEL['outfit.items'];
  const last = f.split('.').pop() ?? f;
  return FIELD_LABEL[last] ?? null;
};

const fieldTextParts = (fe: IFieldErrorLike): { text: string; selfContained: boolean } => {
  const code = fe.code ?? '';
  const p = fe.params ?? {};
  const f = normalizeField(fe.field);
  const special = (FIELD_OVERRIDE[f] ?? FIELD_OVERRIDE[f.split('.').pop() ?? ''])?.(code, p);
  if (special) return { text: special, selfContained: true };
  const entry = FIELD_TEXT[code];
  if (entry === undefined) return { text: '입력값을 확인해 주세요.', selfContained: false };
  return { text: typeof entry === 'function' ? entry(p, fe.field) : entry, selfContained: false };
};

/**
 * 필드 오류 → 문구. 입력칸 바로 아래에 보여 줄 때는 이름 없이("필수 값이에요."),
 * withLabel이면 "이름은 필수 값이에요."처럼 필드 이름을 붙인다.
 */
export const fieldErrorText = (fe: IFieldErrorLike, opts: { withLabel?: boolean } = {}): string => {
  const { text, selfContained } = fieldTextParts(fe);
  if (!opts.withLabel || selfContained) return text;
  const label = fieldLabel(fe.field);
  return label ? `${withJosa(label, '은', '는')} ${text}` : text;
};

// ── 상태별 기본 문구 (모르는 코드) ──

export const statusText = (status: number): string => {
  if (status === 0) return APP_TEXT.NETWORK as string;
  if (status === 400) return '요청 내용을 확인해 주세요.';
  if (status === 401) return '로그인이 필요해요.';
  if (status === 403) return '접근할 수 없어요.';
  if (status === 404) return '찾을 수 없어요.';
  if (status === 409) return '다른 요청과 겹쳐 처리하지 못했어요. 다시 시도해 주세요.';
  if (status === 413) return '사진이 너무 커요.';
  if (status === 429) return '요청이 너무 많아요. 잠시 후 다시 시도해 주세요.';
  if (status >= 500) return '일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.';
  return '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';
};

// ── 시스템 오류·오류 번호 ──

/** 시스템 오류로 보는 서버 코드 (그 밖의 5xx 업무 코드 AI_UNAVAILABLE·WEATHER_UNAVAILABLE은 아니다) */
const SYSTEM_CODES = new Set(['INTERNAL_ERROR', 'SERVICE_UNAVAILABLE']);

/** 시스템 오류인지: 500 INTERNAL_ERROR · 503 SERVICE_UNAVAILABLE · 표에 없는 코드의 5xx */
export const isSystemError = (e: unknown): boolean => {
  if (!isApiError(e)) return false;
  if (SYSTEM_CODES.has(e.code)) return true;
  return e.status >= 500 && SERVER_TEXT[e.code] === undefined;
};

/** 시스템 오류의 전체 traceId (문의·복사용). 시스템 오류가 아니거나 traceId가 없으면 undefined */
export const systemTraceId = (e: unknown): string | undefined =>
  isSystemError(e) ? (e as ApiError).traceId || undefined : undefined;

/** traceId → "오류 번호 ab12cd34" (앞 8자) */
export const traceLabelOf = (traceId: string): string => `오류 번호 ${traceId.slice(0, 8)}`;

/** 시스템 오류면 "오류 번호 ab12cd34", 아니면 null */
export const traceLabel = (e: unknown): string | null => {
  const id = systemTraceId(e);
  return id ? traceLabelOf(id) : null;
};

// ── 오류 → 문구 ──

const textOf = (entry: TText, p: TParams): string => (typeof entry === 'function' ? entry(p) : entry);

/**
 * 사용자에게 보여 줄 오류 문구.
 * - 서버 코드·앱 코드는 표의 문구(params 해석 포함), VALIDATION_FAILED는 첫 필드 오류("이름은 20자까지 쓸 수 있어요.").
 * - 모르는 코드는 상태별 기본 문구. ApiError가 아니면 ctx.fallback.
 * - 오류 번호는 넣지 않는다 (알림은 errorAlertText, 화면은 traceLabel을 따로 그린다).
 */
export const errorText = (e: unknown, ctx: IErrorTextContext = {}): string => {
  if (!isApiError(e)) return ctx.fallback ?? DEFAULT_FALLBACK;
  const p = e.params ?? {};
  if (e.code === 'NETWORK') {
    return ctx.baseUrl
      ? `서버(${ctx.baseUrl})에 연결할 수 없어요. 백엔드가 켜져 있는지, 휴대폰과 Mac이 같은 Wi-Fi인지 확인해 주세요.`
      : (APP_TEXT.NETWORK as string);
  }
  if (e.code === 'VALIDATION_FAILED' && e.fieldErrors?.[0]) return fieldErrorText(e.fieldErrors[0] as FieldError, { withLabel: true });
  if (e.code === 'HTTP_ERROR') return statusText(e.status);
  const entry = SERVER_TEXT[e.code] ?? APP_TEXT[e.code];
  if (entry !== undefined) return textOf(entry, p);
  return statusText(e.status);
};

/** 알림(Alert) 본문: 문구 + 시스템 오류면 끝에 "오류 번호 ab12cd34" 한 줄 */
export const errorAlertText = (e: unknown, ctx: IErrorTextContext = {}): string => {
  const label = traceLabel(e);
  const text = errorText(e, ctx);
  return label ? `${text}\n${label}` : text;
};
