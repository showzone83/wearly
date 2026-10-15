import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import { DEFAULT_PROFILE, seedItems } from '@/data/seed';
import type {
  DailyLook,
  FeedbackType,
  Outfit,
  OutfitItems,
  PlannedEvent,
  Profile,
  SavedOutfit,
  WardrobeItem,
} from '@/data/types';
import { addDays, diffDays, todayKey } from '@/lib/date';
import type { EngineInput } from '@/lib/recommend';
import { buildSampleHistory } from '@/lib/sample';

/** 추천 결과 화면으로 넘기는 임시 데이터 (저장하지 않음) */
export interface Draft {
  mode: 'TODAY' | 'PLAN';
  outfits: Outfit[];
  plan?: Omit<PlannedEvent, 'id' | 'outfit'>;
}

interface State {
  onboarded: boolean;
  profile: Profile;
  items: WardrobeItem[];
  dayOutfits: Record<string, SavedOutfit>;
  plans: PlannedEvent[];
  looks: DailyLook[];
  affinity: Record<string, number>;
  draft: Draft | null;

  completeOnboarding: (profile: Profile, withSample: boolean) => void;
  updateProfile: (patch: Partial<Profile>) => void;
  addItem: (item: WardrobeItem) => void;
  updateItem: (id: string, patch: Partial<WardrobeItem>) => void;
  deleteItem: (id: string) => void;
  saveDayOutfit: (date: string, outfit: Outfit) => void;
  setFeedback: (date: string, type: FeedbackType, reasons?: string[]) => void;
  bumpAffinity: (ids: (string | undefined)[], delta: number) => void;
  addPlan: (plan: PlannedEvent) => void;
  deletePlan: (id: string) => void;
  addLook: (look: DailyLook) => void;
  setDraft: (draft: Draft | null) => void;
  loadSample: () => void;
  resetAll: () => void;
}

const EMPTY = {
  onboarded: false,
  profile: DEFAULT_PROFILE,
  items: [] as WardrobeItem[],
  dayOutfits: {} as Record<string, SavedOutfit>,
  plans: [] as PlannedEvent[],
  looks: [] as DailyLook[],
  affinity: {} as Record<string, number>,
  draft: null as Draft | null,
};

function removeItemRefs(items: OutfitItems, id: string): OutfitItems {
  const next = { ...items };
  for (const k of Object.keys(next) as (keyof OutfitItems)[]) if (next[k] === id) delete next[k];
  return next;
}

export const useStore = create<State>()(
  persist(
    (set, get) => ({
      ...EMPTY,

      completeOnboarding: (profile, withSample) => {
        if (withSample) {
          const items = seedItems();
          set({ onboarded: true, profile, items, ...buildSampleHistory(items, profile) });
        } else {
          set({ onboarded: true, profile });
        }
      },

      updateProfile: (patch) => set({ profile: { ...get().profile, ...patch } }),

      addItem: (item) => set({ items: [item, ...get().items] }),

      updateItem: (id, patch) =>
        set({ items: get().items.map((i) => (i.id === id ? { ...i, ...patch } : i)) }),

      deleteItem: (id) => {
        const s = get();
        const dayOutfits: Record<string, SavedOutfit> = {};
        for (const [k, v] of Object.entries(s.dayOutfits)) {
          dayOutfits[k] = { ...v, outfit: { ...v.outfit, items: removeItemRefs(v.outfit.items, id) } };
        }
        set({
          items: s.items.filter((i) => i.id !== id),
          dayOutfits,
          plans: s.plans.map((p) =>
            p.outfit ? { ...p, outfit: { ...p.outfit, items: removeItemRefs(p.outfit.items, id) } } : p,
          ),
        });
      },

      saveDayOutfit: (date, outfit) =>
        set({ dayOutfits: { ...get().dayOutfits, [date]: { outfit, savedAt: new Date().toISOString() } } }),

      setFeedback: (date, type, reasons) => {
        const saved = get().dayOutfits[date];
        if (!saved) return;
        get().bumpAffinity(Object.values(saved.outfit.items), type === 'LIKE' ? 1 : -0.5);
        set({ dayOutfits: { ...get().dayOutfits, [date]: { ...saved, feedback: type, dislikeReasons: reasons } } });
      },

      bumpAffinity: (ids, delta) => {
        const affinity = { ...get().affinity };
        for (const id of ids) if (id) affinity[id] = Math.max(-3, Math.min(3, (affinity[id] ?? 0) + delta));
        set({ affinity });
      },

      addPlan: (plan) =>
        set({ plans: [...get().plans.filter((p) => p.id !== plan.id), plan].sort((a, b) => a.date.localeCompare(b.date)) }),

      deletePlan: (id) => set({ plans: get().plans.filter((p) => p.id !== id) }),

      addLook: (look) => {
        const s = get();
        const worn = new Set(Object.values(look.items).filter(Boolean) as string[]);
        const items = s.items.map((i) => {
          if (!worn.has(i.id)) return i;
          const already = s.looks.some((l) => l.date === look.date && Object.values(l.items).includes(i.id));
          const later = i.lastWornAt && i.lastWornAt > look.date ? i.lastWornAt : look.date;
          return { ...i, wornCount: already ? i.wornCount : i.wornCount + 1, lastWornAt: later };
        });
        // 만족도 높은 조합은 다음 추천 가중치 ↑ (기획서 p.18)
        const delta = (look.selfScore - 3) * 0.4 + (look.others === 'PRAISE' ? 0.5 : look.others === 'MEH' ? -0.3 : 0);
        set({ items, looks: [look, ...s.looks.filter((l) => l.date !== look.date)] });
        get().bumpAffinity([...worn], delta);
        if (look.praisedItemId) get().bumpAffinity([look.praisedItemId], 0.5);
      },

      setDraft: (draft) => set({ draft }),

      loadSample: () => {
        const items = seedItems();
        const profile = get().profile;
        set({ items, affinity: {}, ...buildSampleHistory(items, profile) });
      },

      resetAll: () => set({ ...EMPTY }),
    }),
    {
      name: 'wearly-store-v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: ({ draft, ...rest }) => rest,
    },
  ),
);

/** 추천 엔진 입력: 옷장 + 취향 + 최근 7일 코디 */
export function engineInput(ctx: EngineInput['ctx']): EngineInput {
  const s = useStore.getState();
  const recentOutfits: OutfitItems[] = [];
  for (const [date, saved] of Object.entries(s.dayOutfits)) {
    const d = diffDays(ctx.date, date);
    if (d > 0 && d <= 7) recentOutfits.push(saved.outfit.items);
  }
  for (const look of s.looks) {
    const d = diffDays(ctx.date, look.date);
    if (d > 0 && d <= 7) recentOutfits.push(look.items);
  }
  return { items: s.items, profile: s.profile, affinity: s.affinity, ctx, recentOutfits };
}

export function upcomingPlans(plans: PlannedEvent[]): PlannedEvent[] {
  const today = todayKey();
  return plans.filter((p) => p.date >= today);
}

export function recentDates(n: number): string[] {
  const today = todayKey();
  return Array.from({ length: n }, (_, i) => addDays(today, -i));
}
