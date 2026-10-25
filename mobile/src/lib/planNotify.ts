// 예정 코디 D-1 알림 (기획서: 예정 코디 → D-1 알림 → 최신 예보로 재확인·수정 제안).
// A. 기기 로컬 알림 (Expo Go·목업 실행에서도 동작): 예정마다 전날 설정 시각(기본 20:00)에 알림 하나 (id = plan-d1:<예정 id>).
//    예정이 바뀌면(저장·수정·동기화·다시 확인·지우기) 예약 목록을 예정 목록과 맞춘다 (고아 알림 취소, 지난 시각은 예약하지 않음).
//    예정 전날에는 그 장소 날씨를 한 번 다시 받아 알림 문구에 최신 값을 쓴다 (못 받으면 저장된 날씨, 출처 표시).
// B. 서버 푸시 (개발용 빌드에서): Expo 푸시 토큰을 받으면 서버에 등록한다. 서버가 serverPush=true로 답하면 서버가
//    D-1 알림을 보내므로 로컬 D-1 알림은 모두 취소한다. 토큰을 못 받으면(Expo Go·projectId 없음·권한 없음) 조용히 로컬 알림만 쓴다.
// 규칙(시각·문구·맞추기)은 lib/planNotifyRules.ts의 순수 함수에 있다.
import { Linking, Platform } from 'react-native';

import { isApiError } from '@/api/errors';
import type { PlannedEvent } from '@/data/types';
import { isRunningInExpoGo } from 'expo';
import Constants from 'expo-constants';
import type * as NotificationsModule from 'expo-notifications';
import { router } from 'expo-router';

import { useNotifyStore, type IFreshEntry } from '@/store/useNotifyStore';
import { useStore } from '@/store/useStore';
import { api } from './api';
import { addDays, todayKey } from './date';
import { MOCK_MODE } from './mode';
import { fetchDayWeather, myPlace } from './placeWeather';
import {
  desiredNotifications,
  diffNotifications,
  findNotifiedPlan,
  NOTIFY_CHANNEL,
  NOTIFY_ID_PREFIX,
  NOTIFY_TYPE,
  triggerAt,
  type IFreshWeather,
  type IScheduledNotify,
} from './planNotifyRules';
import { HELPER_CHANNEL, helperNoticeRoute } from './pairingRules';
import { planSource, weatherFromDay } from './planWeather';

/** 서버 푸시 등록을 다시 하는 간격 (토큰이 바뀌었을 수 있다) */
const REREGISTER_MS = 24 * 60 * 60_000;
/** 예정이 여러 번 바뀔 때 한 번만 맞추도록 기다리는 시간 */
const RECONCILE_DELAY_MS = 400;

const log = (...args: unknown[]) => {
  if (__DEV__) console.log('[plan-notify]', ...args);
};

// expo-notifications는 정적으로 불러오지 않는다: 일부 Expo Go(특히 Android)에는 이 패키지가 쓰는 네이티브 모듈
// (ExpoTopicSubscriptionModule 등)이 없어, 불러오는 순간 오류가 나고 이 파일을 쓰는 _layout까지 통째로 실패한다.
// 처음 필요할 때 한 번만 불러오고, 실패하면 알림 기능만 끄고 앱은 계속 쓴다.
let Notifications: typeof NotificationsModule;
let notificationsLoad: 'unknown' | 'ok' | 'missing' = 'unknown';

const loadNotifications = (): boolean => {
  if (notificationsLoad !== 'unknown') return notificationsLoad === 'ok';
  // Android Expo Go에는 이 패키지가 쓰는 네이티브 모듈이 없다. 불러오려고만 해도 개발 모드에서 빨간 오류가 뜨므로 아예 시도하지 않는다
  if (Platform.OS === 'android' && isRunningInExpoGo()) {
    notificationsLoad = 'missing';
    console.warn('[plan-notify] Android Expo Go에서는 알림 모듈이 없어 D-1 알림을 꺼요 (개발용 빌드에서는 동작)');
    return false;
  }
  try {
    const mod = require('expo-notifications') as typeof NotificationsModule | undefined;
    // 개발 모드에서는 모듈 초기화가 실패해도 throw 대신 오류만 띄우고 undefined를 돌려줄 수 있다 → 실제로 쓸 수 있는지 확인한다
    if (mod && typeof mod.setNotificationHandler === 'function' && typeof mod.scheduleNotificationAsync === 'function') {
      Notifications = mod;
      notificationsLoad = 'ok';
    } else {
      notificationsLoad = 'missing';
      console.warn('[plan-notify] 알림 모듈을 쓸 수 없어 D-1 알림을 꺼요');
    }
  } catch (e) {
    notificationsLoad = 'missing';
    console.warn('[plan-notify] 이 실행에서는 알림을 쓸 수 없어 D-1 알림을 꺼요', (e as Error)?.message);
  }
  return notificationsLoad === 'ok';
};

