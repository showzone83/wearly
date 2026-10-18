// 백엔드 API DTO (doc/05_백엔드_API.md 6장, doc/06_코드_관리.md 4장).
// 이 폴더는 Node(연동 하네스)에서도 그대로 import되므로 앱 쪽 타입(@/data/types)을 가져오지 않는다.
// 옷 속성 값(종류·세부 종류·색·패턴·계절·스타일·보유 상태)은 서버 코드 테이블(GET /codes)이 정하므로 문자열이다.

/** CATEGORY 코드 (예: TOP, DRESS) */
export type Category = string;
/** SUB_TYPE 코드 (예: KNIT, SHIRT_DRESS). parent가 Category */
export type SubType = string;
/** COLOR 코드 (예: navy) */
export type ColorKey = string;
/** PATTERN 코드 (예: SOLID) */
export type Pattern = string;
/** SEASON 코드 (예: SPRING) */
export type Season = string;
/** STYLE 코드 (예: NEAT). 아이템 스타일이자 프로필 취향(moods) 값 */
export type StyleTag = string;
/** ITEM_STATUS 코드 (예: OWNED) */
export type ItemStatus = string;
/** RETIRE_REASON 코드 (예: DONATED). 사용하지 않는 옷으로 옮긴 이유 (doc/13 2장) */
export type RetireReason = string;
export type DressCode = 'CASUAL' | 'BIZ_CASUAL' | 'FORMAL';
export type WorkType = 'WEEKDAY' | 'WEEKEND' | 'CUSTOM';

// ── 인증 ──

export interface UserDto {
  id: string;
  email: string | null;
  name: string;
  /** USER · ADMIN (doc/07). 예전 서버는 보내지 않는다 */
  role?: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  /** 초 */
  accessTokenExpiresIn: number;
  refreshToken: string;
  /** 초 */
  refreshTokenExpiresIn: number;
  user: UserDto;
}

