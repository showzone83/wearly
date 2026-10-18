// 옷의 여러 색(주 색상 + 보조 색)과 구입일 도우미 (GitHub 이슈 #4).
// 추천 엔진이 Node(검증 스크립트)에서도 돌 수 있도록 React Native·Expo를 import하지 않는다.
import type { ColorKey, WardrobeItem } from '@/data/types';
import { toKey, todayKey } from './date';

/** 한 옷에 고를 수 있는 색 수 (서버 colors TOO_MANY{max:3}과 같다) */
export const MAX_COLORS = 3;

/** 구입일로 고를 수 있는 가장 이른 날짜 (서버 purchasedAt 범위의 min) */
export const PURCHASE_MIN = '1990-01-01';

type TColored = Pick<WardrobeItem, 'color' | 'colors'>;

/** 옷의 색 목록 (주 색상 먼저). colors가 없거나 비었으면(예전 데이터·예전 서버) [color] */
export const itemColors = (item: TColored): ColorKey[] => (item.colors && item.colors.length > 0 ? item.colors : [item.color]);

/** 보조 색 (주 색상을 뺀 나머지). 없으면 빈 목록 */
export const subColors = (item: TColored): ColorKey[] => itemColors(item).slice(1);

/** 색 목록 정리: 빈 값·중복을 빼고 최대 3개. 남는 게 없으면 [fallback] */
export const normalizeColors = (colors: readonly ColorKey[] | null | undefined, fallback: ColorKey): ColorKey[] => {
  const out: ColorKey[] = [];
  for (const c of colors ?? []) if (c && !out.includes(c) && out.length < MAX_COLORS) out.push(c);
  return out.length > 0 ? out : [fallback];
};

/** 색을 더 고르거나 빼지 못한 이유: FULL = 이미 3개, LAST = 마지막 하나는 뺄 수 없다 */
export type TColorBlock = 'FULL' | 'LAST';

export interface IColorPick {
  colors: ColorKey[];
  blocked?: TColorBlock;
}

/** 색 칩 탭: 고른 색이면 빼고(마지막 하나는 남긴다), 아니면 끝에 더한다(3개까지) */
export const toggleColor = (colors: readonly ColorKey[], c: ColorKey): IColorPick => {
  if (colors.includes(c)) {
    if (colors.length <= 1) return { colors: [...colors], blocked: 'LAST' };
    return { colors: colors.filter((x) => x !== c) };
  }
  if (colors.length >= MAX_COLORS) return { colors: [...colors], blocked: 'FULL' };
  return { colors: [...colors, c] };
};

/** 색 칩 길게 누르기: 그 색을 주 색상(맨 앞)으로. 고르지 않은 색이면 고르면서 맨 앞에 둔다(3개까지) */
export const makePrimaryColor = (colors: readonly ColorKey[], c: ColorKey): IColorPick => {
  if (colors.includes(c)) return { colors: [c, ...colors.filter((x) => x !== c)] };
  if (colors.length >= MAX_COLORS) return { colors: [...colors], blocked: 'FULL' };
  return { colors: [c, ...colors] };
};

// ── 구입일 ──

/** createdAt(ISO 시각 또는 YYYY-MM-DD) → 기기 날짜 YYYY-MM-DD */
export const dateKeyOf = (createdAt: string): string => {
  if (/^\d{4}-\d{2}-\d{2}$/.test(createdAt)) return createdAt;
  const d = new Date(createdAt);
  return Number.isNaN(d.getTime()) ? todayKey() : toKey(d);
};

/**
 * 폼의 구입일(undefined = 모름) → 저장할 값. 모르면 등록일을 넣고 estimated = true (서버와 같은 규칙).
 * 게스트(기기 저장) 모드에서 쓴다. 서버 계정은 서버가 정한다
 */
export const purchaseFields = (
  purchasedAt: string | undefined,
  createdAt: string,
): Required<Pick<WardrobeItem, 'purchasedAt' | 'purchasedAtEstimated'>> =>
  purchasedAt ? { purchasedAt, purchasedAtEstimated: false } : { purchasedAt: dateKeyOf(createdAt), purchasedAtEstimated: true };

/** 화면에 보일 구입일. 예전 데이터(구입일 없음)는 등록일을 "모름"으로 본다 */
export const purchaseInfo = (
  item: Pick<WardrobeItem, 'purchasedAt' | 'purchasedAtEstimated' | 'createdAt'>,
): { date: string; estimated: boolean } =>
  item.purchasedAt
    ? { date: item.purchasedAt, estimated: item.purchasedAtEstimated === true }
    : { date: dateKeyOf(item.createdAt), estimated: true };

/** "2026-03-02" 또는 "모름 (등록일 2026-10-18)" */
export const purchaseText = (item: Pick<WardrobeItem, 'purchasedAt' | 'purchasedAtEstimated' | 'createdAt'>): string => {
  const { date, estimated } = purchaseInfo(item);
  return estimated ? `모름 (등록일 ${date})` : date;
};

/** 구입일로 고를 수 있는 날짜인지 (1990-01-01 ~ 오늘, 미래 불가) */
export const isPurchaseDateAllowed = (key: string, today: string = todayKey()): boolean =>
  /^\d{4}-\d{2}-\d{2}$/.test(key) && key >= PURCHASE_MIN && key <= today;
