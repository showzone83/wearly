// 옷장 CRUD·목록 필터·일괄 등록·통계·소유권
import { eq, expectApiError, isIsoDateTime, ok, sameSet } from '../lib/assert';
import { addDays, comparable, item, todaySeoul } from '../lib/fixtures';
import { checkErrorBody, raw } from '../lib/http';
import { signedUp, tokensOf } from '../lib/session';
import type { ItemDto, ItemInput } from '../../../mobile/src/api/types';
import { loadCodes } from '../lib/serverCodes';
import type { Scenario } from '../scenario';

const ids = (xs: ItemDto[]) => xs.map((x) => x.id);

const crud: Scenario = {
  group: '옷장',
  name: 'create → get → update(PATCH 일부) → remove → get 404',
  async run() {
    const u = await signedUp('crud');
    const input = item();
    const created = await u.api.wardrobe.create(input);
    ok(typeof created.id === 'string' && created.id.length > 0, 'id는 문자열', created.id);
    ok(isIsoDateTime(created.createdAt), 'createdAt은 ISO-8601', created.createdAt);
    eq(comparable(created), comparable(input), '등록 응답 = 입력');

    const got = await u.api.wardrobe.get(created.id);
    eq(got, created, 'get = 등록 응답');

    // brand·wornCount·lastWornAt 없이 등록하면 기본값
    const minimal = await u.api.wardrobe.create({
      name: '기본값 확인 티',
      category: 'TOP',
      subType: 'TEE',
      color: 'white',
      pattern: 'SOLID',
      seasons: ['SUMMER'],
      styles: [],
      formality: 1,
      status: 'OWNED',
    });
    eq(minimal.wornCount, 0, '기본 wornCount');
    eq(minimal.brand ?? null, null, '기본 brand');
    eq(minimal.lastWornAt ?? null, null, '기본 lastWornAt');

    const today = todaySeoul();
    const patched = await u.api.wardrobe.update(created.id, { status: 'LAUNDRY', wornCount: 4, lastWornAt: today });
    eq(
      comparable(patched),
      { ...comparable(input), status: 'LAUNDRY', wornCount: 4, lastWornAt: today },
      'PATCH: 보낸 필드만 바뀌고 나머지는 유지',
    );
    eq(patched.id, created.id, 'PATCH 뒤 id 유지');
    eq(patched.createdAt, created.createdAt, 'PATCH 뒤 createdAt 유지');
    eq(await u.api.wardrobe.get(created.id), patched, 'PATCH 뒤 get');

    const patched2 = await u.api.wardrobe.update(created.id, { brand: null, seasons: ['WINTER'] });
    eq(patched2.brand ?? null, null, 'brand를 null로 지움');
    eq(patched2.seasons, ['WINTER'], 'seasons 교체');
    eq(patched2.status, 'LAUNDRY', '앞서 바꾼 status 유지');

    await u.api.wardrobe.remove(created.id);
    await expectApiError(u.api.wardrobe.get(created.id), { status: 404, code: 'NOT_FOUND' }, '삭제 뒤 get');
    await expectApiError(u.api.wardrobe.remove(created.id), { status: 404, code: 'NOT_FOUND' }, '삭제 뒤 다시 remove');
    sameSet(ids((await u.api.wardrobe.list()).items), [minimal.id], '삭제 뒤 목록');

    const t = await tokensOf(u);
    const res = await raw('GET', '/wardrobe/items/999999999', { token: t.accessToken });
    checkErrorBody(res, 404, 'NOT_FOUND', '없는 아이템 원시 요청');
  },
};

