import type { Formality, Profile, RecommendContext } from '@/data/types';
import { weekday } from './date';
import { forecast } from './weather';

export function isWorkDay(date: string, profile: Profile): boolean {
  return profile.workDays.includes(weekday(date));
}

/** 홈 화면 추천 조건: 근무 패턴 + 그날 예보 (기획서 p.20, p.21) */
export function dayContext(date: string, profile: Profile): RecommendContext {
  const work = isWorkDay(date, profile);
  const workDress: Formality = profile.dressCodes.includes('FORMAL') && !profile.dressCodes.includes('BIZ_CASUAL')
    ? 'FORMAL'
    : 'BIZ_CASUAL';
  if (work && profile.workRule) {
    return { occasion: 'COMMUTE', mood: 'NEAT', formality: workDress, weather: forecast(date), date, excludeRecent: true };
  }
  if (!work && profile.offRule) {
    return { occasion: 'DAILY', mood: 'COMFY', formality: 'CASUAL', weather: forecast(date), date, excludeRecent: true };
  }
  return { occasion: 'COMMUTE', mood: 'NEAT', formality: workDress, weather: forecast(date), date, excludeRecent: true };
}

export function newId(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}
