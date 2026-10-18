// 예정 코디 서버 동기화의 순수 규칙 (React Native·Expo·스토어를 쓰지 않는다. 단위 점검을 Node에서 바로 돌린다).
// 흐름(서버 호출·스토어 반영)은 src/lib/planSync.ts.
// - 기기 예정 ↔ 서버 요청·응답 변환 (기기 id = 서버 clientId)
// - 합치기: 서버만 있는 예정은 추가, 서버에서 없어진 예정(서버 id가 있었음)은 지움, 양쪽에 있으면 최신(updatedAt)을 쓴다
// - bulk 100개씩 나누기, 오류 종류 가르기
import { isApiError } from '@/api/errors';
import type { PlanDto, PlanInput, PlanPatch } from '@/api/types';
import type { IPlanTombstone, Outfit, PlannedEvent } from '@/data/types';

/** POST /plans/bulk 한 번에 올리는 수 (서버 상한) */
export const PLAN_BULK_SIZE = 100;

/** 서버가 받는 날짜 범위: 오늘 앞뒤 1년 (서버는 한국 날짜 기준이라 하루 여유를 둔다) */
const RANGE_YEARS = 1;

// ── 변환 ──

/** 기기 예정 → POST /plans·bulk 항목. clientId는 기기 id. 없는 값은 null로 보낸다 */
export const toPlanInput = (p: PlannedEvent): PlanInput => ({
  clientId: p.id,
  ...toPlanPatch(p),
});

/** 기기 예정 → PATCH 본문 (모든 필드를 보낸다. 비어 있는 memo·place·outfit·출처·확인 시각은 null로 비운다) */
export const toPlanPatch = (p: PlannedEvent): Required<PlanPatch> => ({
  date: p.date,
  timeOfDay: p.timeOfDay,
  title: p.title,
  occasion: p.occasion,
  concepts: p.concepts ?? [],
  memo: p.memo?.trim() ? p.memo : null,
  place: p.place ? { regionCode: p.place.regionCode ?? null, label: p.place.label, lat: p.place.lat, lon: p.place.lon } : null,
  weather: {
    temp: p.weather.temp,
    min: p.weather.min,
    max: p.weather.max,
    rainProb: p.weather.rainProb,
    cond: p.weather.cond,
    manual: !!p.weather.manual,
  },
  weatherSource: p.weatherSource ?? null,
  weatherCheckedAt: p.weatherCheckedAt ?? null,
  forecastReady: !!p.forecastReady,
  outfit: p.outfit ? (p.outfit as unknown as Required<PlanPatch>['outfit']) : null,
});

/** 서버 예정 → 기기 예정 (보내지 못한 변경 없음). id는 기기 id (보통 clientId) */
export const fromPlanDto = (dto: PlanDto, id: string): PlannedEvent => {
  const w = dto.weather;
  const plan: PlannedEvent = {
    id,
    serverId: dto.id,
    date: dto.date,
    timeOfDay: dto.timeOfDay,
    title: dto.title,
    occasion: dto.occasion,
    concepts: Array.isArray(dto.concepts) ? dto.concepts : [],
    weather: {
      temp: Number(w.temp),
      min: Number(w.min),
      max: Number(w.max),
      rainProb: Number(w.rainProb),
      cond: w.cond,
      ...(w.manual ? { manual: true } : {}),
    },
    updatedAt: dto.updatedAt,
  };
  if (dto.memo) plan.memo = dto.memo;
  if (dto.outfit && typeof dto.outfit === 'object' && dto.outfit.items) plan.outfit = dto.outfit as unknown as Outfit;
  if (dto.place) {
    plan.place = {
      ...(dto.place.regionCode ? { regionCode: dto.place.regionCode } : {}),
      label: dto.place.label,
      lat: Number(dto.place.lat),
      lon: Number(dto.place.lon),
    };
  }
  if (dto.weatherSource) plan.weatherSource = dto.weatherSource;
  if (dto.weatherCheckedAt) plan.weatherCheckedAt = dto.weatherCheckedAt;
  if (dto.forecastReady) plan.forecastReady = true;
  if (Array.isArray(dto.missingItemIds) && dto.missingItemIds.length) plan.missingItemIds = [...dto.missingItemIds];
  return plan;
};

/**
 * 서버에 저장한 응답을 기기 예정에 반영한다. 보낸 뒤 기기에서 또 고쳤으면(updatedAt이 달라짐) 기기 값을 두고
 * 서버 id만 붙여 다시 보낼 대상(pending)으로 남긴다.
 */