/** 이 실행에서 로컬 알림을 쓸 수 있는지 (웹 미리보기, 알림 네이티브 모듈이 없는 Expo Go는 안 된다) */
export const notificationsSupported = (): boolean => Platform.OS !== 'web' && loadNotifications();

/** 원격 푸시 토큰을 받을 수 있는 실행인지: 서버 실행 + 개발용·정식 빌드 (Expo Go는 SDK 53부터 원격 푸시가 없다) */
const remotePushPossible = (): boolean => notificationsSupported() && !MOCK_MODE && !isRunningInExpoGo();

const easProjectId = (): string | undefined => {
  const extra = Constants.expoConfig?.extra as { eas?: { projectId?: string } } | undefined;
  return extra?.eas?.projectId ?? Constants.easConfig?.projectId ?? undefined;
};

/** 지금 로그인한 계정에 서버 푸시가 켜져 있는지 (true면 로컬 D-1 알림을 쓰지 않는다) */
export const serverPushActive = (): boolean => {
  const sp = useNotifyStore.getState().serverPush;
  const user = useStore.getState().user;
  return !!sp && sp.serverPush && useStore.getState().authMode === 'account' && sp.userId === user?.id;
};

// ── 권한 ──

let channelReady: Promise<void> | null = null;

/** 안드로이드 알림 채널 (서버 푸시의 channelId와 같다). 안드로이드 13+는 권한을 묻기 전에 채널이 있어야 한다 */
const ensureChannel = (): Promise<void> => {
  if (Platform.OS !== 'android') return Promise.resolve();
  if (!channelReady) {
    channelReady = Promise.all([
      Notifications.setNotificationChannelAsync(NOTIFY_CHANNEL, {
        name: '예정 전날 알림',
        description: '예정 코디 전날 저녁에 날씨와 함께 알려 드려요',
        importance: Notifications.AndroidImportance.DEFAULT,
      }),
      // 옷장 도우미 알림 (서버 푸시의 channelId와 같다, doc/26 3단계)
      Notifications.setNotificationChannelAsync(HELPER_CHANNEL, {
        name: '옷장 도우미 알림',
        description: '옷장 도우미 연결 요청과 대신 정리한 옷을 알려 드려요',
        importance: Notifications.AndroidImportance.DEFAULT,
      }),
    ])
      .then(() => undefined)
      .catch((e) => {
        channelReady = null;
        log('알림 채널을 만들지 못했어요', (e as Error)?.message);
      });
  }
  return channelReady;
};

/**
 * 알림 권한. 이미 허용이면 granted. ask면 아직 묻지 않았을 때만 한 번 묻는다 (거부하면 다시 묻지 않고 설정 화면 안내).
 * 기기 설정에서 나중에 허용·거부를 바꿀 수 있으므로 매번 실제 상태를 읽는다.
 */
export const ensureNotifyPermission = async (ask: boolean): Promise<'granted' | 'denied' | 'unknown'> => {
  if (!notificationsSupported()) return 'denied';
  const store = useNotifyStore.getState();
  try {
    await ensureChannel();
    const current = await Notifications.getPermissionsAsync();
    if (current.granted || current.ios?.status === Notifications.IosAuthorizationStatus.PROVISIONAL) {
      store.setPermission('granted');
      return 'granted';
    }
    if (!ask || store.permission === 'denied' || !current.canAskAgain) {
      // 기기 설정에서 끈 경우: 다시 물을 수 없으면 거부, 물을 수 있으면 아직 묻지 않은 것으로 되돌린다
      if (!current.canAskAgain) store.setPermission('denied');
      else if (store.permission === 'granted') store.setPermission('unknown');
      return useNotifyStore.getState().permission === 'denied' ? 'denied' : 'unknown';
    }
    const res = await Notifications.requestPermissionsAsync({
      ios: { allowAlert: true, allowBadge: false, allowSound: true },
    });
    const granted = res.granted || res.ios?.status === Notifications.IosAuthorizationStatus.PROVISIONAL;
    store.setPermission(granted ? 'granted' : 'denied');
    return granted ? 'granted' : 'denied';
  } catch (e) {
    log('알림 권한을 확인하지 못했어요', (e as Error)?.message);
    return 'unknown';
  }
};

