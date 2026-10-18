// 시나리오에서 쓰는 고정 데이터와 날짜 도구.
import type { ItemDto, ItemInput, ProfileDto } from '../../../mobile/src/api/types';

/**
 * 앱 DEFAULT_PROFILE(mobile/src/data/seed.ts)과 같은 값.
 * seed.ts는 '@/lib/date'를 import해서 Node에서 바로 가져올 수 없어 복사해 둔다. 바뀌면 같이 고친다.
 */
export const DEFAULT_PROFILE_WITHOUT_NAME: Omit<ProfileDto, 'name' | 'onboarded'> = {
  dressCodes: ['BIZ_CASUAL'],
  moods: ['NEAT', 'MINIMAL'],
  colors: ['navy', 'beige', 'white'],
  workType: 'WEEKDAY',
  workDays: [1, 2, 3, 4, 5],
  workRule: true,
  offRule: true,
  notify: { morning: true, evening: true, dMinus1: true },
  // 나의 정보 (선택): 처음에는 모두 비어 있다
  ageRange: null,
  bodyType: null,
  heightCm: null,
  fitPreference: null,
};
export const DEFAULT_PROFILE_NAME = '웨어리 사용자';

/** 서울 기준 오늘 (YYYY-MM-DD) */
export function todaySeoul(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date());
}

export function addDays(day: string, n: number): string {
  const [y, m, d] = day.split('-').map(Number);
  const t = new Date(Date.UTC(y, m - 1, d + n));
  return t.toISOString().slice(0, 10);
}

export function item(overrides: Partial<ItemInput> = {}): ItemInput {
  return {
    name: '네이비 옥스퍼드 셔츠',
    category: 'TOP',
    subType: 'SHIRT',
    color: 'navy',
    pattern: 'SOLID',
    seasons: ['SPRING', 'FALL'],
    styles: ['NEAT', 'CLASSIC'],
    formality: 2,
    status: 'OWNED',
    brand: '하네스상회',
    wornCount: 0,
    lastWornAt: null,
    ...overrides,
  };
}

/** 서버 응답을 입력과 비교할 수 있게 선택 필드를 정규화한다 (없음 = null). */
export function comparable(x: ItemInput | ItemDto) {
  return {
    name: x.name,
    category: x.category,
    subType: x.subType,
    color: x.color,
    pattern: x.pattern,
    seasons: [...x.seasons],
    styles: [...x.styles],
    formality: x.formality,
    status: x.status,
    brand: x.brand ?? null,
    wornCount: x.wornCount ?? 0,
    lastWornAt: x.lastWornAt ?? null,
  };
}

export const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
