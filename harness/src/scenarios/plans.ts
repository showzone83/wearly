// 예정 코디 서버 저장 (doc/19 "다음 단계"): GET/POST/PATCH/DELETE /plans, POST /plans/bulk.
// 서버 계약 자체를 확인하려고 앱 API 모듈(mobile/src/api의 api.plans) 대신 원시 HTTP(raw)로 부른다.
import { eq, isIsoDateTime, ok } from '../lib/assert';
import { checkErrorBody, raw, type RawResponse } from '../lib/http';
import { rawSignup } from '../lib/rawUser';
import type { Scenario } from '../scenario';

const PLAN_KEYS = [
  'clientId',
  'concepts',
  'createdAt',
  'date',
  'forecastReady',
  'id',
  'memo',
  'missingItemIds',
  'occasion',
  'outfit',
  'place',
  'timeOfDay',
  'title',
  'updatedAt',
  'weather',
  'weatherCheckedAt',
  'weatherSource',
];

/** 한국 날짜 YYYY-MM-DD (오늘 + days) */
function seoulDate(days = 0): string {
  return new Date(Date.now() + 9 * 3600_000 + days * 86400_000).toISOString().slice(0, 10);
}

/** 앱 PlannedEvent 모양 (id 대신 clientId) */
function plan(clientId: string | null, days: number, extra: Record<string, unknown> = {}) {
  return {
    ...(clientId ? { clientId } : {}),
    date: seoulDate(days),
    timeOfDay: 'PM',
    title: '결혼식 하객',
    occasion: 'WEDDING',
    concepts: ['MINIMAL', 'CLASSIC'],
    weather: { temp: 18.5, min: 13, max: 22, rainProb: 20, cond: 'CLOUDY', manual: false },
    ...extra,
  };
}

const fieldCodes = (res: RawResponse) =>
  (res.json.fieldErrors as { field: string; code: string }[]).map((e) => `${e.field}:${e.code}`);

const crud: Scenario = {
  group: '예정 코디',
  name: '등록(201) → 목록(날짜·시간대 순, from/to) → PATCH(보낸 필드만, null은 비움) → DELETE(204) → 다시 404, 토큰 없이 401',
  async run() {
    checkErrorBody(await raw('GET', '/plans'), 401, 'UNAUTHORIZED', '토큰 없이 목록');
    const user = await rawSignup('plan');
    const created = await user.call('POST', '/plans', {
      body: plan(null, 7, {
        memo: '  신부 측 ',
        place: { regionCode: null, label: '도쿄', lat: 35.68, lon: 139.69 },
        weatherSource: 'CLIMATE',
        weatherCheckedAt: '2026-10-18T09:00:00+09:00',
        outfit: { id: 'o-1', items: { TOP: 'item-local' }, score: 80, reasons: ['좋아요'] },
      }),
    });
    eq(created.status, 201, '등록 201');
    const p = created.json;
    eq(Object.keys(p).sort(), PLAN_KEYS, '응답 키');
    ok(/^\d+$/.test(p.id), 'id는 숫자 문자열', p.id);
    eq([p.clientId, p.memo, p.weatherSource, p.weatherCheckedAt], [null, '신부 측', 'CLIMATE', '2026-10-18T00:00:00Z'], 'clientId·memo·출처·확인 시각');
    eq(p.place, { regionCode: null, label: '도쿄', lat: 35.68, lon: 139.69 }, 'place');
    eq(p.weather, { temp: 18.5, min: 13, max: 22, rainProb: 20, cond: 'CLOUDY', manual: false }, 'weather');
    eq(p.outfit, { id: 'o-1', items: { TOP: 'item-local' }, score: 80, reasons: ['좋아요'] }, 'outfit은 보낸 모양 그대로');
    eq(p.missingItemIds, ['item-local'], '옷장에 없는 아이템 ID');
    ok(isIsoDateTime(p.createdAt) && isIsoDateTime(p.updatedAt), 'createdAt·updatedAt ISO', p);

    const am = (await user.call('POST', '/plans', { body: plan(null, 2, { timeOfDay: 'AM' }) })).json.id;
    const evening = (await user.call('POST', '/plans', { body: plan(null, 2, { timeOfDay: 'EVENING' }) })).json.id;
    const list = await user.call('GET', '/plans');
    eq(list.status, 200, '목록 200');
    eq(Object.keys(list.json), ['items'], '목록 키');
    eq(list.json.items.map((i: { id: string }) => i.id), [am, evening, p.id], '날짜 → 시간대 순');
    const ranged = await user.call('GET', `/plans?from=${seoulDate(3)}&to=${seoulDate(7)}`);
    eq(ranged.json.items.map((i: { id: string }) => i.id), [p.id], 'from/to (양 끝 포함)');

    const patched = await user.call('PATCH', `/plans/${p.id}`, {
      body: { memo: null, place: null, forecastReady: true, weather: { temp: 3, min: -1, max: 7, rainProb: 70, cond: 'RAIN' } },
    });
    eq(patched.status, 200, 'PATCH 200');
    eq([patched.json.memo, patched.json.place, patched.json.forecastReady], [null, null, true], 'null은 비우고 보낸 값은 바꿈');
    eq(patched.json.weather, { temp: 3, min: -1, max: 7, rainProb: 70, cond: 'RAIN', manual: false }, 'weather 바꿈');
    eq([patched.json.title, patched.json.weatherSource], ['결혼식 하객', 'CLIMATE'], '안 보낸 필드는 그대로');

    eq((await user.call('DELETE', `/plans/${p.id}`)).status, 204, '삭제 204');
    checkErrorBody(await user.call('DELETE', `/plans/${p.id}`), 404, 'NOT_FOUND', '지운 예정 다시 삭제');
    checkErrorBody(await user.call('PATCH', `/plans/${p.id}`, { body: { title: 'x' } }), 404, 'NOT_FOUND', '지운 예정 PATCH');
  },
};

