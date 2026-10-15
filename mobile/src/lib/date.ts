import { WEEKDAY_SHORT } from '@/data/constants';

/** 로컬 시간 기준 YYYY-MM-DD */
export function toKey(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function fromKey(key: string): Date {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function todayKey(): string {
  return toKey(new Date());
}

export function addDays(key: string, n: number): string {
  const d = fromKey(key);
  d.setDate(d.getDate() + n);
  return toKey(d);
}

export function diffDays(a: string, b: string): number {
  return Math.round((fromKey(a).getTime() - fromKey(b).getTime()) / 86400000);
}

export function weekday(key: string): number {
  return fromKey(key).getDay();
}

/** "10월 14일 수요일" */
export function longLabel(key: string): string {
  const d = fromKey(key);
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${WEEKDAY_SHORT[d.getDay()]}요일`;
}

/** "10.14 (수)" */
export function shortLabel(key: string): string {
  const d = fromKey(key);
  return `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, '0')} (${WEEKDAY_SHORT[d.getDay()]})`;
}

/** 해당 날짜가 속한 주의 월요일 */
export function weekStart(key: string): string {
  const wd = weekday(key);
  return addDays(key, wd === 0 ? -6 : 1 - wd);
}

/** "2026년 10월 · 3주차" (월요일 기준) */
export function weekTitle(mondayKey: string): string {
  const d = fromKey(mondayKey);
  const first = new Date(d.getFullYear(), d.getMonth(), 1);
  const offset = (first.getDay() + 6) % 7;
  const nth = Math.floor((d.getDate() + offset - 1) / 7) + 1;
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 · ${nth}주차`;
}