const listFilters: Scenario = {
  group: '옷장',
  name: 'list 필터 category·status·color·q·sort',
  async run() {
    const u = await signedUp('list');
    // 순서대로 등록한다 (recent 정렬 확인용)
    const specs: ItemInput[] = [
      item({ name: '옥스퍼드 셔츠', category: 'TOP', subType: 'SHIRT', color: 'navy', status: 'OWNED', brand: '하네스상회', wornCount: 5 }),
      item({ name: '울 슬랙스', category: 'BOTTOM', subType: 'SLACKS', color: 'black', status: 'LAUNDRY', brand: '바지공방', wornCount: 1 }),
      item({ name: '캐멀 코트', category: 'OUTER', subType: 'COAT', color: 'camel', status: 'REPAIR', brand: null, wornCount: 10 }),
      item({ name: '니트 스웨터', category: 'TOP', subType: 'KNIT', color: 'navy', status: 'OWNED', brand: '셔츠명가', wornCount: 0 }),
      item({ name: '흰 스니커즈', category: 'SHOES', subType: 'SNEAKER', color: 'white', status: 'OWNED', brand: null, wornCount: 7 }),
    ];
    const made: ItemDto[] = [];
    for (const s of specs) made.push(await u.api.wardrobe.create(s));
    const [shirt, slacks, coat, knit, sneaker] = made;

    const all = (await u.api.wardrobe.list()).items;
    sameSet(ids(all), ids(made), '필터 없음: 전체');
    eq(ids(all), ids([...made].reverse()), '기본 정렬 recent: 최근 등록순');

    const list = async (f: Parameters<typeof u.api.wardrobe.list>[0]) => (await u.api.wardrobe.list(f)).items;

    sameSet(ids(await list({ category: 'TOP' })), [shirt.id, knit.id], 'category=TOP');
    sameSet(ids(await list({ status: 'LAUNDRY' })), [slacks.id], 'status=LAUNDRY');
    sameSet(ids(await list({ status: 'REPAIR' })), [coat.id], 'status=REPAIR');
    sameSet(ids(await list({ color: 'navy' })), [shirt.id, knit.id], 'color=navy');
    sameSet(ids(await list({ q: '셔츠' })), [shirt.id, knit.id], "q='셔츠' (이름 '옥스퍼드 셔츠' + 브랜드 '셔츠명가')");
    sameSet(ids(await list({ q: '공방' })), [slacks.id], "q='공방' (브랜드 부분 일치)");
    sameSet(ids(await list({ q: '없는검색어' })), [], 'q 일치 없음 → 빈 목록');
    sameSet(
      ids(await list({ category: 'TOP', color: 'navy', status: 'OWNED', q: '니트' })),
      [knit.id],
      'category+color+status+q 조합',
    );
    sameSet(ids(await list({ category: 'BAG' })), [], 'category=BAG (없음)');

    eq(ids(await list({ sort: 'worn' })), [coat.id, sneaker.id, shirt.id, slacks.id, knit.id], "sort=worn: 많이 입은 순");
    const byName = [...made].sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));
    eq(ids(await list({ sort: 'name' })), ids(byName), 'sort=name: 이름 가나다순');
    eq(ids(await list({ sort: 'recent' })), ids([...made].reverse()), 'sort=recent 명시');
    eq(ids(await list({ category: 'TOP', sort: 'worn' })), [shirt.id, knit.id], 'category=TOP + sort=worn');

    const t = await tokensOf(u);
    const bad = await raw('GET', '/wardrobe/items?category=HAT', { token: t.accessToken });
    checkErrorBody(bad, 400, 'VALIDATION_FAILED', '없는 category 값');
  },
};

const badPair: Scenario = {
  group: '옷장',
  name: '잘못된 category·subType 짝 → 400 VALIDATION_FAILED',
  async run() {
    const u = await signedUp('pair');
    await expectApiError(
      u.api.wardrobe.create(item({ category: 'TOP', subType: 'SLACKS' })),
      { status: 400, code: 'VALIDATION_FAILED' },
      'TOP + SLACKS 등록',
    );
    // CARDIGAN은 앱 SUBTYPE 표에서 OUTER다
    await expectApiError(
      u.api.wardrobe.create(item({ category: 'TOP', subType: 'CARDIGAN' })),
      { status: 400, code: 'VALIDATION_FAILED' },
      'TOP + CARDIGAN 등록 (CARDIGAN은 OUTER)',
    );
    const cardigan = await u.api.wardrobe.create(item({ name: '가디건', category: 'OUTER', subType: 'CARDIGAN' }));
    eq(cardigan.category, 'OUTER', 'OUTER + CARDIGAN은 허용');

    const shirt = await u.api.wardrobe.create(item());
    await expectApiError(
      u.api.wardrobe.update(shirt.id, { category: 'BOTTOM' }),
      { status: 400, code: 'VALIDATION_FAILED' },
      'PATCH로 category만 바꿔 짝이 깨짐 (SHIRT + BOTTOM)',
    );
    const ok2 = await u.api.wardrobe.update(shirt.id, { category: 'BOTTOM', subType: 'CHINO' });
    eq([ok2.category, ok2.subType], ['BOTTOM', 'CHINO'], '짝을 함께 바꾸면 허용');

    await expectApiError(
      u.api.wardrobe.create(item({ formality: 4 as unknown as 1 })),
      { status: 400, code: 'VALIDATION_FAILED' },
      'formality 4',
    );
    const bad = await raw('POST', '/wardrobe/items', {
      token: (await tokensOf(u)).accessToken,
      body: item({ formality: 4 as unknown as 1 }),
    });
    checkErrorBody(bad, 400, 'VALIDATION_FAILED', 'formality 4 원시 응답');
    ok(
      bad.json.fieldErrors.some(
        (f: { field: string; code: string; params?: { min?: number; max?: number } }) =>
          f.field === 'formality' && f.code === 'OUT_OF_RANGE' && f.params?.min === 1 && f.params?.max === 3,
      ),
      'formality 오류는 OUT_OF_RANGE {min:1,max:3}',
      bad.json.fieldErrors,
    );
    await expectApiError(
      u.api.wardrobe.create(item({ name: '' })),
      { status: 400, code: 'VALIDATION_FAILED' },
      '빈 name',
    );
    sameSet(ids((await u.api.wardrobe.list()).items), [cardigan.id, shirt.id], '실패한 등록은 남지 않음');
  },
};

