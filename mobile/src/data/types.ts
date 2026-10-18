// 옷 속성 값은 서버 코드 테이블(doc/06_코드_관리.md)이 정한다. 라벨·속성은 src/lib/codes.ts로 읽는다.

/** CATEGORY 코드 (예: TOP, DRESS, ACC) */
export type Category = string;
/** SUB_TYPE 코드. 일러스트 모양·보온·실루엣(볼륨)은 코드 attrs가 정한다 */
export type SubType = string;
/** COLOR 코드 (예: navy) */
export type ColorKey = string;
/** PATTERN 코드 (예: SOLID) */
export type Pattern = string;
/** SEASON 코드 (SPRING·SUMMER·FALL·WINTER) */
export type Season = string;
/** STYLE 코드 (예: NEAT) */
export type StyleTag = string;
/** ITEM_STATUS 코드 (예: OWNED). 추천 후보 여부는 attrs.recommendable */
export type ItemStatus = string;
/** RETIRE_REASON 코드 (예: DONATED). 사용하지 않는 옷으로 옮긴 이유 (doc/13) */
export type RetireReason = string;

/** 서버 사진 참조 (@/api/types의 ImageRef와 같은 모양) */
export interface IItemImage {
  imageId: string;
  url: string;
  thumbUrl: string;
  expiresAt: string;
}

export interface WardrobeItem {
  id: string;
  name: string;
  category: Category;
  subType: SubType;
  color: ColorKey;
  pattern: Pattern;
  seasons: Season[];
  styles: StyleTag[];
  /** 1 = 편한 데일리, 2 = 비즈니스 캐주얼, 3 = 포멀 */
  formality: 1 | 2 | 3;
  status: ItemStatus;
  /**
   * 기기 사진 주소: 게스트 사진(앱 문서 폴더 photos/), 샘플 사진(wearly-sample://), 계정 모드에서 아직 서버에 못 올린 사진.
   * 서버 사진(image)이 있으면 그것을 먼저 쓴다
   */
  imageUri?: string;
  /** 서버에 올린 사진 (계정 모드). url·thumbUrl은 서명 주소라 expiresAt 뒤에는 아이템을 다시 받아야 한다 */
  image?: IItemImage;
  brand?: string;
  wornCount: number;
  lastWornAt?: string;
  createdAt: string;
  /**
   * 사용하지 않는 옷으로 옮긴 시각(ISO). 있으면 옷장·추천에서 빠지고 과거 기록에만 보인다 (doc/13).
   * 보유 상태(status)와 별개라 다시 사용하면 원래 status로 돌아온다
   */
  retiredAt?: string;
  retireReason?: RetireReason;
  retireMemo?: string;
}

/** 코디 칸 (앱 로직이라 고정). 코디는 TOP+BOTTOM 또는 DRESS 하나로 몸판을 채운다 */
export type Slot = 'TOP' | 'BOTTOM' | 'DRESS' | 'OUTER' | 'SHOES' | 'BAG';
export type OutfitItems = Partial<Record<Slot, string>>;

export type Occasion =
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

export type Mood = 'NEAT' | 'COMFY' | 'CHIC';
export type Formality = 'CASUAL' | 'BIZ_CASUAL' | 'FORMAL';

export interface Weather {
  temp: number;
  min: number;
  max: number;
  rainProb: number;
  cond: 'SUNNY' | 'CLOUDY' | 'RAIN';
  /** 예보 기반이 아니라 사용자가 직접 입력한 값인지 */
  manual?: boolean;
}

export interface RecommendContext {
  occasion: Occasion;
  mood: Mood;
  formality: Formality;
  weather?: Weather;
  anchorItemId?: string;
  fixed?: OutfitItems;
  excludeRecent?: boolean;
  avoidColors?: ColorKey[];
  /** 예정일 컨셉 */
  concepts?: StyleTag[];
  /** 기준 날짜(YYYY-MM-DD). 최근 착용 판단에 사용 */
  date: string;
}

export interface ScoreBreakdown {
  color: number;
  occasion: number;
  weather: number | null;
  taste: number;
  diversity: number;
}

export interface Outfit {
  id: string;
  items: OutfitItems;
  score: number;
  breakdown: ScoreBreakdown;
  reasons: string[];
  context: RecommendContext;
  /**
   * AI 코디 추천(doc/09)으로 받은 후보일 때 AI가 쓴 이유와 예상 점수(0~100, 참고용).
   * 점수·breakdown·reasons는 항상 앱 엔진이 매긴 값이다. 한 칸을 교체하면 엔진이 새로 평가하므로 이 값은 빠진다.
   */
  ai?: { reason: string; scores?: { color: number; occasion: number; weather: number; taste: number; diversity: number } };
}

export type FeedbackType = 'LIKE' | 'DISLIKE';

export interface SavedOutfit {
  outfit: Outfit;
  savedAt: string;
  feedback?: FeedbackType;
  dislikeReasons?: string[];
}

export type TimeOfDay = 'AM' | 'PM' | 'EVENING';

export interface PlannedEvent {
  id: string;
  date: string;
  timeOfDay: TimeOfDay;
  title: string;
  occasion: Occasion;
  concepts: StyleTag[];
  weather: Weather;
  memo?: string;
  outfit?: Outfit;
}

export type Emotion = 'CONFIDENT' | 'COMFY' | 'AWKWARD' | 'UNCOMFY';
export type OthersReaction = 'PRAISE' | 'NORMAL' | 'MEH';

export interface AxisScores {
  color: number;
  silhouette: number;
  tpo: number;
  weather: number;
  diversity: number;
}

export interface Diagnosis {
  aiScore: number;
  axes: AxisScores;
  strength: keyof AxisScores;
  weakness: keyof AxisScores;
  swap?: { slot: Slot; fromId: string; toId: string };
  tip: string;
}

export interface DailyLook {
  id: string;
  date: string;
  items: OutfitItems;
  occasion: Occasion;
  selfScore: number;
  emotions: Emotion[];
  memo?: string;
  others: OthersReaction;
  praisedItemId?: string;
  diagnosis: Diagnosis;
  createdAt: string;
}

export type WorkType = 'WEEKDAY' | 'WEEKEND' | 'CUSTOM';

export interface Profile {
  name: string;
  dressCodes: Formality[];
  moods: StyleTag[];
  colors: ColorKey[];
  workType: WorkType;
  /** 0 = 일 ... 6 = 토 (Date.getDay 기준) */
  workDays: number[];
  workRule: boolean;
  offRule: boolean;
  notify: { morning: boolean; evening: boolean; dMinus1: boolean };
}
