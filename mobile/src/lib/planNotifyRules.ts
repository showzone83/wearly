// 예정 코디 D-1 알림 규칙. 화면·스토어·expo-notifications 없이 순수 함수만 둔다 (단위 점검 대상).
// - 알림 시각: 예정 전날 설정 시각(기본 20:00, 30분 단위, 기기 시간대)
// - 문구: 서버 푸시(backend notify DayBeforeMessage)와 같은 모양
//   보통 "내일 ‘결혼식 하객’ 예정이에요 · 도쿄 · 예보 18°C", 예보가 바뀌면 "예보가 바뀌었어요" + 저장할 때 기온
// - 예약할 알림 목록과 지금 예약된 알림을 비교해 취소·예약할 것 고르기 (알림 id = plan-d1:<예정 id>)
import type { PlannedEvent, TWeatherSource } from '@/data/types';
import { isRainy, planSource, SOURCE_LABEL } from './planWeather';

/** 알림 id 접두사. 다른 알림과 섞이지 않게 이것으로 시작하는 것만 다룬다 */
export const NOTIFY_ID_PREFIX = 'plan-d1:';
/** 알림 data.type (서버 푸시와 같다) */
export const NOTIFY_TYPE = 'plan-d1';
/** 안드로이드 알림 채널 (서버 푸시의 channelId와 같다) */
export const NOTIFY_CHANNEL = 'plan-d1';
export const DEFAULT_NOTIFY_TIME = '20:00';
/** 시각 고르기 단위(분) */
export const NOTIFY_TIME_STEP = 30;
/**
 * 한 번에 예약해 두는 알림 수. iOS는 앱마다 예약 알림을 64개까지만 두므로 가까운 것부터 이만큼만 예약한다
 * (앱을 열 때마다 다시 맞추므로 뒤의 예정도 차례가 되면 예약된다)
 */
export const MAX_SCHEDULED = 60;

export const notifyId = (planId: string): string => `${NOTIFY_ID_PREFIX}${planId}`;

export const planIdOfNotifyId = (id: string): string | null =>
  id.startsWith(NOTIFY_ID_PREFIX) ? id.slice(NOTIFY_ID_PREFIX.length) : null;

/** "HH:mm"이 맞는 시각인지 (30분 단위) */
export const isNotifyTime = (t: unknown): t is string => {
  if (typeof t !== 'string' || !/^\d{2}:\d{2}$/.test(t)) return false;
  const [h, m] = t.split(':').map(Number);
  return h >= 0 && h < 24 && m >= 0 && m < 60 && m % NOTIFY_TIME_STEP === 0;
};

/** 30분씩 앞뒤로 (00:00 ↔ 23:30에서 돈다) */
export const stepNotifyTime = (t: string, dir: 1 | -1): string => {
  const [h, m] = (isNotifyTime(t) ? t : DEFAULT_NOTIFY_TIME).split(':').map(Number);
  const total = (h * 60 + m + dir * NOTIFY_TIME_STEP + 24 * 60) % (24 * 60);
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
};