const bulk: Scenario = {
  group: '옷장',
  name: 'bulkCreate 요청 순서 유지, 101개 → 400',
  async run() {
    const u = await signedUp('bulk');
    const inputs: ItemInput[] = [
      item({ name: '하나 셔츠', subType: 'SHIRT' }),
      item({ name: '둘 슬랙스', category: 'BOTTOM', subType: 'SLACKS', color: 'gray' }),
      item({ name: '셋 로퍼', category: 'SHOES', subType: 'LOAFER', color: 'black' }),
      item({ name: '넷 토트백', category: 'BAG', subType: 'TOTE', color: 'brown' }),
      item({ name: '다섯 시계', category: 'ACC', subType: 'WATCH', color: 'black', wornCount: 2, lastWornAt: todaySeoul() }),
    ];
    const out = (await u.api.wardrobe.bulkCreate(inputs)).items;
    eq(out.length, inputs.length, '응답 개수');
    eq(out.map(comparable), inputs.map(comparable), '응답이 요청 순서대로, 값 일치');
    eq(new Set(ids(out)).size, out.length, 'id 서로 다름');
    sameSet(ids((await u.api.wardrobe.list()).items), ids(out), '목록에 모두 있음');

    const many = Array.from({ length: 101 }, (_, i) => item({ name: `대량 ${i}` }));
    await expectApiError(u.api.wardrobe.bulkCreate(many), { status: 400, code: 'VALIDATION_FAILED' }, '101개 일괄 등록');
    await expectApiError(
      u.api.wardrobe.bulkCreate([item({ name: '정상' }), item({ category: 'TOP', subType: 'COAT' })]),
      { status: 400, code: 'VALIDATION_FAILED' },
      '일괄 등록 중 짝이 틀린 항목',
    );
    eq((await u.api.wardrobe.list()).items.length, inputs.length, '실패한 일괄 등록은 하나도 남지 않음');
  },
};

