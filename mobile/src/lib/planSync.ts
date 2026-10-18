// 예정 코디 서버 동기화 (계정 모드). 규칙(변환·합치기·오류 종류)은 lib/planSyncRules.ts의 순수 함수에 있다.
// - 로그인·앱 시작(세션 확인 뒤)·복귀 때 syncPlans: 지운 예정 서버에서 지우기 → 아직 올리지 않은 예정 bulk(100개씩, clientId = 기기 id)
//   → GET /plans 전체를 받아 기기와 합치기 → 기기 쪽이 최신인 예정 PATCH
// - 예정 만들기·고치기·지우기는 기기에 먼저 반영하고 서버에 보낸다 (savePlan · pushPlan · removePlan).
//   실패(오프라인 등)는 기기 예정에 pending·지우기 기록(planDeletes)으로 남겨 다음 동기화 때 다시 보낸다 (같은 clientId라 중복되지 않는다).
// - 게스트·목업 실행(npm run start:mock)은 기기만 쓴다. 서버에 이 API가 없으면(404) 기기만 쓰고 10분 동안 다시 묻지 않는다.
import type { PlanDto } from '@/api/types';
import type { IPlanTombstone, PlannedEvent } from '@/data/types';
import { useStore } from '@/store/useStore';
import { api } from './api';
import { todayKey } from './date';
import { MOCK_MODE } from './mode';
import { createSupportMemo } from './photoRules';
import {
  applySaved,
  chunk,
  inServerRange,
  invalidIndexes,
  isPending,
  mergePlans,
  planErrorKind,
  toPlanInput,
  toPlanPatch,
  uploadTargets,
} from './planSyncRules';

/** 복귀 때 다시 맞추는 최소 간격 */
const MIN_SYNC_GAP_MS = 30_000;

const support = createSupportMemo();
let lastSyncAt = 0;

const log = (...args: unknown[]) => {
  if (__DEV__) console.log('[plan-sync]', ...args);
};

/** 서버와 맞출 수 있는 상태인지: 서버 실행 + 계정 모드 + 로그인 유지 + API 있음 */
const canSync = (): boolean => {
  if (MOCK_MODE || support.knownUnsupported(Date.now())) return false;
  const s = useStore.getState();
  return s.authMode === 'account' && !s.authLost && !!s.user;
};

const currentUserId = (): string | null => useStore.getState().user?.id ?? null;

const findPlan = (id: string): PlannedEvent | undefined => useStore.getState().plans.find((p) => p.id === id);

/** 한 예정만 바꾼다 (없으면 아무것도 안 한다) */
const updatePlan = (id: string, fn: (p: PlannedEvent) => PlannedEvent | null) => {
  const plans = useStore.getState().plans;
  const i = plans.findIndex((p) => p.id === id);
  if (i < 0) return;
  const next = fn(plans[i]);
  const list = [...plans];
  if (next) list[i] = next;
  else list.splice(i, 1);
  useStore.setState({ plans: list });
};

const markUnsupported = () => {
  support.markUnsupported(Date.now());
  log('서버에 /plans가 없어 기기에만 저장해요 (10분 뒤 다시 확인)');
};

// ── 한 예정 보내기 ──

export type TPushOutcome =
  /** 서버에 저장했거나 보낼 것이 없음 */
  | { kind: 'ok' }
  /** 지금은 보내지 않음 (게스트·목업·API 없음·날짜 범위 밖) */
  | { kind: 'skipped' }
  /** 실패해 대기열에 둠 (다음 동기화 때 다시) */
  | { kind: 'queued'; error: unknown }
  /** 회원당 500개 초과 (대기열에 두지만 지난 예정을 지워야 올라간다) */
  | { kind: 'limit'; error: unknown }
  /** 서버가 입력 오류로 거절 (기기에만 둔다) */
  | { kind: 'invalid'; error: unknown }
  /** 다른 기기에서 지운 예정이라 기기에서도 지움 */
  | { kind: 'gone' };

interface IPushOptions {
  /** PATCH가 404(다른 기기에서 지움)면 새로 만든다 (사용자가 방금 저장한 예정). 아니면 기기에서도 지운다 */
  recreate?: boolean;
}

