// 사용하지 않는 옷 · 완전 삭제 (doc/13): retire·restore, 목록 retired 필터, 통계, 코드 가드, 추천 후보
// 앱 API 모듈을 거치지 않고 원시 HTTP(raw)로만 확인한다.
import { eq, ok, sameSet } from '../lib/assert';
import { checkErrorBody, raw } from '../lib/http';
import { rawAdmin, rawSignup, type RawUser } from '../lib/rawUser';
import type { Scenario } from '../scenario';

const ITEMS = '/wardrobe/items';
const REASONS = ['NOT_WORN', 'DISCARDED', 'DONATED', 'SOLD', 'ETC'];

interface ItemOverrides {
  name?: string;
  category?: string;
  subType?: string;
  color?: string;
  status?: string;
  lastWornAt?: string | null;
}

function itemBody(o: ItemOverrides = {}) {
  return {
    name: '네이비 옥스퍼드 셔츠',
    category: 'TOP',
    subType: 'SHIRT',
    color: 'navy',
    pattern: 'SOLID',
    seasons: ['SPRING', 'FALL'],
    styles: ['NEAT'],
    formality: 2,
    status: 'OWNED',
    brand: null,
    ...o,
  };
}

async function create(u: RawUser, o: ItemOverrides = {}): Promise<string> {
  const res = await u.call('POST', ITEMS, { body: itemBody(o) });
  eq(res.status, 201, `아이템 등록 201 (${o.name ?? '기본'})`);
  return res.json.id;
}

async function retire(u: RawUser, id: string, reason: string, memo?: string) {
  const res = await u.call('POST', `${ITEMS}/${id}/retire`, { body: memo === undefined ? { reason } : { reason, memo } });
  eq(res.status, 200, `retire ${reason} 200`);
  return res.json;
}

async function listIds(u: RawUser, query = ''): Promise<string[]> {
  const res = await u.call('GET', `${ITEMS}${query}`);
  eq(res.status, 200, `목록 ${query || '(기본)'} 200`);
  return res.json.items.map((i: { id: string }) => i.id);
}

/** 서울 기준 오늘 (YYYY-MM-DD) */
function todaySeoul(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date());
}

const codes: Scenario = {
  group: '옷 사용 중지',
  name: 'GET /codes에 RETIRE_REASON 5개(안 입어요·버렸어요·기부했어요·팔았어요·기타), attrs 없음',
  async run() {
    const res = await raw('GET', '/codes');
    const group = res.json.groups.find((g: { group: string }) => g.group === 'RETIRE_REASON');
    ok(group, 'RETIRE_REASON 그룹', res.json.groups.map((g: { group: string }) => g.group));
    const active = group.codes.filter((c: { active: boolean }) => c.active);
    ok(REASONS.every((r) => active.some((c: { code: string }) => c.code === r)), '초기 코드 5개 (활성)', group.codes);
    eq(
      REASONS.map((r) => group.codes.find((c: { code: string }) => c.code === r).label),
      ['안 입어요', '버렸어요', '기부했어요', '팔았어요', '기타'],
      '라벨',
    );
    ok(group.codes.every((c: { attrs: object }) => Object.keys(c.attrs).length === 0), 'attrs 없음', group.codes);
  },
};