export const applySaved = (local: PlannedEvent, dto: PlanDto, sentUpdatedAt: string | undefined): PlannedEvent => {
  if (local.updatedAt === sentUpdatedAt) return fromPlanDto(dto, local.id);
  return { ...local, serverId: dto.id, pending: true };
};

// ── 판단 ──

/** 아직 서버에 보내지 못한 변경이 있는지 (서버 id가 없으면 아직 올리지 않은 예정) */
export const isPending = (p: PlannedEvent): boolean => !p.serverId || !!p.pending;

/** 기기 수정 시각이 서버 updatedAt보다 늦은지. 기기 시각이 없거나 읽을 수 없으면 서버가 최신 */
export const isLocalNewer = (localIso: string | undefined, serverIso: string | undefined): boolean => {
  const a = localIso ? Date.parse(localIso) : NaN;
  if (Number.isNaN(a)) return false;
  const b = serverIso ? Date.parse(serverIso) : NaN;
  if (Number.isNaN(b)) return true;
  return a > b;
};

const shiftYears = (key: string, years: number): string => `${Number(key.slice(0, 4)) + years}${key.slice(4)}`;

const shiftDay = (key: string, n: number): string => {
  const [y, m, d] = key.split('-').map(Number);
  const dt = new Date(y, m - 1, d + n);
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
};

/** 서버가 받을 수 있는 날짜인지 (오늘 앞뒤 1년, 하루 여유). 범위 밖 예정은 기기에만 둔다 */
export const inServerRange = (date: string, today: string): boolean => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return false;
  const min = shiftDay(shiftYears(today, -RANGE_YEARS), 1);
  const max = shiftDay(shiftYears(today, RANGE_YEARS), -1);
  return date >= min && date <= max;
};

/** 서버에 새로 올릴 예정: 서버 id가 없고, 거절당하지 않았고, 날짜가 범위 안 */
export const uploadTargets = (plans: PlannedEvent[], today: string): PlannedEvent[] =>
  plans.filter((p) => !p.serverId && !p.syncBlocked && inServerRange(p.date, today));

/** 목록을 size개씩 나눈다 (bulk 100개) */
export const chunk = <T>(list: T[], size: number = PLAN_BULK_SIZE): T[][] => {
  const out: T[][] = [];
  for (let i = 0; i < list.length; i += size) out.push(list.slice(i, i + size));
  return out;
};

const TIME_ORDER: Record<string, number> = { AM: 0, PM: 1, EVENING: 2 };

/** 날짜 → 시간대 순 (서버 목록과 같은 순서) */
export const sortPlans = (plans: PlannedEvent[]): PlannedEvent[] =>
  [...plans].sort((a, b) => a.date.localeCompare(b.date) || (TIME_ORDER[a.timeOfDay] ?? 9) - (TIME_ORDER[b.timeOfDay] ?? 9));

// ── 합치기 ──

export interface IMergeResult {
  /** 합친 기기 예정 (날짜·시간대 순) */
  plans: PlannedEvent[];
  /** 기기 쪽이 최신이라 서버에 다시 보낼 예정의 기기 id (PATCH) */
  push: string[];
  /** 기기에서 지웠는데 서버에 남아 있어 지울 서버 id */
  deleteServerIds: string[];
  /** 남겨 둘 지우기 기록 (서버에 아직 있는 것만, 서버 id를 채움) */
  tombstones: IPlanTombstone[];
}

/**
 * 서버 목록(GET /plans 전체)과 기기 예정을 합친다.
 * - 짝 찾기: 서버 id(serverId) → 아직 서버 id가 없는 기기 예정은 clientId(= 기기 id)
 * - 양쪽에 있으면: 기기에 보내지 못한 변경이 있고 기기 수정 시각이 서버 updatedAt보다 늦으면 기기 값(→ push), 아니면 서버 값
 * - 서버에만 있으면(다른 기기에서 만든 것) 기기에 추가. id는 clientId, 없으면 srv-서버id
 * - 기기에만 있는데 서버 id가 있으면 서버에서 지워진 것 → 기기에서도 지운다. 서버 id가 없으면 아직 올리지 않은 것 → 그대로
 * - 지우기 기록(tombstone)에 있는 서버 예정은 기기에 넣지 않고 지울 목록에
 */
