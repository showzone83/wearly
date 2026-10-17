// 모델 드롭다운(pages/providers/ModelPicker.tsx)의 순수 함수. 화면 없이 단위 테스트한다 (tests/modelPicker.test.mjs).
// 값을 import하지 않는다 — node --test가 이 파일을 바로 읽는다(타입 import는 지워진다).
import type { IProviderModel, IProviderModelList } from '@/api';

/** 드롭다운 모드: 목록에서 고르기 / 직접 입력 */
export type TPickerMode = 'select' | 'manual';

/** 목록에서 그 ID의 모델 (앞뒤 공백 무시) */
export const findModel = (models: readonly IProviderModel[], id: string | null | undefined): IProviderModel | null => {
  const v = (id ?? '').trim();
  if (!v) return null;
  return models.find((m) => m.id === v) ?? null;
};

/**
 * 처음 보여 줄 모드. 목록이 비었거나 지금 값이 목록에 없으면 직접 입력(지금 값을 그대로 보여 준다),
 * 값이 없거나 목록에 있으면 목록에서 고르기.
 */
export const initialMode = (models: readonly IProviderModel[], value: string | null | undefined): TPickerMode => {
  if (models.length === 0) return 'manual';
  const v = (value ?? '').trim();
  if (!v) return 'select';
  return findModel(models, v) ? 'select' : 'manual';
};

/** 지금 값이 있는데 목록에 없다 ("목록에 없는 모델" 안내) */
export const isUnlisted = (models: readonly IProviderModel[], value: string | null | undefined): boolean => {
  const v = (value ?? '').trim();
  return models.length > 0 && v !== '' && !findModel(models, v);
};

/** 검색어로 거르기: ID·화면 이름에 들어 있으면 (대소문자 무시). 순서는 그대로 */
export const filterModels = (models: readonly IProviderModel[], query: string): IProviderModel[] => {
  const q = query.trim().toLowerCase();
  if (!q) return [...models];
  return models.filter((m) => m.id.toLowerCase().includes(q) || m.displayName.toLowerCase().includes(q));
};

/**
 * 목록 위 출처 문구.
 * - LIVE: "제공사에서 받은 목록 · 3분 전" (relative는 호출하는 쪽이 fmtRelative로 만든다)
 * - CATALOG: "내장 목록(키가 없거나 목록을 받지 못했어요: <서버 안내>)"
 */
export const sourceText = (list: Pick<IProviderModelList, 'source' | 'error'>, relative: string): string => {
  if (list.source === 'LIVE') return `제공사에서 받은 목록 · ${relative}`;
  const reason = list.error?.message?.trim();
  return reason ? `내장 목록(키가 없거나 목록을 받지 못했어요: ${reason})` : '내장 목록';
};

/** 아직 저장하지 않은 공급자(추가 중이거나 종류를 바꾼 중)의 출처 문구 */
export const UNSAVED_SOURCE_TEXT = '내장 목록(저장한 뒤 키가 있으면 제공사 목록을 받을 수 있어요)';

/** 키보드로 강조 항목 옮기기. 항목 수(count)에는 맨 아래 "직접 입력…"이 들어 있다 */
export const moveIndex = (current: number, delta: number, count: number): number => {
  if (count <= 0) return -1;
  if (current < 0) return delta > 0 ? 0 : count - 1;
  return (current + delta + count) % count;
};
