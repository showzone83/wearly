// 샘플 옷장으로 시작할 때 캘린더·진단 화면이 비어 보이지 않도록 지난 기록과 예정일을 만든다.
import type { DailyLook, PlannedEvent, Profile, WardrobeItem } from '@/data/types';
import { addDays, todayKey, weekday } from './date';
import { dayContext } from './context';
import { diagnose } from './diagnose';
import { recommend, type EngineInput } from './recommend';
import { forecast } from './weather';

function input(items: WardrobeItem[], profile: Profile, ctx: EngineInput['ctx']): EngineInput {
  return { items, profile, affinity: {}, ctx, recentOutfits: [] };
}

export function buildSampleHistory(items: WardrobeItem[], profile: Profile) {
  const today = todayKey();
  const looks: DailyLook[] = [];

  // 최근 근무일 2일치 착용 기록
  let cursor = addDays(today, -1);
  const scores = [{ self: 4, others: 'PRAISE' as const }, { self: 3, others: 'NORMAL' as const }];
  while (looks.length < 2) {
    if (profile.workDays.includes(weekday(cursor))) {
      const ctx = { ...dayContext(cursor, profile), excludeRecent: false };
      const pick = recommend(input(items, profile, ctx), 3)[looks.length];
      if (pick) {
        const meta = scores[looks.length];
        looks.push({
          id: `sample-look-${cursor}`,
          date: cursor,
          items: pick.items,
          occasion: ctx.occasion,
          selfScore: meta.self,
          emotions: meta.self >= 4 ? ['CONFIDENT'] : ['COMFY'],
          memo: meta.self >= 4 ? '하루 종일 편했다' : '조금 추웠다',
          others: meta.others,
          praisedItemId: meta.others === 'PRAISE' ? pick.items.OUTER ?? pick.items.TOP : undefined,
          diagnosis: diagnose(pick.items, input(items, profile, ctx)),
          createdAt: new Date().toISOString(),
        });
      }
    }
    cursor = addDays(cursor, -1);
    if (cursor < addDays(today, -14)) break;
  }

  // 다가오는 예정일: 하나는 추천 저장, 하나는 추천 대기
  const weddingDate = addDays(today, 24);
  const weddingWeather = forecast(weddingDate);
  const weddingCtx: EngineInput['ctx'] = {
    occasion: 'WEDDING',
    mood: 'CHIC',
    formality: 'FORMAL',
    concepts: ['MINIMAL', 'CLASSIC'],
    weather: { ...weddingWeather, manual: true },
    date: weddingDate,
  };
  const weddingOutfit = recommend(input(items, profile, weddingCtx), 1)[0];
  const plans: PlannedEvent[] = [
    {
      id: 'sample-plan-1',
      date: weddingDate,
      timeOfDay: 'PM',
      title: '결혼식 하객',
      occasion: 'WEDDING',
      concepts: ['MINIMAL', 'CLASSIC'],
      weather: weddingCtx.weather!,
      outfit: weddingOutfit,
    },
    {
      id: 'sample-plan-2',
      date: addDays(today, 8),
      timeOfDay: 'AM',
      title: '외근 · 거래처 미팅',
      occasion: 'FIELD',
      concepts: ['CLASSIC'],
      weather: forecast(addDays(today, 8)),
    },
  ];

  return { looks, plans, dayOutfits: {} };
}