export const mergePlans = (local: PlannedEvent[], server: PlanDto[], tombstones: IPlanTombstone[] = []): IMergeResult => {
  const byServerId = new Map<string, PlannedEvent>();
  const byId = new Map<string, PlannedEvent>();
  for (const p of local) {
    byId.set(p.id, p);
    if (p.serverId) byServerId.set(p.serverId, p);
  }
  const deadServer = new Set(tombstones.map((t) => t.serverId).filter((v): v is string => !!v));
  const deadClient = new Set(tombstones.map((t) => t.clientId));

  const used = new Set<string>();
  const out: PlannedEvent[] = [];
  const push: string[] = [];
  const deleteServerIds: string[] = [];
  const serverByClient = new Map<string, PlanDto>();
  const serverIds = new Set<string>();

  for (const dto of server) {
    serverIds.add(dto.id);
    if (dto.clientId) serverByClient.set(dto.clientId, dto);
    if (deadServer.has(dto.id) || (dto.clientId && deadClient.has(dto.clientId))) {
      deleteServerIds.push(dto.id);
      continue;
    }
    let mine = byServerId.get(dto.id);
    if (!mine && dto.clientId) {
      const same = byId.get(dto.clientId);
      if (same && !same.serverId) mine = same;
    }
    if (mine && !used.has(mine.id)) {
      used.add(mine.id);
      if (isPending(mine) && isLocalNewer(mine.updatedAt, dto.updatedAt)) {
        out.push({ ...mine, serverId: dto.id, pending: true });
        if (!mine.syncBlocked) push.push(mine.id);
      } else {
        out.push(fromPlanDto(dto, mine.id));
      }
      continue;
    }
    // 서버에만 있는 예정: 기기 id가 겹치면 srv-서버id
    const taken = (x: string) => used.has(x) || byId.has(x);
    let id = dto.clientId ?? `srv-${dto.id}`;
    if (taken(id)) id = `srv-${dto.id}`;
    while (taken(id)) id = `${id}-${dto.id}`;
    used.add(id);
    out.push(fromPlanDto(dto, id));
  }

  for (const p of local) {
    if (used.has(p.id)) continue;
    if (p.serverId) continue; // 서버에서 지워졌다 (다른 기기에서 삭제)
    out.push(p); // 아직 올리지 않은 예정
  }

  const keep: IPlanTombstone[] = [];
  for (const t of tombstones) {
    const serverId = t.serverId && serverIds.has(t.serverId) ? t.serverId : serverByClient.get(t.clientId)?.id;
    if (serverId) keep.push({ clientId: t.clientId, serverId });
  }

  return { plans: sortPlans(out), push, deleteServerIds, tombstones: keep };
};

// ── 오류 ──

export type TPlanErrorKind =
  /** 서버에 이 API가 없다 (예전 서버: 404·405·501) */
  | 'unsupported'
  /** 회원당 500개를 넘었다 (409 PLAN_LIMIT) */
  | 'limit'
  /** 입력 오류 (400 VALIDATION_FAILED): 다시 보내도 같다 */
  | 'invalid'
  /** 없는 예정 (PATCH·DELETE 404) */
  | 'notFound'
  /** 오프라인·시간 초과·서버 오류 등: 다음 동기화 때 다시 */
  | 'retry';

/**
 * 오류 종류. path가 'item'(PATCH·DELETE /plans/{id})이면 404는 없는 예정, 그 밖(목록·만들기·bulk)은 API 없음으로 본다
 */
export const planErrorKind = (e: unknown, path: 'collection' | 'item' = 'collection'): TPlanErrorKind => {
  if (!isApiError(e)) return 'retry';
  if (e.code === 'PLAN_LIMIT') return 'limit';
  if (e.status === 400 && e.code === 'VALIDATION_FAILED') return 'invalid';
  if (e.status === 405 || e.status === 501) return 'unsupported';
  if (e.status === 404) return path === 'item' ? 'notFound' : 'unsupported';
  return 'retry';
};

/** bulk 검증 오류에서 틀린 항목 번호 (필드 이름 items[3].title → 3) */
export const invalidIndexes = (e: unknown): Set<number> => {
  const out = new Set<number>();
  if (!isApiError(e)) return out;
  for (const fe of e.fieldErrors ?? []) {
    const m = /^items\[(\d+)\]/.exec(fe.field);
    if (m) out.add(Number(m[1]));
  }
  return out;
};

// ── 표시 ──

/**
 * 예정 코디에서 "지운 옷"으로 보여 줄 아이템 id: 서버가 알려 준 missingItemIds와,
 * 기기 옷장에 없는 아이템 (계정 모드는 옷을 지워도 예정 코디에서 빼지 않는다)
 */
export const goneItemIds = (plan: Pick<PlannedEvent, 'outfit' | 'missingItemIds'>, ownedIds: Set<string>): string[] => {
  if (!plan.outfit) return [];
  const missing = new Set(plan.missingItemIds ?? []);
  const ids = Object.values(plan.outfit.items).filter((v): v is string => !!v);
  return [...new Set(ids.filter((id) => missing.has(id) || !ownedIds.has(id)))];
};