const pushOnce = async (id: string, opts: IPushOptions): Promise<TPushOutcome> => {
  const plan = findPlan(id);
  if (!plan || plan.syncBlocked || !isPending(plan)) return { kind: 'ok' };
  if (!plan.serverId && !inServerRange(plan.date, todayKey())) return { kind: 'skipped' };
  const userId = currentUserId();
  const sent = plan.updatedAt;
  let dto: PlanDto;
  try {
    dto = plan.serverId ? await api.plans.patch(plan.serverId, toPlanPatch(plan)) : await api.plans.create(toPlanInput(plan));
  } catch (e) {
    if (currentUserId() !== userId) return { kind: 'skipped' };
    const kind = planErrorKind(e, plan.serverId ? 'item' : 'collection');
    if (kind === 'notFound') {
      if (opts.recreate) {
        // 다른 기기에서 지운 예정을 이 기기에서 방금 저장했다 → 새로 만든다 (clientId는 같다)
        updatePlan(id, (p) => ({ ...p, serverId: undefined, pending: true }));
        return pushOnce(id, { recreate: false });
      }
      updatePlan(id, () => null);
      log('다른 기기에서 지운 예정이라 기기에서도 지웠어요', id);
      return { kind: 'gone' };
    }
    if (kind === 'unsupported') {
      markUnsupported();
      return { kind: 'skipped' };
    }
    if (kind === 'invalid') {
      updatePlan(id, (p) => (p.updatedAt === sent ? { ...p, syncBlocked: true } : p));
      console.warn('[plan-sync] 서버가 예정을 받지 않아 기기에만 둬요', id, e);
      return { kind: 'invalid', error: e };
    }
    if (kind === 'limit') return { kind: 'limit', error: e };
    log('예정을 보내지 못해 다음에 다시 보내요', id, (e as Error)?.message);
    return { kind: 'queued', error: e };
  }
  if (currentUserId() !== userId) return { kind: 'skipped' };
  support.markSupported();
  updatePlan(id, (p) => applySaved(p, dto, sent));
  return { kind: 'ok' };
};

const pushing = new Map<string, Promise<TPushOutcome>>();
const pushAgain = new Set<string>();

/**
 * 기기에서 고친 예정 하나를 서버에 보낸다 (서버 id가 없으면 POST + clientId, 있으면 PATCH 전체 필드).
 * 같은 예정을 보내는 중이면 끝난 뒤 한 번 더 보낸다. throw하지 않는다.
 */
export const pushPlan = (id: string, opts: IPushOptions = {}): Promise<TPushOutcome> => {
  if (!canSync()) return Promise.resolve({ kind: 'skipped' });
  const running = pushing.get(id);
  if (running) {
    pushAgain.add(id);
    return running;
  }
  const job = (async () => {
    let out: TPushOutcome;
    do {
      pushAgain.delete(id);
      out = await pushOnce(id, opts);
    } while (out.kind === 'ok' && pushAgain.has(id) && canSync());
    return out;
  })()
    .catch((e): TPushOutcome => {
      console.warn('[plan-sync] 예정 보내기 실패', id, e);
      return { kind: 'queued', error: e };
    })
    .finally(() => pushing.delete(id));
  pushing.set(id, job);
  return job;
};

/** 예정 저장 (코디 저장·다시 추천): 기기에 먼저 반영하고 서버에 보낸다 */
export const savePlan = (plan: PlannedEvent): Promise<TPushOutcome> => {
  useStore.getState().addPlan(plan);
  return pushPlan(plan.id, { recreate: true });
};

// ── 지우기 ──

/** 지우기 기록 하나를 서버에서 지운다. 끝났으면(지움·이미 없음) true */
const deleteOnServer = async (serverId: string): Promise<boolean | 'stop'> => {
  try {
    await api.plans.remove(serverId);
    return true;
  } catch (e) {
    const kind = planErrorKind(e, 'item');
    if (kind === 'notFound') return true;
    if (kind === 'unsupported') {
      markUnsupported();
      return 'stop';
    }
    log('예정을 서버에서 지우지 못해 다음에 다시 지워요', serverId, (e as Error)?.message);
    return 'stop';
  }
};

const dropTombstone = (serverId: string) =>
  useStore.setState({ planDeletes: useStore.getState().planDeletes.filter((t) => t.serverId !== serverId) });

/** 서버 id가 있는 지우기 기록을 서버에서 지운다. 실패하면 남겨 두고 멈춘다 */
const flushDeletes = async (): Promise<boolean> => {
  const userId = currentUserId();
  const targets = useStore.getState().planDeletes.filter((t): t is Required<IPlanTombstone> => !!t.serverId);
  for (const t of targets) {
    const done = await deleteOnServer(t.serverId);
    if (currentUserId() !== userId) return false;
    if (done === 'stop') return false;
    dropTombstone(t.serverId);
  }
  return true;
};

/**
 * 예정 지우기 (캘린더): 기기에서 지우고, 계정 모드면 서버에서도 지운다.
 * 서버 id가 없어도(올리는 중일 수 있음) 기록을 남겨 다음 동기화 때 서버 목록에서 clientId로 찾아 지운다.
 */
export const removePlan = (id: string): void => {
  const s = useStore.getState();
  const plan = s.plans.find((p) => p.id === id);
  s.deletePlan(id);
  if (!plan || MOCK_MODE || s.authMode !== 'account') return;
  const tomb: IPlanTombstone = { clientId: plan.id, ...(plan.serverId ? { serverId: plan.serverId } : {}) };
  useStore.setState({ planDeletes: [...useStore.getState().planDeletes.filter((t) => t.clientId !== plan.id), tomb] });
  if (plan.serverId && canSync()) void flushDeletes();
};