/** 처음 예정을 저장할 때 한 번 묻는다 (D-1 알림이 켜져 있을 때만). 그 뒤 맞추기·토큰 등록 */
export const askNotifyPermissionOnce = async (): Promise<void> => {
  if (!notificationsSupported() || !useStore.getState().profile.notify.dMinus1) return;
  if (useNotifyStore.getState().permission !== 'unknown') return;
  const p = await ensureNotifyPermission(true);
  if (p === 'granted') {
    void syncPushToken();
    scheduleReconcile();
  }
};

/** 기기 설정 앱의 이 앱 화면 (알림을 거부했을 때 안내) */
export const openNotifySettings = (): void => {
  void Linking.openSettings().catch(() => undefined);
};

// ── 예정 전날 날씨 다시 확인 ──

/** 예정 전날(알림 시각 전)이면 그 장소 날씨를 하루 한 번 다시 받아 둔다. 직접 고른 날씨(MANUAL)는 그대로 */
const refreshTomorrowWeather = async (): Promise<void> => {
  const today = todayKey();
  const tomorrow = addDays(today, 1);
  const { time, fresh, setFresh } = useNotifyStore.getState();
  const now = Date.now();
  const targets = useStore
    .getState()
    .plans.filter(
      (p) =>
        p.date === tomorrow &&
        planSource(p) !== 'MANUAL' &&
        triggerAt(p.date, time).getTime() > now &&
        !(fresh[p.id]?.checkedOn === today && fresh[p.id]?.date === p.date),
    );
  for (const plan of targets) {
    const out = await fetchDayWeather(plan.place ?? myPlace(), plan.date);
    if (out.kind === 'unsupported') return; // 목업 실행·예전 서버
    if (out.kind !== 'ok') continue; // 로그인 필요·한도·네트워크: 저장된 날씨로 알린다
    const w = weatherFromDay(out.res);
    const entry: IFreshEntry = { temp: w.temp, rainProb: w.rainProb, source: out.res.source, checkedOn: today, date: plan.date };
    setFresh(plan.id, entry);
  }
};

// ── 로컬 알림 맞추기 ──

const scheduledOurs = async (): Promise<IScheduledNotify[]> => {
  const all = await Notifications.getAllScheduledNotificationsAsync();
  return all
    .filter((n) => n.identifier.startsWith(NOTIFY_ID_PREFIX))
    .map((n) => {
      const sig = (n.content.data as Record<string, unknown> | null)?.sig;
      return { id: n.identifier, sig: typeof sig === 'string' ? sig : null };
    });
};

const freshFor = (plans: PlannedEvent[]): Record<string, IFreshWeather | undefined> => {
  const fresh = useNotifyStore.getState().fresh;
  const out: Record<string, IFreshWeather | undefined> = {};
  for (const p of plans) {
    const f = fresh[p.id];
    if (f && f.date === p.date) out[p.id] = { temp: f.temp, rainProb: f.rainProb, source: f.source };
  }
  return out;
};

