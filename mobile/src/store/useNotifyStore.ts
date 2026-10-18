// 예정 D-1 알림 기기 설정 (lib/planNotify.ts). 켜기·끄기는 프로필(profile.notify.dMinus1, 계정 모드는 서버 동기화)에 있고,
// 여기에는 기기에만 두는 값(알림 시각·권한 요청 여부·기기 ID·서버 푸시 등록 결과·다시 확인한 날씨)을 둔다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import { DEFAULT_NOTIFY_TIME, isNotifyTime, type IFreshWeather } from '@/lib/planNotifyRules';

/** unknown = 아직 묻지 않음, granted = 허용, denied = 거부(다시 묻지 않고 설정 화면에 안내) */
export type TNotifyPermission = 'unknown' | 'granted' | 'denied';

/** 서버 푸시 등록 결과 (계정·기기별). serverPush면 앱은 로컬 D-1 알림을 끈다 */
export interface IServerPush {
  userId: string;
  token: string;
  serverPush: boolean;
  /** 서버 D-1 작업 시각 "HH:mm" (한국 시간) */
  dayBeforeTime: string;
  /** 등록한 시각 (ISO). 하루가 지나면 다시 등록한다 */
  at: string;
}

/** 예정 전날 다시 확인한 최신 날씨 (알림 문구용, 예정 id별) */
export interface IFreshEntry extends IFreshWeather {
  /** 확인한 날 (로컬 YYYY-MM-DD). 같은 예정은 하루 한 번 */
  checkedOn: string;
  /** 확인한 예정 날짜 (날짜가 바뀌면 버린다) */
  date: string;
}

interface INotifyState {
  /** 알림 시각 "HH:mm" (30분 단위, 기기 시간대) */
  time: string;
  permission: TNotifyPermission;
  /** 이 기기의 ID (푸시 토큰 등록에 쓴다). 처음 필요할 때 만든다 */
  deviceId: string | null;
  serverPush: IServerPush | null;
  fresh: Record<string, IFreshEntry>;
  setTime: (time: string) => void;
  setPermission: (p: TNotifyPermission) => void;
  ensureDeviceId: () => string;
  setServerPush: (v: IServerPush | null) => void;
  setFresh: (planId: string, entry: IFreshEntry | null) => void;
  /** 없어진 예정의 날씨 기록을 버린다 */
  pruneFresh: (keepIds: Set<string>) => void;
}

const makeDeviceId = (): string =>
  `dev-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}${Math.random().toString(36).slice(2, 10)}`;

export const useNotifyStore = create<INotifyState>()(
  persist(
    (set, get) => ({
      time: DEFAULT_NOTIFY_TIME,
      permission: 'unknown',
      deviceId: null,
      serverPush: null,
      fresh: {},
      setTime: (time) => {
        if (isNotifyTime(time)) set({ time });
      },
      setPermission: (permission) => set({ permission }),
      ensureDeviceId: () => {
        const current = get().deviceId;
        if (current) return current;
        const id = makeDeviceId();
        set({ deviceId: id });
        return id;
      },
      setServerPush: (serverPush) => set({ serverPush }),
      setFresh: (planId, entry) => {
        const fresh = { ...get().fresh };
        if (entry) fresh[planId] = entry;
        else delete fresh[planId];
        set({ fresh });
      },
      pruneFresh: (keepIds) => {
        const fresh = get().fresh;
        const stale = Object.keys(fresh).filter((id) => !keepIds.has(id));
        if (!stale.length) return;
        const next = { ...fresh };
        for (const id of stale) delete next[id];
        set({ fresh: next });
      },
    }),
    {
      name: 'wearly-notify-v1',
      storage: createJSONStorage(() => AsyncStorage),
    },
  ),
);
