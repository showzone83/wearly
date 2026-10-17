// 백엔드 API DTO (doc/05_백엔드_API.md 6장).
// 이 폴더는 Node(연동 하네스)에서도 그대로 import되므로 앱 쪽 타입(@/data/types)을 가져오지 않고
// 같은 문자열 값을 여기 다시 정의한다. 앱 타입과 어긋나면 src/lib/serverItems.ts의 타입 검사가 깨진다.

export type Category = 'TOP' | 'BOTTOM' | 'OUTER' | 'SHOES' | 'BAG' | 'ACC';
export type SubType =
  | 'TEE'
  | 'KNIT'
  | 'SHIRT'
  | 'CARDIGAN'
  | 'SLACKS'
  | 'WIDE'
  | 'CHINO'
  | 'SKIRT'
  | 'JACKET'
  | 'COAT'
  | 'LOAFER'
  | 'SNEAKER'
  | 'TOTE'
  | 'SHOULDER'
  | 'WATCH';
export type ColorKey =
  | 'navy'
  | 'black'
  | 'charcoal'
  | 'gray'
  | 'white'
  | 'ivory'
  | 'beige'
  | 'camel'
  | 'brown'
  | 'khaki'
  | 'lightblue'
  | 'burgundy';
export type Pattern = 'SOLID' | 'STRIPE' | 'CHECK' | 'ETC';
export type Season = 'SPRING' | 'SUMMER' | 'FALL' | 'WINTER';
export type StyleTag = 'NEAT' | 'MINIMAL' | 'CASUAL' | 'CLASSIC' | 'STREET';
export type ItemStatus = 'OWNED' | 'LAUNDRY' | 'REPAIR';
export type DressCode = 'CASUAL' | 'BIZ_CASUAL' | 'FORMAL';
export type WorkType = 'WEEKDAY' | 'WEEKEND' | 'CUSTOM';

// ── 인증 ──

export interface UserDto {
  id: string;
  email: string | null;
  name: string;
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

/** 앱 WardrobeItem에서 imageUri를 뺀 모양. 사진은 앱이 아이템 id별로 기기에 따로 보관한다. */
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
}

export type ItemInput = Omit<ItemDto, 'id' | 'createdAt' | 'wornCount' | 'lastWornAt'> & {
  wornCount?: number;
  lastWornAt?: string | null;
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
}

export interface WardrobeStats {
  total: number;
  byCategory: Record<Category, number>;
  byColor: { color: ColorKey; count: number }[];
  byStyle: { style: StyleTag; count: number }[];
  byStatus: Record<ItemStatus, number>;
  wornLast30Days: number;
  utilization30d: number;
  sleeping: string[];
}

// ── AI 분석 ──

export type AiProvider = 'mock' | 'claude';

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

/** React Native의 FormData 파일 파트 모양 */
export interface RNFile {
  uri: string;
  name: string;
  type: string;
}

export type AnalyzeFile = Blob | RNFile;

// ── 오류 ──

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