const doReconcile = async (): Promise<void> => {
  if (!notificationsSupported()) return;
  const s = useStore.getState();
  const n = useNotifyStore.getState();
  n.pruneFresh(new Set(s.plans.map((p) => p.id)));
  const scheduled = await scheduledOurs();
  // 알림을 껐거나, 서버가 대신 보내거나, 권한이 없으면 우리 알림을 모두 취소한다
  let enabled = s.onboarded && s.profile.notify.dMinus1 && !serverPushActive();
  if (enabled && (await ensureNotifyPermission(false)) !== 'granted') enabled = false;
  if (enabled) await refreshTomorrowWeather();
  const plans = useStore.getState().plans;
  const desired = enabled
    ? desiredNotifications(plans, {
        now: new Date(),
        time: useNotifyStore.getState().time,
        homeLabel: myPlace().label,
        fresh: freshFor(plans),
      })
    : [];
  const { cancel, schedule } = diffNotifications(desired, scheduled);
  for (const id of cancel) await Notifications.cancelScheduledNotificationAsync(id);
  for (const d of schedule) {
    await Notifications.scheduleNotificationAsync({
      identifier: d.id,
      content: {
        title: d.title,
        body: d.body,
        sound: 'default',
        data: { type: NOTIFY_TYPE, planId: d.planId, date: d.date, sig: d.sig },
      },
      trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date: d.at, channelId: NOTIFY_CHANNEL },
    });
  }
  if (cancel.length || schedule.length) log(`맞춤: 예약 ${schedule.length}개 · 취소 ${cancel.length}개 · 전체 ${desired.length}개`);
};

let reconciling: Promise<void> | null = null;
let again = false;
let timer: ReturnType<typeof setTimeout> | null = null;

/** 예약 목록을 예정 목록과 맞춘다 (동시에 여러 번 불러도 하나씩, 도는 중이면 끝난 뒤 한 번 더). throw하지 않는다 */
export const reconcilePlanNotifications = (): Promise<void> => {
  if (!notificationsSupported()) return Promise.resolve();
  if (reconciling) {
    again = true;
    return reconciling;
  }
  reconciling = (async () => {
    do {
      again = false;
      try {
        await doReconcile();
      } catch (e) {
        console.warn('[plan-notify] 알림을 예약하지 못했어요', e);
      }
    } while (again);
  })().finally(() => {
    reconciling = null;
  });
  return reconciling;
};

/** 조금 기다렸다 맞춘다 (예정이 한꺼번에 바뀔 때) */
export const scheduleReconcile = (): void => {
  if (!notificationsSupported()) return;
  if (timer) clearTimeout(timer);
  timer = setTimeout(() => {
    timer = null;
    void reconcilePlanNotifications();
  }, RECONCILE_DELAY_MS);
};

// ── 서버 푸시 토큰 ──

let pushing: Promise<void> | null = null;

/**
 * 원격 푸시 토큰을 받아 서버에 등록한다 (계정 모드, 개발용·정식 빌드, 권한 허용, EAS projectId 있음).
 * 하나라도 안 되면 조용히 건너뛰고 로컬 알림만 쓴다. 서버 응답의 serverPush로 로컬 알림을 끌지 정한다.
 */
export const syncPushToken = (opts: { force?: boolean } = {}): Promise<void> => {
  if (!pushing) {
    pushing = doSyncPushToken(!!opts.force)
      .catch((e) => log('푸시 토큰을 등록하지 못해 로컬 알림만 써요', (e as Error)?.message))
      .finally(() => {
        pushing = null;
      });
  }
  return pushing;
};

const doSyncPushToken = async (force: boolean): Promise<void> => {
  const s = useStore.getState();
  const n = useNotifyStore.getState();
  const user = s.user;
  if (!remotePushPossible() || s.authMode !== 'account' || s.authLost || !user) return;
  const projectId = easProjectId();
  if (!projectId) return; // EAS 프로젝트가 없으면 Expo 푸시 토큰을 받을 수 없다
  if ((await ensureNotifyPermission(false)) !== 'granted') return;
  const recent = n.serverPush;
  if (!force && recent && recent.userId === user.id && Date.now() - new Date(recent.at).getTime() < REREGISTER_MS) return;
  const token = (await Notifications.getExpoPushTokenAsync({ projectId })).data;
  try {
    const res = await api.push.register({
      token,
      platform: Platform.OS === 'ios' ? 'ios' : 'android',
      deviceId: n.ensureDeviceId(),
    });
    if (useStore.getState().user?.id !== user.id) return;
    n.setServerPush({
      userId: user.id,
      token,
      serverPush: res.serverPush,
      dayBeforeTime: res.dayBeforeTime,
      at: new Date().toISOString(),
    });
    log(res.serverPush ? '서버가 D-1 알림을 보내요 (로컬 알림 끔)' : '서버 푸시가 꺼져 있어 로컬 알림을 써요');
  } catch (e) {
    // 예전 서버(404)·오프라인: 로컬 알림을 쓴다
    if (isApiError(e) && e.status === 404) n.setServerPush(null);
    throw e;
  }
  scheduleReconcile();
};