export interface SignupRequest {
  email: string;
  password: string;
  name: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

// ── 프로필 ──

export interface ProfileDto {
  name: string;
  dressCodes: DressCode[];
  moods: StyleTag[];
  colors: ColorKey[];
  workType: WorkType;
  /** 0=일 ~ 6=토 */
  workDays: number[];
  workRule: boolean;
  offRule: boolean;
  notify: { morning: boolean; evening: boolean; dMinus1: boolean };
  onboarded: boolean;
}

// ── 옷장 ──

/**
 * 앱 WardrobeItem에서 imageUri(기기 사진)를 뺀 모양. 서버에 올린 사진은 image로 온다.
 * 서버에 사진 기능이 없거나 아직 안 올린 사진은 앱이 아이템 id별로 기기에 따로 보관한다.
 */
export interface ItemDto {
  id: string;
  name: string;
  category: Category;
  subType: SubType;
  color: ColorKey;
  pattern: Pattern;
  seasons: Season[];
  styles: StyleTag[];
  formality: 1 | 2 | 3;
  status: ItemStatus;
  brand?: string | null;
  wornCount: number;
  /** YYYY-MM-DD */
  lastWornAt?: string | null;
  createdAt: string;
  /** 사용 중지 시각(ISO). null이면 사용 중 (doc/13 3장). 예전 서버는 보내지 않는다 */
  retiredAt?: string | null;
  /** 사용 중지 이유 (RETIRE_REASON 코드) */
  retireReason?: RetireReason | null;
  /** 사용 중지 메모 (200자 이하) */
  retireMemo?: string | null;
  /** 대표 사진 (서버 저장). 없으면 null. 사진 기능이 없는 예전 서버는 보내지 않는다 */
  image?: ImageRef | null;
}

export type ItemInput = Omit<
  ItemDto,
  'id' | 'createdAt' | 'wornCount' | 'lastWornAt' | 'retiredAt' | 'retireReason' | 'retireMemo' | 'image'
> & {
  wornCount?: number;
  lastWornAt?: string | null;
  /**
   * 붙일 사진: complete까지 끝난(READY) 내 이미지 id. null이면 사진을 뺀다.
   * 보내지 않으면(undefined) 사진은 그대로다
   */
  imageId?: string | null;
};

export interface ItemListResponse {
  items: ItemDto[];
}

export type ItemSort = 'recent' | 'worn' | 'name';

export interface ItemFilter {
  category?: Category;
  status?: ItemStatus;
  color?: ColorKey;
  /** 이름·브랜드 부분 일치 */
  q?: string;
  sort?: ItemSort;
  /** 사용 중지한 옷: false(기본, 사용 중만) · true(사용 중지만) · 'all'(모두) (doc/13 3장) */
  retired?: boolean | 'all';
}

/** POST /wardrobe/items/{id}/retire 요청 */
export interface RetireRequest {
  /** 활성 RETIRE_REASON 코드 */
  reason: RetireReason;
  /** 200자 이하 */
  memo?: string;
}

export interface WardrobeStats {
  total: number;
  /** 활성 CATEGORY 코드 전부 (0 포함) + 아이템이 있는 비활성 코드 */
  byCategory: Record<Category, number>;
  byColor: { color: ColorKey; count: number }[];
  byStyle: { style: StyleTag; count: number }[];
  byStatus: Record<ItemStatus, number>;
  wornLast30Days: number;
  utilization30d: number;
  sleeping: string[];
  /** 사용 중지한 옷 수 (위 숫자들은 사용 중인 옷만 센다). 예전 서버는 보내지 않는다 */
  retired?: number;
  /** 이유별 사용 중지한 옷 수 */
  retiredByReason?: { reason: RetireReason; count: number }[];
}

// ── 사진 (서버 저장) ──

/** 아이템에 붙은 사진. url·thumbUrl은 expiresAt까지 유효한 서명 GET 주소라 받을 때마다 바뀐다 */
export interface ImageRef {
  /** 이미지 id (UUID). 주소가 바뀌어도 그대로라 화면 캐시 키로 쓴다 */
  imageId: string;
  /** 원본(긴 변 2048 이하) 주소 */
  url: string;
  /** 썸네일(긴 변 400) 주소 */
  thumbUrl: string;
  /** 주소 만료 시각(ISO) */
  expiresAt: string;
}

export type ImagePurpose = 'ITEM';
export type ImageContentType = 'image/jpeg' | 'image/png';

/** POST /images/upload-url 요청 */
export interface UploadUrlRequest {
  purpose: ImagePurpose;
  contentType: ImageContentType;
  /** 올릴 파일 크기(바이트) */
  size: number;
}

/** POST /images/upload-url 응답: 오브젝트 스토리지에 직접 올릴 서명 주소 */
export interface UploadUrlResponse {
  imageId: string;
  uploadUrl: string;
  method: 'PUT';
  /** PUT 때 그대로 붙여야 하는 헤더 (서명에 들어간 Content-Type 등) */
  headers: Record<string, string>;
  /** 업로드 주소 만료 시각(ISO) */
  expiresAt: string;
}

/** POST /images/{imageId}/complete 응답 (이미 READY여도 같은 응답) */
export interface ImageCompleteResponse extends ImageRef {
  width: number;
  height: number;
}

/**
 * 서명 주소로 올릴 본문. Blob(Node·웹), 바이트, 또는 arrayBuffer()만 있는 파일 객체
 * (React Native의 expo-file-system File처럼 Blob을 상속하지 않고 인터페이스만 구현한 것)
 */
export type UploadBody = Blob | ArrayBuffer | Uint8Array | { arrayBuffer(): Promise<ArrayBuffer> };

// ── AI 분석 ──

/** 공급자 ID (tb_ai_provider.provider_id). 예: "claude", "openai", "gemini", 서버 목업이면 "mock" */
export type AiProvider = string;

export interface AnalyzeSuggestion {
  name: string;
  category: Category;
  subType: SubType;
  color: ColorKey;
  pattern: Pattern;
  seasons: Season[];
  styles: StyleTag[];
  formality: 1 | 2 | 3;
  /** 0~1 */
  confidence: number;
}

export interface AnalyzeResponse {
  suggestion: AnalyzeSuggestion;
  provider: AiProvider;
  model: string | null;
  remainingToday: number;
}

// ── AI 코디 추천 (doc/09_AI_코디_추천.md 3장) ──

/** 코디 칸. 코디는 TOP+BOTTOM 또는 DRESS 하나로 몸판을 채우고 SHOES는 필수 */
export type OutfitSlot = 'TOP' | 'BOTTOM' | 'DRESS' | 'OUTER' | 'SHOES' | 'BAG';
/** 칸별 아이템 id */
export type OutfitSlotItems = Partial<Record<OutfitSlot, string>>;

export type OutfitOccasion =
  | 'COMMUTE'
  | 'MEETING'
  | 'FIELD'
  | 'DINNER'
  | 'DAILY'
  | 'INTERVIEW'
  | 'WEDDING'
  | 'DATE'
  | 'TRAVEL'
  | 'GATHERING';
export type OutfitMood = 'NEAT' | 'COMFY' | 'CHIC';
export type OutfitTimeOfDay = 'AM' | 'PM' | 'EVENING';

export interface OutfitWeatherDto {
  temp: number;
  min: number;
  max: number;
  /** 0~100 */
  rainProb: number;
  cond: 'SUNNY' | 'CLOUDY' | 'RAIN';
}

export interface OutfitRecommendContext {
  /** YYYY-MM-DD */
  date: string;
  occasion: OutfitOccasion;
  mood: OutfitMood;
  formality: DressCode;
  weather?: OutfitWeatherDto;
  /** 꼭 넣을 중심 아이템 */
  anchorItemId?: string;
  /** 칸 고정 */
  fixed?: OutfitSlotItems;
  /** 피하고 싶은 COLOR 코드 */
  avoidColors?: ColorKey[];
  /** 예정일 컨셉 (STYLE 코드) */
  concepts?: StyleTag[];
  timeOfDay?: OutfitTimeOfDay;
  /** 예정일 메모 (200자 이하) */
  memo?: string;
}

export interface OutfitRecommendRequest {
  context: OutfitRecommendContext;
  /** 최근 착용 제외 등 앱이 계산한 제외 목록 */
  excludeItemIds?: string[];
  /** 최근 7일 코디 (착용 기록은 기기에만 있어서 앱이 보낸다) */
  recent?: { date: string; items: OutfitSlotItems }[];
  /** 앱 affinity > 0 */
  likedItemIds?: string[];
  /** 앱 affinity < 0 */
  dislikedItemIds?: string[];
  /** 후보 수. 기본 5, 1~6 */
  count?: number;
}

/** AI가 매긴 0~100 예상 점수 (참고용. 화면 점수는 앱 엔진이 다시 매긴다) */
export interface OutfitAiScores {
  color: number;
  occasion: number;
  weather: number;
  taste: number;
  diversity: number;
}

export interface OutfitCandidateDto {
  items: OutfitSlotItems;
  /** 한국어 한두 문장 (80자 이하) */
  reason: string;
  aiScores: OutfitAiScores;
}

export interface OutfitRecommendResponse {
  candidates: OutfitCandidateDto[];
  provider: AiProvider;
  model: string | null;
  /** 코디 추천 남은 횟수 */
  remainingToday: number;
}

// ── 코드 (doc/06_코드_관리.md 4장) ──

/** 코드 그룹. 서버가 새 그룹을 더해도 받을 수 있게 string도 허용한다 */
export type CodeGroupKey =
  | 'CATEGORY'
  | 'SUB_TYPE'
  | 'COLOR'
  | 'PATTERN'
  | 'SEASON'
  | 'STYLE'
  | 'ITEM_STATUS'
  /** 사용하지 않는 옷 이유 (doc/13 2장). 예전 서버에는 없다 */
  | 'RETIRE_REASON'
  /** 날씨 지역 (doc/17 2장). attrs.lat·lon. 예전 서버에는 없다 */
  | 'REGION';

export interface CodeDto {
  code: string;
  /** 한국어 표시 이름 */
  label: string;
  /** SUB_TYPE만: CATEGORY 코드 */
  parent: string | null;
  sortOrder: number;
  /** false면 새로 고를 수 없지만 저장된 값의 이름은 보여 준다 */
  active: boolean;
  /** 그룹별 속성 (06 문서 3장) */
  attrs: Record<string, unknown>;
}

export interface CodeGroupDto {
  group: CodeGroupKey | (string & {});
  name: string;
  codes: CodeDto[];
}

export interface CodesResponse {
  /** 코드 테이블이 바뀌면 달라지는 문자열. ETag와 같은 값 */
  version: string;
  groups: CodeGroupDto[];
}

/** GET /codes 결과. If-None-Match가 맞아 304가 오면 changed: false */
export type CodesResult =
  | { changed: true; codes: CodesResponse; /** 다음 요청의 If-None-Match에 그대로 넣을 값 */ etag: string }
  | { changed: false };

// ── 앱 설정 (doc/12 3장) ──

/** GET /app/config 응답. 전체 사용자에게 같은 값이다 */
export interface AppConfigDto {
  /** 앱 테마 ID (nyang · closeta). 앱이 모르는 값이면 무시한다 */
  themeId: string;
  /** 설정이 바뀐 시각(ISO). ETag와 같은 값 */
  version: string;
}

/** GET /app/config 결과. If-None-Match가 맞아 304가 오면 changed: false */
/** 날씨 상태 (앱 Weather.cond와 같다) */
export type WeatherCondDto = 'SUNNY' | 'CLOUDY' | 'RAIN';

/** GET /weather (doc/17 5장). 서버에 저장된 값만 준다. 기온은 섭씨 */
export interface WeatherDto {
  region: { code: string; label: string };
  current: {
    temp: number;
    feelsLike: number | null;
    cond: WeatherCondDto;
    condText: string | null;
    /** 공급자가 관측·갱신한 시각 (ISO) */
    observedAt: string | null;
  };
  /** 그 지역의 오늘부터. 받은 일수만큼만 실제 예보다 */
  days: {
    /** YYYY-MM-DD */
    date: string;
    temp: number;
    min: number;
    max: number;
    rainProb: number;
    cond: WeatherCondDto;
    condText: string | null;
  }[];
  /** 서버가 받은 시각 (ISO) */
  fetchedAt: string;
  /** 서버가 한동안 받지 못해 오래된 값 ("업데이트 지연") */
  stale: boolean;
}

export type WeatherResult =
  | { changed: true; weather: WeatherDto; /** 다음 요청의 If-None-Match */ etag: string | null }
  | { changed: false };

// ── 장소별 날씨 (doc/19) ──

/**
 * 날씨 장소. REGION 코드(시·도)에 있는 곳은 regionCode(좌표는 코드 attrs), 그 밖(해외 등)은 검색 결과의 이름·좌표.
 * 서버 응답에서는 regionCode가 null일 수 있다
 */
export interface Place {
  regionCode?: string | null;
  label: string;
  lat: number;
  lon: number;
}

/** 날씨 출처: 예보 · 평년값. MANUAL(체감 온도 직접 고르기)은 앱만 쓴다 */
export type WeatherDaySource = 'FORECAST' | 'CLIMATE';

/** GET /weather/day 요청 장소: 지역 코드(인증 불필요) 또는 좌표(로그인 필요) */
export type WeatherDayWhere = { region: string } | { lat: number; lon: number };

/** GET /weather/day (doc/19). 한 장소·한 날짜의 날씨 */
export interface WeatherDayResponse {
  place: Place;
  /** YYYY-MM-DD (한국 날짜) */
  date: string;
  source: WeatherDaySource;
  day: {
    /** CLIMATE면 평년 평균 */
    temp: number;
    /** CLIMATE면 평년 평균 최저 */
    min: number;
    /** CLIMATE면 평년 평균 최고 */
    max: number;
    /** 0~100. CLIMATE면 비 온 날 비율×100 */
    rainProb: number;
    /** CLIMATE면 null */
    cond: WeatherCondDto | null;
    condText: string | null;
  };
  /** 이 장소의 예보가 있는 마지막 날짜 (없으면 null) */
  forecastUntil: string | null;
  /** FORECAST면 예보를 받은 시각, CLIMATE면 평년값 계산 시각 (ISO) */
  fetchedAt: string;
  /** CLIMATE일 때만 */
  climate: { years: string; rainDayRatio: number } | null;
}

/** GET /weather/places 결과 한 줄 */
export interface PlaceSearchItem {
  label: string;
  /** 시·도·주 이름 (공급자 값) */
  region: string | null;
  country: string | null;
  lat: number;
  lon: number;
  /** 국내 결과가 REGION 좌표 30km 안이면 그 코드 */
  regionCode?: string | null;
}

export interface PlaceSearchResponse {
  items: PlaceSearchItem[];
}

export type AppConfigResult =
  | { changed: true; config: AppConfigDto; /** 다음 요청의 If-None-Match에 그대로 넣을 값 */ etag: string }
  | { changed: false };

/** React Native의 FormData 파일 파트 모양 */
export interface RNFile {
  uri: string;
  name: string;
  type: string;
}

export type AnalyzeFile = Blob | RNFile;

/** POST /wardrobe/items/analyze JSON 요청: 이미 올린(READY) 내 이미지로 분석 */
export interface AnalyzeImageRequest {
  imageId: string;
}

// ── 오류 ──

/** 필드 오류. 서버는 문장 없이 { field, code, params? }만 보낸다 (backend error-codes.json의 fieldErrorCodes) */
export interface FieldError {
  field: string;
  /** 필드 오류 코드 (REQUIRED·TOO_LONG·OUT_OF_RANGE·NOT_ALLOWED …) */
  code: string;
  /** 코드의 기준 값 (max·min·allowed·format …). 값이 있을 때만 */
  params?: Record<string, unknown>;
  /** 화면 문구 자리. 서버가 보내지 않으면 code를 담아 둔다 (코드별 문구 표는 따로 정한다) */
  message: string;
}

/** 오류 응답 본문: { status, code, params?, fieldErrors: [{ field, code, params? }], traceId } */
export interface ApiErrorBody {
  status: number;
  code: string;
  /** 코드의 상세 값 (예: WARDROBE_INSUFFICIENT의 missing·alternatives). 값이 있을 때만 */
  params?: Record<string, unknown>;
  fieldErrors?: { field: string; code?: string; params?: Record<string, unknown>; message?: string }[];
  /** 요청 추적 ID (응답 헤더 X-Request-Id와 같다) */
  traceId?: string;
  /** 예전 서버만 보낸다 */
  message?: string;
}
