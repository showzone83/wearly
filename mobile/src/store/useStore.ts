import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import { DEFAULT_PROFILE, seedItems } from '@/data/seed';
import type {
  DailyLook,
  FeedbackType,
  IPlanTombstone,
  Outfit,
  OutfitItems,
  PlannedEvent,
  Profile,
  SavedOutfit,
  WardrobeItem,
} from '@/data/types';
import type { RecommendSource } from '@/lib/aiOutfits';
import { addDays, diffDays, todayKey } from '@/lib/date';
import type { EngineInput } from '@/lib/recommend';
import type { IPhotoSyncEntry } from '@/lib/photoRules';
import { buildSampleHistory } from '@/lib/sample';

/** 추천 결과 화면으로 넘기는 임시 데이터 (저장하지 않음) */
export interface Draft {
  mode: 'TODAY' | 'PLAN';
  outfits: Outfit[];
  plan?: Omit<PlannedEvent, 'id' | 'outfit'>;
  /** 이미 있는 예정을 다시 추천할 때 그 id (저장하면 그 예정을 바꾼다) */
  planId?: string;
  /** 추천 방식(AI·규칙)과 AI 공급자·모델·남은 횟수. 없으면 규칙 추천 */
  source?: RecommendSource;
}

export type AuthMode = 'guest' | 'account';

/** 로그인이 풀린 이유: expired = 만료·갱신 실패, elsewhere = 다른 곳에서 로그아웃됨(401 TOKEN_INVALID, doc/16 6장) */
export type AuthLostReason = 'expired' | 'elsewhere';

/** 로그인한 계정 정보 (토큰은 여기 두지 않고 Keychain/Keystore에 둔다) */
export interface AccountUser {
  id: string;
  email: string | null;
  name: string;
}

interface State {
  /** guest = 로그인 없이 체험(기기 저장만), account = 서버 계정 */
  authMode: AuthMode;
  /** account 모드의 마지막 로그인 사용자 */
  user: AccountUser | null;
  /** 토큰 갱신 실패 등으로 로그인이 풀린 상태. 로그인 화면으로 보낸다 */
  authLost: boolean;
  /** authLost일 때 로그인 화면 안내 문구를 고르는 이유 */
  authLostReason: AuthLostReason | null;
  /**
   * 계정 모드: 서버 아이템 id → 기기에만 있는 사진 uri.
   * 샘플 사진, 서버에 사진 기능이 없을 때 고른 사진, 아직 서버로 옮기지 못한 사진이다 (서버 사진은 item.image)
   */
  photos: Record<string, string>;
  /** 계정 모드: 기기 사진을 서버로 옮기기 실패 기록 (아이템 id별, 계약 4장) */
  photoSync: Record<string, IPhotoSyncEntry>;
  onboarded: boolean;
  profile: Profile;
  items: WardrobeItem[];
  dayOutfits: Record<string, SavedOutfit>;
  plans: PlannedEvent[];
  /** 계정 모드: 기기에서 지웠지만 아직 서버에서 지우지 못한 예정 (lib/planSync.ts). 예전 데이터에는 없다 */
  planDeletes: IPlanTombstone[];
  looks: DailyLook[];
  affinity: Record<string, number>;
  draft: Draft | null;

  /** items를 주면(계정 모드: 서버가 돌려준 아이템) 그것으로 옷장을 채운다 */
  completeOnboarding: (profile: Profile, withSample: boolean, items?: WardrobeItem[]) => void;
  /** 서버 목록으로 옷장을 맞춘다. 사라진 아이템은 저장 코디·예정일에서도 뺀다 */
  syncItems: (items: WardrobeItem[]) => void;
  setPhoto: (id: string, uri: string | undefined) => void;
  setPhotoSync: (id: string, entry: IPhotoSyncEntry | undefined) => void;
  updateProfile: (patch: Partial<Profile>) => void;
  addItem: (item: WardrobeItem) => void;
  updateItem: (id: string, patch: Partial<WardrobeItem>) => void;
  deleteItem: (id: string) => void;
  saveDayOutfit: (date: string, outfit: Outfit) => void;
  setFeedback: (date: string, type: FeedbackType, reasons?: string[]) => void;
  bumpAffinity: (ids: (string | undefined)[], delta: number) => void;
  /** 예정 저장(같은 id면 바꿈). 서버 id는 이어받고, 서버에 보낼 변경으로 표시한다. 서버 전송은 lib/planSync.ts savePlan */
  addPlan: (plan: PlannedEvent) => void;
  deletePlan: (id: string) => void;
  /** 예정의 날씨 칸만 바꾼다 (다시 확인, doc/19). 그사이 지워진 예정이면 아무것도 안 한다 */
  updatePlanWeather: (id: string, patch: Pick<PlannedEvent, 'weather' | 'weatherSource' | 'weatherCheckedAt' | 'forecastReady'>) => void;
  addLook: (look: DailyLook) => void;
  setDraft: (draft: Draft | null) => void;
  loadSample: () => void;
  resetAll: () => void;
}