/**
 * 로그아웃 전에: 서버에서 이 기기의 토큰을 지운다 (실패해도 로그아웃은 계속).
 * @param server false면 기기 기록만 지운다 (탈퇴: 서버가 토큰을 함께 지운다)
 */
export const unregisterPushToken = async (server = true): Promise<void> => {
  const n = useNotifyStore.getState();
  const had = n.serverPush;
  n.setServerPush(null);
  if (!server || !had || !n.deviceId || MOCK_MODE) return;
  try {
    await api.push.remove(n.deviceId);
  } catch (e) {
    log('서버에서 푸시 토큰을 지우지 못했어요', (e as Error)?.message);
  }
};

// ── 시작 · 알림 누르기 ──

/** 알림을 누르면 그 예정의 PLAN 모드를 연다 (로컬 알림·서버 푸시 같은 data) */
const openFromResponse = (response: NotificationsModule.NotificationResponse | null | undefined): boolean => {
  if (!response || response.actionIdentifier !== Notifications.DEFAULT_ACTION_IDENTIFIER) return false;
  const data = response.notification.request.content.data as Record<string, unknown> | null;
  // 옷장 도우미 알림(연결 요청·승인·해제·대리 작업)은 도우미 화면을 연다 (doc/26)
  const helperRoute = helperNoticeRoute(data);
  if (helperRoute) {
    router.push(helperRoute as never);
    return true;
  }
  if (data?.type !== NOTIFY_TYPE) return false;
  const plan = findNotifiedPlan(useStore.getState().plans, data);
  if (plan) {
    router.push(`/coordi?mode=plan&planId=${encodeURIComponent(plan.id)}&t=${Date.now()}`);
  } else {
    router.push('/calendar'); // 아직 동기화 전이거나 지운 예정
  }
  return true;
};

let started = false;

/**
 * 앱 시작(화면 준비 뒤) 한 번: 알림 표시 방식·누르기 처리·예정 변경 구독. 해제 함수를 돌려준다.
 * 앱을 알림으로 연 경우(종료 상태)도 마지막 응답으로 그 예정을 연다.
 */
export const startPlanNotifications = (): (() => void) => {
  if (!notificationsSupported() || started) return () => undefined;
  started = true;
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: false,
      shouldSetBadge: false,
    }),
  });
  void ensureChannel();

  const sub = Notifications.addNotificationResponseReceivedListener((r) => {
    if (openFromResponse(r)) void Notifications.clearLastNotificationResponseAsync().catch(() => undefined);
  });
  void Notifications.getLastNotificationResponseAsync()
    .then((r) => {
      if (openFromResponse(r)) return Notifications.clearLastNotificationResponseAsync();
    })
    .catch(() => undefined);

  // 예정·알림 설정·계정이 바뀌면 맞춘다 (저장·수정·동기화·다시 확인·지우기·로그아웃 모두 여기로 온다)
  const unsubStore = useStore.subscribe((s, prev) => {
    if (s.plans !== prev.plans || s.profile.notify.dMinus1 !== prev.profile.notify.dMinus1 || s.onboarded !== prev.onboarded) {
      scheduleReconcile();
    }
    if (s.user?.id !== prev.user?.id || s.authMode !== prev.authMode) {
      if (s.authMode === 'account' && s.user) void syncPushToken({ force: true });
      scheduleReconcile();
    }
  });
  const unsubNotify = useNotifyStore.subscribe((s, prev) => {
    if (s.time !== prev.time || s.serverPush !== prev.serverPush) scheduleReconcile();
  });

  void syncPushToken();
  scheduleReconcile();
  return () => {
    started = false;
    sub.remove();
    unsubStore();
    unsubNotify();
  };
};

/** 앱이 다시 활성이 될 때: 날짜가 바뀌었을 수 있고(전날 날씨 다시 확인) 기기 설정에서 권한을 바꿨을 수 있다 */
export const onNotifyAppActive = (): void => {
  void syncPushToken();
  scheduleReconcile();
};