// ── 전체 맞추기 ──

/** 아직 서버 id가 없는 예정을 bulk로 올린다. 입력 오류 항목은 빼고 한 번 더. 계속할 수 없으면 false */
const uploadBatch = async (batch: PlannedEvent[], retry: boolean): Promise<boolean> => {
  const userId = currentUserId();
  const sent = new Map(batch.map((p) => [p.id, p.updatedAt]));
  let items: PlanDto[];
  try {
    items = (await api.plans.bulk(batch.map(toPlanInput))).items;
  } catch (e) {
    if (currentUserId() !== userId) return false;
    const kind = planErrorKind(e);
    if (kind === 'unsupported') {
      markUnsupported();
      return false;
    }
    if (kind === 'invalid') {
      const bad = invalidIndexes(e);
      if (!bad.size || !retry) {
        console.warn('[plan-sync] 서버가 예정 묶음을 받지 않았어요', e);
        return true; // 이 묶음만 건너뛴다
      }
      const badIds = new Set(batch.filter((_, i) => bad.has(i)).map((p) => p.id));
      for (const pid of badIds) updatePlan(pid, (p) => (p.updatedAt === sent.get(pid) ? { ...p, syncBlocked: true } : p));
      console.warn('[plan-sync] 서버가 받지 않는 예정은 기기에만 둬요', [...badIds], e);
      const rest = batch.filter((p) => !badIds.has(p.id));
      return rest.length ? uploadBatch(rest, false) : true;
    }
    if (kind === 'limit') console.warn('[plan-sync] 예정 개수 상한(500개)을 넘어 더 올리지 못했어요');
    else log('예정을 올리지 못해 다음에 다시 올려요', (e as Error)?.message);
    return false;
  }
  if (currentUserId() !== userId) return false;
  support.markSupported();
  items.forEach((dto, i) => {
    const pid = dto.clientId ?? batch[i]?.id;
    if (pid) updatePlan(pid, (p) => applySaved(p, dto, sent.get(pid)));
  });
  return true;
};

const doSync = async (): Promise<void> => {
  const userId = currentUserId();
  if (!(await flushDeletes())) return;

  // 1) 아직 올리지 않은 예정 (처음이면 기기의 예정 전부)
  for (const batch of chunk(uploadTargets(useStore.getState().plans, todayKey()))) {
    if (!(await uploadBatch(batch, true))) break;
    if (!canSync()) return;
  }
  if (!canSync()) return;

  // 2) 서버 목록과 합치기
  let server: PlanDto[];
  try {
    server = (await api.plans.list()).items;
  } catch (e) {
    if (planErrorKind(e) === 'unsupported') markUnsupported();
    else log('예정 목록을 받지 못했어요', (e as Error)?.message);
    return;
  }
  if (currentUserId() !== userId || !canSync()) return;
  support.markSupported();
  const s = useStore.getState();
  const merged = mergePlans(s.plans, server, s.planDeletes);
  useStore.setState({ plans: merged.plans, planDeletes: merged.tombstones });
  lastSyncAt = Date.now();
  log(`맞춤: 서버 ${server.length}개 · 기기 ${merged.plans.length}개 · 보낼 ${merged.push.length}개 · 지울 ${merged.deleteServerIds.length}개`);

  // 3) 기기에서 지운 예정 · 기기 쪽이 최신인 예정
  for (const serverId of merged.deleteServerIds) {
    const done = await deleteOnServer(serverId);
    if (done === 'stop' || currentUserId() !== userId) return;
    dropTombstone(serverId);
  }
  for (const id of merged.push) {
    const out = await pushPlan(id);
    if (out.kind === 'queued' || out.kind === 'skipped') return;
  }
};

let syncing: Promise<void> | null = null;

/**
 * 서버와 예정을 맞춘다 (로그인·앱 시작·복귀). 동시에 여러 번 불러도 한 번만 돈다. throw하지 않는다.
 * @param force 최소 간격(30초)을 무시한다 (로그인·온보딩 직후)
 */
export const syncPlans = (opts: { force?: boolean } = {}): Promise<void> => {
  if (!canSync()) return Promise.resolve();
  // 로그인 직후(force)라면 돌던 동기화(다른 계정이었을 수 있다)가 끝난 뒤 한 번 더
  if (syncing) return opts.force ? syncing.then(() => syncPlans({ force: true })) : syncing;
  if (!opts.force && Date.now() - lastSyncAt < MIN_SYNC_GAP_MS) return Promise.resolve();
  syncing = doSync()
    .catch((e) => console.warn('[plan-sync] 동기화 실패', e))
    .finally(() => {
      syncing = null;
    });
  return syncing;
};