const EMPTY = {
  authMode: 'guest' as AuthMode,
  user: null as AccountUser | null,
  authLost: false,
  authLostReason: null as AuthLostReason | null,
  photos: {} as Record<string, string>,
  photoSync: {} as Record<string, IPhotoSyncEntry>,
  onboarded: false,
  profile: DEFAULT_PROFILE,
  items: [] as WardrobeItem[],
  dayOutfits: {} as Record<string, SavedOutfit>,
  plans: [] as PlannedEvent[],
  planDeletes: [] as IPlanTombstone[],
  looks: [] as DailyLook[],
  affinity: {} as Record<string, number>,
  draft: null as Draft | null,
};

/** 계정 모드는 옷을 지워도 예정 코디에서 빼지 않는다: 서버가 예정을 그대로 두고 missingItemIds로 알려 주므로 "지운 옷"으로 보여 준다 */
const keepPlanRefs = (s: { authMode: AuthMode }): boolean => s.authMode === 'account';

/** 기기에서 예정을 고쳤다는 표시 (서버 동기화 대상) */
const touched = (): Pick<PlannedEvent, 'updatedAt' | 'pending' | 'syncBlocked'> => ({
  updatedAt: new Date().toISOString(),
  pending: true,
  syncBlocked: undefined,
});

function removeItemRefs(items: OutfitItems, id: string): OutfitItems {
  const next = { ...items };
  for (const k of Object.keys(next) as (keyof OutfitItems)[]) if (next[k] === id) delete next[k];
  return next;
}

export const useStore = create<State>()(
  persist(
    (set, get) => ({
      ...EMPTY,

      completeOnboarding: (profile, withSample, serverItems) => {
        const items = serverItems ?? (withSample ? seedItems() : get().items);
        if (withSample) {
          set({ onboarded: true, profile, items, ...buildSampleHistory(items, profile) });
        } else {
          set({ onboarded: true, profile, items });
        }
      },

      syncItems: (items) => {
        const s = get();
        const keep = new Set(items.map((i) => i.id));
        const removed = s.items.filter((i) => !keep.has(i.id)).map((i) => i.id);
        let { dayOutfits, plans } = s;
        for (const id of removed) {
          const next: Record<string, SavedOutfit> = {};
          for (const [k, v] of Object.entries(dayOutfits)) {
            next[k] = { ...v, outfit: { ...v.outfit, items: removeItemRefs(v.outfit.items, id) } };
          }
          dayOutfits = next;
          if (keepPlanRefs(s)) continue;
          plans = plans.map((p) => (p.outfit ? { ...p, outfit: { ...p.outfit, items: removeItemRefs(p.outfit.items, id) } } : p));
        }
        const photos = { ...s.photos };
        const photoSync = { ...s.photoSync };
        for (const id of removed) {
          delete photos[id];
          delete photoSync[id];
        }
        set({ items, dayOutfits, plans, photos, photoSync });
      },

      setPhoto: (id, uri) => {
        const photos = { ...get().photos };
        if (uri) photos[id] = uri;
        else delete photos[id];
        set({ photos });
      },

      setPhotoSync: (id, entry) => {
        const photoSync = { ...get().photoSync };
        if (entry) photoSync[id] = entry;
        else delete photoSync[id];
        set({ photoSync });
      },

      updateProfile: (patch) => set({ profile: { ...get().profile, ...patch } }),

      addItem: (item) => set({ items: [item, ...get().items] }),

      updateItem: (id, patch) =>
        set({ items: get().items.map((i) => (i.id === id ? { ...i, ...patch } : i)) }),

      deleteItem: (id) => {
        const s = get();
        const photos = { ...s.photos };
        delete photos[id];
        const photoSync = { ...s.photoSync };
        delete photoSync[id];
        const dayOutfits: Record<string, SavedOutfit> = {};
        for (const [k, v] of Object.entries(s.dayOutfits)) {
          dayOutfits[k] = { ...v, outfit: { ...v.outfit, items: removeItemRefs(v.outfit.items, id) } };
        }
        set({
          items: s.items.filter((i) => i.id !== id),
          photos,
          photoSync,
          dayOutfits,
          plans: keepPlanRefs(s)
            ? s.plans
            : s.plans.map((p) =>
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

      addPlan: (plan) => {
        const prev = get().plans.find((p) => p.id === plan.id);
        // 새 코디는 지금 옷장으로 고른 것이라 예전 missingItemIds는 버린다 (서버가 다시 알려 준다)
        const next: PlannedEvent = { ...plan, serverId: plan.serverId ?? prev?.serverId, missingItemIds: undefined, ...touched() };
        set({ plans: [...get().plans.filter((p) => p.id !== plan.id), next].sort((a, b) => a.date.localeCompare(b.date)) });
      },

      deletePlan: (id) => set({ plans: get().plans.filter((p) => p.id !== id) }),

      updatePlanWeather: (id, patch) => {
        const plans = get().plans;
        if (!plans.some((p) => p.id === id)) return;
        set({ plans: plans.map((p) => (p.id === id ? { ...p, ...patch, ...touched() } : p)) });
      },

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
