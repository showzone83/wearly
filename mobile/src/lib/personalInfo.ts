// 나의 정보 (GitHub 이슈 #5): 연령대·체형·키·핏 선호. 모두 선택 입력이고 언제든 비울 수 있다.
// 코디 추천(AI)에 참고로 주고, 고도화 때 새 옷 추천에 쓴다. 코드 값은 서버 코드 그룹 AGE_RANGE·BODY_TYPE·FIT.
// 화면이 아닌 순수 로직이라 React Native·Expo를 import하지 않는다.
import type { Profile } from '@/data/types';
import { codeLabel, codeOf } from './codes';

export const HEIGHT_MIN = 130;
export const HEIGHT_MAX = 210;

/** 체형 "잘 모르겠어요" 코드 (서버도 AI 프롬프트에서 뺀다) */
export const BODY_TYPE_UNKNOWN = 'UNKNOWN';

/** 개인정보 안내 문구 (나의 정보 화면·온보딩 공통) */
export const PERSONAL_NOTICE = '코디·새 옷 추천에만 써요. 언제든 지울 수 있어요';

export interface IPersonalInfo {
  ageRange: string | null;
  bodyType: string | null;
  heightCm: number | null;
  fitPreference: string | null;
}

export const EMPTY_PERSONAL: IPersonalInfo = { ageRange: null, bodyType: null, heightCm: null, fitPreference: null };

/** 프로필의 나의 정보 (예전에 저장한 프로필은 필드가 없어 null로 맞춘다) */
export const personalOf = (p: Profile): IPersonalInfo => ({
  ageRange: p.ageRange ?? null,
  bodyType: p.bodyType ?? null,
  heightCm: p.heightCm ?? null,
  fitPreference: p.fitPreference ?? null,
});

export const withPersonal = (p: Profile, info: IPersonalInfo): Profile => ({ ...p, ...info });

export const isPersonalEmpty = (info: IPersonalInfo): boolean =>
  info.ageRange === null && info.bodyType === null && info.heightCm === null && info.fitPreference === null;

export type THeightInput = { ok: true; value: number | null } | { ok: false };

/**
 * 키 입력 칸 글자 → 저장 값. 빈 칸이면 null(비움), 130~210 정수만 받는다.
 * 숫자 말고 다른 글자나 범위 밖이면 { ok: false }.
 */
export const parseHeight = (text: string): THeightInput => {
  const t = text.trim();
  if (!t) return { ok: true, value: null };
  if (!/^\d{2,3}$/.test(t)) return { ok: false };
  const n = Number(t);
  return n >= HEIGHT_MIN && n <= HEIGHT_MAX ? { ok: true, value: n } : { ok: false };
};

export const heightText = (cm: number | null): string => (cm === null ? '' : String(cm));

export interface IBodyTypeInfo {
  code: string;
  label: string;
  /** 짧은 설명 (attrs.description) */
  description: string | null;
  /** 고르기를 돕는 한 줄 특징 (attrs.hint) */
  hint: string | null;
}

const str = (v: unknown): string | null => (typeof v === 'string' && v.trim() ? v.trim() : null);

/** 체형 코드의 이름·설명. 관리자가 코드 관리에서 바꿀 수 있어 코드 테이블에서 읽는다 */
export const bodyTypeInfo = (code: string): IBodyTypeInfo => {
  const c = codeOf('BODY_TYPE', code);
  return { code, label: c?.label ?? code, description: str(c?.attrs.description), hint: str(c?.attrs.hint) };
};

/** 마이 화면 메뉴에 보일 요약. 비어 있으면 null */
export const personalSummary = (info: IPersonalInfo): string | null => {
  const parts: string[] = [];
  if (info.ageRange) parts.push(codeLabel('AGE_RANGE', info.ageRange));
  if (info.bodyType && info.bodyType !== BODY_TYPE_UNKNOWN) parts.push(codeLabel('BODY_TYPE', info.bodyType));
  if (info.heightCm !== null) parts.push(`${info.heightCm}cm`);
  if (info.fitPreference) parts.push(codeLabel('FIT', info.fitPreference));
  // "잘 모르겠어요"만 고른 경우에는 그 이름을 보여 준다
  if (!parts.length && info.bodyType) parts.push(codeLabel('BODY_TYPE', info.bodyType));
  return parts.length ? parts.join(' · ') : null;
};