const stats: Scenario = {
  group: '옷장',
  name: 'stats 수치 (total·byCategory·byColor·byStyle·byStatus·utilization·sleeping)',
  async run() {
    const u = await signedUp('stats');
    const empty = await u.api.wardrobe.stats();
    eq(empty.total, 0, '빈 옷장 total');
    eq(empty.utilization30d, 0, '빈 옷장 utilization30d (0으로 나누지 않음)');

    const today = todaySeoul();
    // 경계(30일·60일)에서 멀리 떨어진 날짜만 써서 시간대 차이에 흔들리지 않게 한다
    const { items: out } = await u.api.wardrobe.bulkCreate([
      item({ name: 'A', category: 'TOP', subType: 'KNIT', color: 'navy', styles: ['NEAT', 'MINIMAL'], status: 'OWNED', wornCount: 2, lastWornAt: addDays(today, -5) }),
      item({ name: 'B', category: 'TOP', subType: 'SHIRT', color: 'white', styles: ['NEAT'], status: 'OWNED', wornCount: 1, lastWornAt: addDays(today, -40) }),
      item({ name: 'C', category: 'BOTTOM', subType: 'SLACKS', color: 'navy', styles: ['CLASSIC'], status: 'LAUNDRY', wornCount: 0, lastWornAt: null }),
      item({ name: 'D', category: 'SHOES', subType: 'SNEAKER', color: 'black', styles: ['CASUAL', 'NEAT'], status: 'REPAIR', wornCount: 7, lastWornAt: addDays(today, -1) }),
      item({ name: 'E', category: 'BAG', subType: 'TOTE', color: 'navy', styles: ['NEAT'], status: 'OWNED', wornCount: 3, lastWornAt: addDays(today, -70) }),
    ]);
    const c = out[2];
    const e = out[4];

    const st = await u.api.wardrobe.stats();
    eq(st.total, 5, 'total');
    // 활성 CATEGORY 코드 전부가 키로 들어간다 (0 포함, 06 문서 4장)
    const byCat: Record<string, number> = Object.fromEntries((await loadCodes()).active('CATEGORY').map((c) => [c.code, 0]));
    Object.assign(byCat, { TOP: 2, BOTTOM: 1, SHOES: 1, BAG: 1 });
    eq(st.byCategory, byCat, 'byCategory (활성 종류 전부, 0도 포함)');
    eq(st.byStatus, { OWNED: 3, LAUNDRY: 1, REPAIR: 1 }, 'byStatus');
    eq(st.byColor[0], { color: 'navy', count: 3 }, 'byColor 첫째 = 가장 많은 색');
    sameSet(st.byColor.map((x) => `${x.color}:${x.count}`), ['navy:3', 'white:1', 'black:1'], 'byColor 전체');
    eq(st.byStyle[0], { style: 'NEAT', count: 4 }, 'byStyle 첫째 = 가장 많은 스타일');
    sameSet(st.byStyle.map((x) => `${x.style}:${x.count}`), ['NEAT:4', 'MINIMAL:1', 'CLASSIC:1', 'CASUAL:1'], 'byStyle 전체');
    ok(
      st.byColor.every((x, i, arr) => i === 0 || arr[i - 1].count >= x.count),
      'byColor는 많은 순',
      st.byColor,
    );
    eq(st.wornLast30Days, 2, 'wornLast30Days (A 5일 전, D 1일 전)');
    eq(st.utilization30d, 0.4, 'utilization30d = 2/5');
    sameSet(st.sleeping, [c.id, e.id], 'sleeping (한 번도 안 입은 C, 70일 전 E)');

    // 삭제가 통계에 반영되는지
    await u.api.wardrobe.remove(e.id);
    const st2 = await u.api.wardrobe.stats();
    eq(st2.total, 4, '삭제 뒤 total');
    eq(st2.utilization30d, 0.5, '삭제 뒤 utilization30d = 2/4');
    sameSet(st2.sleeping, [c.id], '삭제 뒤 sleeping');

    // 소수 둘째 자리 반올림: 1/3 → 0.33
    await u.api.wardrobe.remove(c.id);
    await u.api.wardrobe.create(item({ name: 'F', wornCount: 0 }));
    const st3 = await u.api.wardrobe.stats();
    eq([st3.total, st3.wornLast30Days], [4, 2], '추가 뒤 total·wornLast30Days');
    eq(st3.utilization30d, 0.5, '추가 뒤 utilization30d = 2/4');
    await u.api.wardrobe.create(item({ name: 'G', wornCount: 0 }));
    await u.api.wardrobe.create(item({ name: 'H', wornCount: 0 }));
    eq((await u.api.wardrobe.stats()).utilization30d, 0.33, 'utilization30d = 2/6 → 0.33');
  },
};

const ownership: Scenario = {
  group: '옷장',
  name: '다른 사용자의 아이템 get·update·remove → 404, 목록·통계에 안 보임',
  async run() {
    const owner = await signedUp('owner');
    const other = await signedUp('other');
    const mine = await owner.api.wardrobe.create(item({ name: '내 셔츠' }));

    await expectApiError(other.api.wardrobe.get(mine.id), { status: 404, code: 'NOT_FOUND' }, '남의 아이템 get');
    await expectApiError(
      other.api.wardrobe.update(mine.id, { name: '훔친 셔츠' }),
      { status: 404, code: 'NOT_FOUND' },
      '남의 아이템 PATCH',
    );
    await expectApiError(other.api.wardrobe.remove(mine.id), { status: 404, code: 'NOT_FOUND' }, '남의 아이템 remove');
    eq((await other.api.wardrobe.list()).items.length, 0, '다른 사용자 목록은 비어 있음');
    eq((await other.api.wardrobe.stats()).total, 0, '다른 사용자 통계 total');

    const still = await owner.api.wardrobe.get(mine.id);
    eq(still.name, '내 셔츠', '주인의 아이템은 그대로');
  },
};

export const wardrobeScenarios: Scenario[] = [crud, listFilters, badPair, bulk, stats, ownership];