const bulk: Scenario = {
  group: '예정 코디',
  name: 'bulk: 요청 순서대로 만들거나(같은 clientId면) 고침, 다시 올려도 늘지 않음, clientId 중복·없음·101개 → 400, 틀린 항목이 있으면 하나도 저장 안 함, POST도 같은 clientId면 200',
  async run(ctx) {
    const user = await rawSignup('plan-bulk');
    const first = await user.call('POST', '/plans', { body: plan('sample-plan-1', 0) });
    eq(first.status, 201, 'clientId 등록 201');
    const again = await user.call('POST', '/plans', { body: plan('sample-plan-1', 1, { title: '바꾼 제목' }) });
    eq([again.status, again.json.id, again.json.title], [200, first.json.id, '바꾼 제목'], '같은 clientId POST는 200·같은 id');

    const items = [plan('plan-a', 3), plan('sample-plan-1', 24), plan('plan-b', -30)];
    const res = await user.call('POST', '/plans/bulk', { body: { items } });
    eq(res.status, 200, 'bulk 200');
    eq(Object.keys(res.json).sort(), ['created', 'items', 'updated'], 'bulk 응답 키');
    eq([res.json.created, res.json.updated], [2, 1], 'created 2 · updated 1');
    eq(res.json.items.map((i: { clientId: string }) => i.clientId), ['plan-a', 'sample-plan-1', 'plan-b'], '요청 순서');
    eq(res.json.items[1].id, first.json.id, '기존 예정은 같은 id');
    const twice = await user.call('POST', '/plans/bulk', { body: { items } });
    eq([twice.json.created, twice.json.updated], [0, 3], '다시 올리면 모두 updated');
    eq((await user.call('GET', '/plans')).json.items.length, 3, '목록 3개');

    const dup = await user.call('POST', '/plans/bulk', { body: { items: [plan('x', 0), plan('x', 0)] } });
    checkErrorBody(dup, 400, 'VALIDATION_FAILED', 'clientId 중복');
    eq(fieldCodes(dup), ['items[1].clientId:DUPLICATE'], 'items[1].clientId DUPLICATE');
    const noId = await user.call('POST', '/plans/bulk', { body: { items: [plan(null, 0)] } });
    eq(fieldCodes(noId), ['items[0].clientId:REQUIRED'], 'clientId 없음 REQUIRED');
    const bad = await user.call('POST', '/plans/bulk', { body: { items: [plan('ok-1', 0), plan('bad-1', 0, { occasion: 'PARTY' })] } });
    eq(fieldCodes(bad), ['items[1].occasion:NOT_ALLOWED'], '틀린 항목');
    const many = await user.call('POST', '/plans/bulk', { body: { items: Array.from({ length: 101 }, (_, i) => plan(`p-${i}`, 0)) } });
    eq(fieldCodes(many), ['items:TOO_MANY'], '101개 TOO_MANY');
    eq((await user.call('GET', '/plans')).json.items.length, 3, '실패한 bulk는 저장 안 함');
    ctx.note('bulk 상한 100개, 회원당 예정 500개(넘으면 409 PLAN_LIMIT)');
  },
};