const flow: Scenario = {
  group: '옷 사용 중지',
  name: 'retire → 목록 기본에서 빠짐·retired=true/all에 보임, 다시 retire는 시각 유지, PATCH 가능, restore로 돌아옴',
  async run() {
    const u = await rawSignup('retire');
    const knit = await create(u, { name: '네이비 니트', subType: 'KNIT' });
    const shirt = await create(u, { name: '화이트 셔츠', color: 'white' });

    const fresh = (await u.call('GET', `${ITEMS}/${knit}`)).json;
    eq([fresh.retiredAt, fresh.retireReason, fresh.retireMemo], [null, null, null], '처음에는 retiredAt·retireReason·retireMemo = null');

    const r1 = await retire(u, knit, 'DONATED', '  아름다운가게 기부 ');
    eq(r1.id, knit, '응답은 그 아이템');
    eq([r1.retireReason, r1.retireMemo, r1.status], ['DONATED', '아름다운가게 기부', 'OWNED'], '이유·메모(앞뒤 공백 제거)·보유 상태 유지');
    ok(typeof r1.retiredAt === 'string' && r1.retiredAt.endsWith('Z') && Math.abs(Date.parse(r1.retiredAt) - Date.now()) < 60_000, 'retiredAt은 지금 ISO 시각', r1.retiredAt);

    eq(await listIds(u), [shirt], '목록 기본(사용 중인 옷만)');
    eq(await listIds(u, '?retired=false'), [shirt], 'retired=false');
    eq(await listIds(u, '?retired=true'), [knit], 'retired=true');
    sameSet(await listIds(u, '?retired=all'), [knit, shirt], 'retired=all');
    eq(await listIds(u, '?retired=true&q=%EB%8B%88%ED%8A%B8'), [knit], 'retired=true + 검색어 함께');
    checkErrorBody(await u.call('GET', `${ITEMS}?retired=maybe`), 400, 'VALIDATION_FAILED', '모르는 retired 값');

    const r2 = await retire(u, knit, 'SOLD');
    eq([r2.retiredAt, r2.retireReason, r2.retireMemo], [r1.retiredAt, 'SOLD', null], '이미 사용 중지면 이유·메모만 바뀌고 시각은 그대로');

    const patched = await u.call('PATCH', `${ITEMS}/${knit}`, { body: { name: '판 니트' } });
    eq([patched.status, patched.json.name, patched.json.retireReason], [200, '판 니트', 'SOLD'], '사용 중지한 옷도 PATCH 가능');

    const restored = await u.call('POST', `${ITEMS}/${knit}/restore`);
    eq(restored.status, 200, 'restore 200');
    eq([restored.json.retiredAt, restored.json.retireReason, restored.json.retireMemo, restored.json.status], [null, null, null, 'OWNED'], 'restore: 사용 중지 값이 비고 원래 상태');
    const again = await u.call('POST', `${ITEMS}/${knit}/restore`);
    eq([again.status, again.json], [200, restored.json], '사용 중인 옷 restore도 그대로 200');
    sameSet(await listIds(u), [knit, shirt], 'restore 뒤 목록 기본에 다시 보임');
  },
};

const stats: Scenario = {
  group: '옷 사용 중지',
  name: '통계: 사용 중인 옷만 세고 retired·retiredByReason(많은 순)을 따로, restore 뒤 다시 계산',
  async run() {
    const u = await rawSignup('retire-st');
    await create(u, { name: '오늘 입은 셔츠', color: 'white', lastWornAt: todaySeoul() });
    const slacks = await create(u, { name: '슬랙스', category: 'BOTTOM', subType: 'SLACKS', color: 'black' });
    const coat = await create(u, { name: '코트', category: 'OUTER', subType: 'COAT', color: 'beige' });
    const loafer = await create(u, { name: '로퍼', category: 'SHOES', subType: 'LOAFER', color: 'brown' });
    await retire(u, slacks, 'DONATED');
    await retire(u, coat, 'DONATED');
    await retire(u, loafer, 'SOLD');

    const st = (await u.call('GET', '/wardrobe/stats')).json;
    eq(st.total, 1, 'total = 사용 중인 옷만');
    eq([st.byCategory.TOP, st.byCategory.BOTTOM, st.byCategory.OUTER, st.byCategory.SHOES], [1, 0, 0, 0], 'byCategory');
    eq(st.byStatus.OWNED, 1, 'byStatus.OWNED');
    eq(st.byColor, [{ color: 'white', count: 1 }], 'byColor');
    eq([st.wornLast30Days, st.utilization30d], [1, 1], '최근 착용·활용률');
    eq(st.sleeping, [], 'sleeping에 사용 중지 옷 없음');
    eq(st.retired, 3, 'retired');
    eq(st.retiredByReason, [{ reason: 'DONATED', count: 2 }, { reason: 'SOLD', count: 1 }], 'retiredByReason (많은 순)');

    eq((await u.call('POST', `${ITEMS}/${loafer}/restore`)).status, 200, '로퍼 restore');
    const st2 = (await u.call('GET', '/wardrobe/stats')).json;
    eq([st2.total, st2.retired, st2.byCategory.SHOES], [2, 2, 1], 'restore 뒤 total·retired·byCategory');
    eq(st2.sleeping, [loafer], 'restore한 옷은 sleeping 계산에 다시 들어감');
    eq(st2.retiredByReason, [{ reason: 'DONATED', count: 2 }], 'restore 뒤 retiredByReason');
  },
};

