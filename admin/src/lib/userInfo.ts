// 회원 나의 정보(연령대·체형·키·핏 선호) 표시 (GitHub 이슈 #5). 조회만 한다.
import type { IAdminUser } from '@/api';

type TLabel = (group: string, code: string) => string;

export interface IPersonalPart {
  /** 항목 이름 */
  name: string;
  /** 보이는 값 (코드는 라벨로) */
  text: string;
  /** 코드 값 (키는 null) */
  code: string | null;
}

/** 입력된 항목만 순서대로. 하나도 없으면 빈 배열 */
export const personalParts = (u: IAdminUser, label: TLabel): IPersonalPart[] => {
  const parts: IPersonalPart[] = [];
  if (u.ageRange) parts.push({ name: '연령대', text: label('AGE_RANGE', u.ageRange), code: u.ageRange });
  if (u.bodyType) parts.push({ name: '체형', text: label('BODY_TYPE', u.bodyType), code: u.bodyType });
  if (typeof u.heightCm === 'number') parts.push({ name: '키', text: `${u.heightCm}cm`, code: null });
  if (u.fitPreference) parts.push({ name: '핏 선호', text: label('FIT', u.fitPreference), code: u.fitPreference });
  return parts;
};

/** 표에 보일 한 줄 요약 ("30대 초반 · 웨이브 · 162cm · 슬림핏"). 없으면 null */
export const personalSummary = (u: IAdminUser, label: TLabel): string | null => {
  const parts = personalParts(u, label);
  return parts.length ? parts.map((p) => p.text).join(' · ') : null;
};
