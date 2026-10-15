export type Category = 'TOP' | 'BOTTOM' | 'OUTER' | 'SHOES' | 'BAG' | 'ACC';

/** 일러스트 모양과 실루엣(볼륨)을 정하는 세부 종류 */
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
  imageUri?: string;
  brand?: string;
  wornCount: number;
  lastWornAt?: string;
  createdAt: string;
}

export type Slot = 'TOP' | 'BOTTOM' | 'OUTER' | 'SHOES' | 'BAG';
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