const deleteVsRetire: Scenario = {
  group: '옷 사용 중지',
  name: '완전 삭제와 차이(삭제는 all에도 없음), 남의 옷 404, 입력 400(모르는 이유·메모 201자·이유 없음)',
  async run() {
    const u = await rawSignup('retire-del');
    const other = await rawSignup('retire-other');
    const kept = await create(u, { name: '기부한 셔츠' });
    const gone = await create(u, { name: '버린 셔츠', color: 'white' });
    await retire(u, kept, 'DONATED');

    eq((await u.call('DELETE', `${ITEMS}/${gone}`)).status, 204, '완전 삭제 204');
    checkErrorBody(await u.call('GET', `${ITEMS}/${gone}`), 404, 'NOT_FOUND', '삭제한 옷 조회');
    checkErrorBody(await u.call('POST', `${ITEMS}/${gone}/restore`), 404, 'NOT_FOUND', '삭제한 옷 restore');
    eq(await listIds(u, '?retired=all'), [kept], 'retired=all: 사용 중지 옷은 남고 삭제한 옷은 없음');
    eq((await u.call('GET', `${ITEMS}/${kept}`)).status, 200, '사용 중지 옷 단건 조회 200');

    checkErrorBody(await other.call('POST', `${ITEMS}/${kept}/restore`), 404, 'NOT_FOUND', '남의 옷 restore');
    checkErrorBody(await other.call('POST', `${ITEMS}/${kept}/retire`, { body: { reason: 'ETC' } }), 404, 'NOT_FOUND', '남의 옷 retire');
    checkErrorBody(await other.call('POST', `${ITEMS}/${kept}/retire`, { body: { reason: 'LOST' } }), 404, 'NOT_FOUND', '남의 옷이면 입력이 틀려도 404가 먼저');
    eq(await listIds(other, '?retired=all'), [], '남의 목록에 안 보임');
    checkErrorBody(await raw('POST', `${ITEMS}/${kept}/retire`, { body: { reason: 'ETC' } }), 401, 'UNAUTHORIZED', '토큰 없이');

    const target = await create(u, { name: '검증용 셔츠' });
    const bad = await u.call('POST', `${ITEMS}/${target}/retire`, { body: { reason: 'LOST', memo: '가'.repeat(201) } });
    checkErrorBody(bad, 400, 'VALIDATION_FAILED', '모르는 이유 + 메모 201자');
    sameSet(bad.json.fieldErrors.map((e: { field: string }) => e.field), ['reason', 'memo'], 'fieldErrors: reason·memo');
    const none = await u.call('POST', `${ITEMS}/${target}/retire`, { body: {} });
    checkErrorBody(none, 400, 'VALIDATION_FAILED', '이유 없음');
    eq(none.json.fieldErrors.map((e: { field: string }) => e.field), ['reason'], 'fieldErrors: reason');
    eq((await u.call('GET', `${ITEMS}/${target}`)).json.retiredAt, null, '400이면 바뀌지 않음');
    eq((await retire(u, target, 'ETC', '가'.repeat(200))).retireMemo.length, 200, '메모 200자는 됨');
  },
};