const validation: Scenario = {
  group: '예정 코디',
  name: '입력 검증(필드 오류 코드)·날짜 범위(앞뒤 1년)·남의 예정 404·지운 옷은 missingItemIds',
  async run() {
    const user = await rawSignup('plan-check');
    const res = await user.call('POST', '/plans', {
      body: plan('bad id', 367, {
        timeOfDay: 'NIGHT',
        concepts: ['CLASSIC', 'NOPE'],
        place: { regionCode: 'NOWHERE', label: '어딘가', lat: 91, lon: 10 },
        outfit: { items: { HAT: '1' } },
      }),
    });
    checkErrorBody(res, 400, 'VALIDATION_FAILED', '여러 필드 오류');
    eq(
      fieldCodes(res),
      [
        'clientId:INVALID_FORMAT',
        'date:OUT_OF_RANGE',
        'timeOfDay:NOT_ALLOWED',
        'concepts[1]:NOT_ALLOWED',
        'place.lat:OUT_OF_RANGE',
        'place.regionCode:NOT_ALLOWED',
        'outfit.items.HAT:NOT_ALLOWED',
      ],
      'fieldErrors',
    );
    const range = res.json.fieldErrors[1].params;
    eq(Object.keys(range).sort(), ['max', 'min'], 'date params는 min·max');
    ok(range.min <= seoulDate(-365) && range.max >= seoulDate(365) && range.max < seoulDate(367), 'date 범위는 오늘 앞뒤 1년 (YYYY-MM-DD)', range);
    const noWeather = await user.call('POST', '/plans', { body: { ...plan(null, 0), weather: undefined } });
    eq(fieldCodes(noWeather), ['weather:REQUIRED'], 'weather 필수');
    eq((await user.call('POST', '/plans', { body: plan(null, -364) })).status, 201, '지난 날짜(1년 안)도 저장');

    // 지운 옷은 missingItemIds로 표시
    const item = await user.call('POST', '/wardrobe/items', {
      body: { name: '셔츠', category: 'TOP', subType: 'SHIRT', color: 'navy', pattern: 'SOLID', seasons: ['FALL'], styles: ['NEAT'], formality: 2, status: 'OWNED' },
    });
    eq(item.status, 201, '옷 등록');
    const withOutfit = await user.call('POST', '/plans', { body: plan(null, 1, { outfit: { items: { TOP: item.json.id } } }) });
    eq(withOutfit.json.missingItemIds, [], '있는 옷은 표시 안 함');
    eq((await user.call('DELETE', `/wardrobe/items/${item.json.id}`)).status, 204, '옷 완전 삭제');
    const listed = (await user.call('GET', '/plans')).json.items.find((i: { id: string }) => i.id === withOutfit.json.id);
    eq(listed.missingItemIds, [item.json.id], '지운 옷 id 표시');
    eq(listed.outfit.items.TOP, item.json.id, '코디는 그대로');

    const other = await rawSignup('plan-other');
    checkErrorBody(await other.call('PATCH', `/plans/${withOutfit.json.id}`, { body: { title: '' } }), 404, 'NOT_FOUND', '남의 예정 PATCH (입력이 틀려도 404 먼저)');
    checkErrorBody(await other.call('DELETE', `/plans/${withOutfit.json.id}`), 404, 'NOT_FOUND', '남의 예정 DELETE');
    eq((await other.call('GET', '/plans')).json.items, [], '남의 목록에 안 보임');
  },
};

export const planScenarios: Scenario[] = [crud, bulk, validation];
