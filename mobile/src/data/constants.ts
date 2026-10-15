import type {
  Category,
  ColorKey,
  Emotion,
  Formality,
  Mood,
  Occasion,
  OthersReaction,
  Pattern,
  Season,
  Slot,
  StyleTag,
  SubType,
  TimeOfDay,
  ItemStatus,
} from './types';

export const CATEGORY_LABEL: Record<Category, string> = {
  TOP: '상의',
  BOTTOM: '하의',
  OUTER: '아우터',
  SHOES: '신발',
  BAG: '가방',
  ACC: '액세서리',
};
export const CATEGORIES: Category[] = ['TOP', 'BOTTOM', 'OUTER', 'SHOES', 'BAG', 'ACC'];

export const SLOT_LABEL: Record<Slot, string> = {
  TOP: 'TOP',
  BOTTOM: 'BOTTOM',
  OUTER: 'OUTER',
  SHOES: 'SHOES',
  BAG: 'BAG',
};
export const SLOTS: Slot[] = ['TOP', 'BOTTOM', 'OUTER', 'SHOES', 'BAG'];

interface SubTypeMeta {
  label: string;
  category: Category;
  /** 실루엣 볼륨: -1 = 슬림, 0 = 레귤러, 1 = 볼륨 */
  volume: -1 | 0 | 1;
  /** 보온 정도 0~3 */
  warmth: number;
}

export const SUBTYPE: Record<SubType, SubTypeMeta> = {
  TEE: { label: '티셔츠', category: 'TOP', volume: 0, warmth: 0 },
  KNIT: { label: '니트', category: 'TOP', volume: 0, warmth: 2 },
  SHIRT: { label: '셔츠', category: 'TOP', volume: -1, warmth: 1 },
  CARDIGAN: { label: '가디건', category: 'OUTER', volume: 0, warmth: 1 },
  SLACKS: { label: '슬랙스', category: 'BOTTOM', volume: -1, warmth: 1 },
  WIDE: { label: '와이드 팬츠', category: 'BOTTOM', volume: 1, warmth: 1 },
  CHINO: { label: '치노 팬츠', category: 'BOTTOM', volume: 0, warmth: 1 },
  SKIRT: { label: '스커트', category: 'BOTTOM', volume: 1, warmth: 0 },
  JACKET: { label: '재킷', category: 'OUTER', volume: 0, warmth: 2 },
  COAT: { label: '코트', category: 'OUTER', volume: 1, warmth: 3 },
  LOAFER: { label: '로퍼', category: 'SHOES', volume: 0, warmth: 0 },
  SNEAKER: { label: '스니커즈', category: 'SHOES', volume: 0, warmth: 0 },
  TOTE: { label: '토트백', category: 'BAG', volume: 0, warmth: 0 },
  SHOULDER: { label: '숄더백', category: 'BAG', volume: 0, warmth: 0 },
  WATCH: { label: '시계', category: 'ACC', volume: 0, warmth: 0 },
};

export const SUBTYPES_BY_CATEGORY: Record<Category, SubType[]> = CATEGORIES.reduce(
  (acc, c) => {
    acc[c] = (Object.keys(SUBTYPE) as SubType[]).filter((s) => SUBTYPE[s].category === c);
    return acc;
  },
  {} as Record<Category, SubType[]>,
);

interface ColorMeta {
  label: string;
  hex: string;
  /** 무채색·뉴트럴 계열 여부 (조합 부담이 적다) */
  neutral: boolean;
  /** 밝기 0(어두움)~1(밝음) */
  light: number;
}

export const COLOR: Record<ColorKey, ColorMeta> = {
  navy: { label: '네이비', hex: '#22305A', neutral: true, light: 0.15 },
  black: { label: '블랙', hex: '#1C1C1E', neutral: true, light: 0.05 },
  charcoal: { label: '차콜', hex: '#3A3B3F', neutral: true, light: 0.2 },
  gray: { label: '그레이', hex: '#9A9CA1', neutral: true, light: 0.55 },
  white: { label: '화이트', hex: '#F7F7F5', neutral: true, light: 1 },
  ivory: { label: '아이보리', hex: '#EFE8D8', neutral: true, light: 0.92 },
  beige: { label: '베이지', hex: '#CDB591', neutral: true, light: 0.72 },
  camel: { label: '카멜', hex: '#B08453', neutral: false, light: 0.5 },
  brown: { label: '브라운', hex: '#6E4A33', neutral: false, light: 0.3 },
  khaki: { label: '카키', hex: '#6E6B4E', neutral: false, light: 0.38 },
  lightblue: { label: '라이트블루', hex: '#A9C3E3', neutral: false, light: 0.8 },
  burgundy: { label: '버건디', hex: '#6B2333', neutral: false, light: 0.2 },
};
export const COLOR_KEYS = Object.keys(COLOR) as ColorKey[];

