// 사용하지 않는 옷 (doc/13_옷_사용_중지.md): 판별·이유 코드·표시 도우미.
// 추천 엔진이 Node(검증 스크립트)에서도 돌 수 있도록 React Native·Expo를 import하지 않는다.
import type { RetireReason, WardrobeItem } from '@/data/types';
import { activeCodes, codeOf } from './codes';

/** 서버에 RETIRE_REASON 코드 그룹이 아직 없을 때 쓰는 기본 목록 (doc/13 2장) */
export const DEFAULT_RETIRE_REASONS: readonly { code: RetireReason; label: string }[] = [
  { code: 'NOT_WORN', label: '안 입어요' },
  { code: 'DISCARDED', label: '버렸어요' },
  { code: 'DONATED', label: '기부했어요' },
  { code: 'SOLD', label: '팔았어요' },
  { code: 'ETC', label: '기타' },
];

/** 메모 최대 길이 (서버 retire_memo VARCHAR(200)) */
export const RETIRE_MEMO_MAX = 200;

/** 사용하지 않는 옷인지 */
export const isRetired = (i: Pick<WardrobeItem, 'retiredAt'>): boolean => !!i.retiredAt;

/** 사용 중인 옷만 (옷장·추천·통계 기준) */
export const activeItems = <T extends Pick<WardrobeItem, 'retiredAt'>>(items: readonly T[]): T[] =>
  items.filter((i) => !i.retiredAt);

/** 사용하지 않는 옷만 */
export const retiredItems = <T extends Pick<WardrobeItem, 'retiredAt'>>(items: readonly T[]): T[] =>
  items.filter((i) => !!i.retiredAt);

/** 고를 수 있는 이유: 서버 코드(활성)가 있으면 그것, 없으면 기본 목록 */
export const retireReasonOptions = (): RetireReason[] => {
  const fromServer = activeCodes('RETIRE_REASON');
  return fromServer.length ? fromServer : DEFAULT_RETIRE_REASONS.map((r) => r.code);
};

/** 이유 이름. 서버 코드 → 기본 목록 → 코드 문자열 순 */
export const retireReasonLabel = (code: RetireReason | undefined): string => {
  if (!code) return '';
  return codeOf('RETIRE_REASON', code)?.label ?? DEFAULT_RETIRE_REASONS.find((r) => r.code === code)?.label ?? code;
};

/** ISO 시각 → "2026.10.17" (기기 시간대) */
export const retiredDateLabel = (iso: string | undefined): string => {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
};

/** 배너 한 줄: "사용하지 않는 옷 · 기부했어요 · 2026.10.17" */
export const retiredSummary = (i: Pick<WardrobeItem, 'retiredAt' | 'retireReason'>): string =>
  ['사용하지 않는 옷', retireReasonLabel(i.retireReason), retiredDateLabel(i.retiredAt)].filter(Boolean).join(' · ');