/** "20:00" → "오후 8:00", "09:30" → "오전 9:30" */
export const notifyTimeLabel = (t: string): string => {
  const [h, m] = (isNotifyTime(t) ? t : DEFAULT_NOTIFY_TIME).split(':').map(Number);
  const half = h < 12 ? '오전' : '오후';
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${half} ${h12}:${String(m).padStart(2, '0')}`;
};

/** 예정 날짜(YYYY-MM-DD)의 전날 설정 시각 (기기 시간대) */
export const triggerAt = (planDate: string, time: string): Date => {
  const [y, mo, d] = planDate.split('-').map(Number);
  const [h, m] = (isNotifyTime(time) ? time : DEFAULT_NOTIFY_TIME).split(':').map(Number);
  return new Date(y, mo - 1, d - 1, h, m, 0, 0);
};

/** 다시 확인한 최신 날씨 (lib/planNotify.ts가 예정 전날 받아 둔다) */
export interface IFreshWeather {
  temp: number;
  rainProb: number;
  source: TWeatherSource;
}

export interface INotifyContent {
  title: string;
  body: string;
  /** 다시 확인한 날씨가 저장값과 크게 다르다 (기온 3도·비 여부) */
  changed: boolean;
}

const RECHECK_TEMP_DIFF = 3;

/**
 * 알림 문구. fresh(예정 전날 다시 확인한 날씨)가 있으면 그 값과 출처를, 없으면 저장된 날씨와 출처를 쓴다.
 * @param placeLabel 장소 이름. 예정에 장소가 없으면 내 지역 이름
 */
export const notifyContent = (plan: PlannedEvent, placeLabel: string | null, fresh?: IFreshWeather | null): INotifyContent => {
  const savedSource = planSource(plan);
  const savedTemp = Math.round(plan.weather.temp);
  const shown = fresh ?? { temp: plan.weather.temp, rainProb: plan.weather.rainProb, source: savedSource };
  const shownTemp = Math.round(shown.temp);
  const changed =
    !!fresh && (Math.abs(plan.weather.temp - shownTemp) >= RECHECK_TEMP_DIFF || isRainy(plan.weather) !== isRainy(fresh));
  const weather = `${SOURCE_LABEL[shown.source]} ${shownTemp}°C`;
  const place = placeLabel?.trim() || null;
  if (!changed) {
    const parts = [`내일 ‘${plan.title}’ 예정이에요`, ...(place ? [place] : []), weather];
    return { title: '내일 예정 코디', body: parts.join(' · '), changed: false };
  }
  const parts = [`내일 ‘${plan.title}’`, ...(place ? [place] : []), `${weather} (저장할 때 ${savedTemp}°C)`];
  if (isRainy(fresh!) !== isRainy(plan.weather)) parts.push(isRainy(fresh!) ? '비 소식이 있어요' : '비 소식이 없어졌어요');
  parts.push('코디를 다시 확인해 보세요');
  return { title: '예보가 바뀌었어요', body: parts.join(' · '), changed: true };
};

/** 예약할 알림 하나 */
export interface IDesiredNotify {
  id: string;
  planId: string;
  date: string;
  at: Date;
  title: string;
  body: string;
  /** 내용이 바뀌었는지 비교하는 값 (예약된 알림의 data.sig) */
  sig: string;
}

export interface IDesiredOptions {
  /** 지금 (이 시각이 지난 알림은 예약하지 않는다) */
  now: Date;
  /** 알림 시각 "HH:mm" */
  time: string;
  /** 장소가 없는 예정의 장소 이름 (내 지역) */
  homeLabel: string;
  /** 예정 id → 다시 확인한 최신 날씨 */
  fresh?: Record<string, IFreshWeather | undefined>;
}

/** 예약해야 할 D-1 알림 (예정마다 하나, 가까운 순, 최대 MAX_SCHEDULED개). 이미 지난 시각은 뺀다 */
export const desiredNotifications = (plans: PlannedEvent[], opts: IDesiredOptions): IDesiredNotify[] => {
  const out: IDesiredNotify[] = [];
  for (const p of plans) {
    const at = triggerAt(p.date, opts.time);
    if (Number.isNaN(at.getTime()) || at.getTime() <= opts.now.getTime()) continue;
    const content = notifyContent(p, p.place?.label ?? opts.homeLabel, opts.fresh?.[p.id]);
    const sig = [p.date, opts.time, content.title, content.body].join('|');
    out.push({ id: notifyId(p.id), planId: p.id, date: p.date, at, title: content.title, body: content.body, sig });
  }
  return out.sort((a, b) => a.at.getTime() - b.at.getTime()).slice(0, MAX_SCHEDULED);
};

/** 지금 예약돼 있는 우리 알림 (id·sig) */
export interface IScheduledNotify {
  id: string;
  sig: string | null;
}

/**
 * 맞추기: 바라는 목록에 없는 알림(지운 예정·지난 시각·고아)은 취소하고, 없거나 내용·시각이 바뀐 알림은 다시 예약한다.
 * 우리 접두사(plan-d1:)로 시작하지 않는 알림은 건드리지 않는다.
 */
export const diffNotifications = (
  desired: IDesiredNotify[],
  scheduled: IScheduledNotify[],
): { cancel: string[]; schedule: IDesiredNotify[] } => {
  const want = new Map(desired.map((d) => [d.id, d]));
  const have = new Map(scheduled.filter((s) => s.id.startsWith(NOTIFY_ID_PREFIX)).map((s) => [s.id, s]));
  const cancel: string[] = [];
  const schedule: IDesiredNotify[] = [];
  for (const [id, s] of have) {
    const d = want.get(id);
    if (!d || d.sig !== s.sig) cancel.push(id);
  }
  for (const [id, d] of want) {
    const s = have.get(id);
    if (!s || s.sig !== d.sig) schedule.push(d);
  }
  return { cancel, schedule };
};

/** 알림 data에서 열 예정 찾기: 기기 예정 id(planId) → 서버 id(serverId) 순 */
export const findNotifiedPlan = (
  plans: PlannedEvent[],
  data: Record<string, unknown> | null | undefined,
): PlannedEvent | undefined => {
  if (!data || data.type !== NOTIFY_TYPE) return undefined;
  const planId = typeof data.planId === 'string' ? data.planId : null;
  const serverId = typeof data.serverId === 'string' ? data.serverId : null;
  return (planId ? plans.find((p) => p.id === planId) : undefined) ?? (serverId ? plans.find((p) => p.serverId === serverId) : undefined);
};