/** 잘 어울린다고 알려진 색 조합 (순서 무관) */
export const GOOD_PAIRS: [ColorKey, ColorKey][] = [
  ['navy', 'beige'],
  ['navy', 'white'],
  ['navy', 'gray'],
  ['navy', 'lightblue'],
  ['navy', 'camel'],
  ['black', 'white'],
  ['black', 'gray'],
  ['black', 'camel'],
  ['charcoal', 'white'],
  ['charcoal', 'lightblue'],
  ['gray', 'white'],
  ['beige', 'white'],
  ['beige', 'brown'],
  ['beige', 'ivory'],
  ['ivory', 'khaki'],
  ['khaki', 'white'],
  ['brown', 'ivory'],
  ['burgundy', 'gray'],
  ['burgundy', 'navy'],
  ['lightblue', 'beige'],
];

export const PATTERN_LABEL: Record<Pattern, string> = {
  SOLID: '솔리드',
  STRIPE: '스트라이프',
  CHECK: '체크',
  ETC: '기타',
};

export const SEASON_LABEL: Record<Season, string> = {
  SPRING: '봄',
  SUMMER: '여름',
  FALL: '가을',
  WINTER: '겨울',
};
export const SEASONS: Season[] = ['SPRING', 'SUMMER', 'FALL', 'WINTER'];

export const STYLE_LABEL: Record<StyleTag, string> = {
  NEAT: '단정',
  MINIMAL: '미니멀',
  CASUAL: '캐주얼',
  CLASSIC: '클래식',
  STREET: '스트릿',
};
export const STYLE_TAGS: StyleTag[] = ['NEAT', 'MINIMAL', 'CASUAL', 'CLASSIC', 'STREET'];

export const STATUS_LABEL: Record<ItemStatus, string> = {
  OWNED: '보유 중',
  LAUNDRY: '세탁 중',
  REPAIR: '수선 중',
};

export const FORMALITY_LABEL: Record<Formality, string> = {
  CASUAL: '편한 데일리',
  BIZ_CASUAL: '비즈니스 캐주얼',
  FORMAL: '포멀',
};
export const FORMALITY_LEVEL: Record<Formality, 1 | 2 | 3> = {
  CASUAL: 1,
  BIZ_CASUAL: 2,
  FORMAL: 3,
};

export const MOOD_LABEL: Record<Mood, string> = {
  NEAT: '단정',
  COMFY: '편안',
  CHIC: '세련',
};

export const OCCASION_LABEL: Record<Occasion, string> = {
  COMMUTE: '출근',
  MEETING: '회의',
  FIELD: '외근',
  DINNER: '회식',
  DAILY: '데일리',
  INTERVIEW: '면접',
  WEDDING: '결혼식 하객',
  DATE: '데이트',
  TRAVEL: '여행',
  GATHERING: '모임',
};

/** 상황별 기대 격식 (1~3) */
export const OCCASION_FORMALITY: Record<Occasion, number> = {
  COMMUTE: 2,
  MEETING: 2.5,
  FIELD: 2,
  DINNER: 1.5,
  DAILY: 1,
  INTERVIEW: 3,
  WEDDING: 2.7,
  DATE: 1.7,
  TRAVEL: 1,
  GATHERING: 1.5,
};

export const WORK_OCCASIONS: Occasion[] = ['COMMUTE', 'MEETING', 'FIELD', 'DINNER'];
export const PLAN_OCCASIONS: Occasion[] = [
  'INTERVIEW',
  'WEDDING',
  'DATE',
  'TRAVEL',
  'FIELD',
  'GATHERING',
  'MEETING',
];

export const TIME_LABEL: Record<TimeOfDay, string> = { AM: '오전', PM: '오후', EVENING: '저녁' };

export const EMOTION_LABEL: Record<Emotion, string> = {
  CONFIDENT: '자신감',
  COMFY: '편안함',
  AWKWARD: '어색함',
  UNCOMFY: '불편함',
};

export const OTHERS_LABEL: Record<OthersReaction, string> = {
  PRAISE: '칭찬 받음',
  NORMAL: '보통',
  MEH: '아쉬움',
};

export const DISLIKE_REASONS = ['색 조합', '격식이 안 맞음', '날씨와 안 맞음', '자주 입은 옷', '내 취향 아님'];

export const WEEKDAY_SHORT = ['일', '월', '화', '수', '목', '금', '토'];