const inactiveReason: Scenario = {
  group: '옷 사용 중지',
  name: '관리자: 비활성 이유 코드로 retire 400, 사용 중지 옷이 쓰는 코드 삭제 409 CODE_IN_USE, 회원 목록 itemCount·retiredItemCount',
  needs: ['admin'],
  async run() {
    const admin = await rawAdmin();
    const u = await rawSignup('retire-code');
    const code = `H${Date.now().toString(36).toUpperCase()}`.slice(0, 12);
    eq((await admin.call('POST', '/admin/codes/RETIRE_REASON', { body: { code, label: '하네스 이유', attrs: {} } })).status, 201, '이유 코드 추가 201');

    const a = await create(u, { name: '셔츠 A' });
    const b = await create(u, { name: '셔츠 B', color: 'white' });
    await create(u, { name: '셔츠 C', color: 'black' });
    eq((await retire(u, a, code)).retireReason, code, '새 이유 코드로 retire');

    checkErrorBody(await admin.call('DELETE', `/admin/codes/RETIRE_REASON/${code}`), 409, 'CODE_IN_USE', '사용 중지 옷이 쓰는 이유 코드 삭제');
    eq((await admin.call('PATCH', `/admin/codes/RETIRE_REASON/${code}`, { body: { active: false } })).status, 200, '이유 코드 비활성화');
    const bad = await u.call('POST', `${ITEMS}/${b}/retire`, { body: { reason: code } });
    checkErrorBody(bad, 400, 'VALIDATION_FAILED', '비활성 이유 코드로 retire');
    eq(bad.json.fieldErrors.map((e: { field: string }) => e.field), ['reason'], 'fieldErrors: reason');

    const users = await admin.call('GET', `/admin/users?q=${encodeURIComponent(u.email)}`);
    const row = users.json.items[0];
    eq([row.itemCount, row.retiredItemCount], [2, 1], '회원 목록: itemCount = 사용 중, retiredItemCount = 사용 중지');

    // 정리: 아이템을 지우면 코드를 지울 수 있다
    eq((await u.call('DELETE', `${ITEMS}/${a}`)).status, 204, '사용 중지 옷 완전 삭제');
    eq((await admin.call('DELETE', `/admin/codes/RETIRE_REASON/${code}`)).status, 204, '쓰는 옷이 없으면 코드 삭제 204');
  },
};

const outfit: Scenario = {
  group: '옷 사용 중지',
  name: 'AI 코디 추천: 사용 중지 옷은 후보에서 빠지고, 고정·중심으로 주면 400',
  async run() {
    const u = await rawSignup('retire-outfit');
    const top = await create(u, { name: '화이트 셔츠', color: 'white' });
    const knit = await create(u, { name: '네이비 니트', subType: 'KNIT' });
    await create(u, { name: '차콜 슬랙스', category: 'BOTTOM', subType: 'SLACKS', color: 'charcoal' });
    const loafer = await create(u, { name: '블랙 로퍼', category: 'SHOES', subType: 'LOAFER', color: 'black' });
    const sneakers = await create(u, { name: '화이트 스니커즈', category: 'SHOES', subType: 'SNEAKER', color: 'white' });
    await retire(u, knit, 'NOT_WORN');
    await retire(u, sneakers, 'DISCARDED');

    const context = {
      date: todaySeoul(),
      occasion: 'COMMUTE',
      mood: 'NEAT',
      formality: 'BIZ_CASUAL',
      weather: { temp: 14, min: 8, max: 19, rainProb: 20, cond: 'CLOUDY' },
    };
    const ok200 = await u.call('POST', '/outfits/recommendations', { body: { context } });
    eq(ok200.status, 200, '추천 200');
    const used = new Set<string>();
    for (const c of ok200.json.candidates) Object.values(c.items as Record<string, string>).forEach((id) => used.add(id));
    ok(!used.has(knit) && !used.has(sneakers), '후보에 사용 중지 옷 없음', [...used]);
    ok(used.has(top) && used.has(loafer), '사용 중인 옷으로 후보를 만듦', [...used]);

    const pinned = await u.call('POST', '/outfits/recommendations', {
      body: { context: { ...context, fixed: { SHOES: sneakers }, anchorItemId: knit } },
    });
    checkErrorBody(pinned, 400, 'VALIDATION_FAILED', '사용 중지 옷을 고정·중심으로');
    sameSet(pinned.json.fieldErrors.map((e: { field: string }) => e.field), ['context.fixed.SHOES', 'context.anchorItemId'], 'fieldErrors');
    ok(pinned.json.fieldErrors.every((e: { code: string }) => e.code === 'ITEM_RETIRED'), '코드 ITEM_RETIRED', pinned.json.fieldErrors);
  },
};

export const retireScenarios: Scenario[] = [codes, flow, stats, deleteVsRetire, inactiveReason, outfit];
