// 앱 로직 값(상황·분위기·격식·감정 등). 옷 속성(종류·세부 종류·색·패턴·계절·스타일·보유 상태)의
// 라벨과 속성은 서버 코드에서 읽는다 → src/lib/codes.ts
// 이 파일은 연동 하네스(Node)도 타입 검사에 포함하므로 순수 TS로 둔다.
import type { Emotion, Formality, Mood, Occasion, OthersReaction, Slot, TimeOfDay } from './types';

/** 코디 칸 순서 */
export const SLOTS: Slot[] = ['TOP', 'BOTTOM', 'DRESS', 'OUTER', 'SHOES', 'BAG'];

/** 코디 칸 이름. 같은 칸의 CATEGORY 코드가 있으면 그 라벨을 먼저 쓴다(slotLabel) */
export const SLOT_LABEL: Record<Slot, string> = {
  TOP: '상의',
  BOTTOM: '하의',
  DRESS: '원피스',
  OUTER: '아우터',
  SHOES: '신발',
  BAG: '가방',
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
