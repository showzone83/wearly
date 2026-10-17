// 코드 관리 (doc/06): GET /codes 형식·ETag, 새 코드로 아이템 등록, 코드 기반 검증
import { readFileSync } from 'node:fs';
import { eq, expectApiError, ok } from '../lib/assert';
import { item } from '../lib/fixtures';
import { loadCodes } from '../lib/serverCodes';
import { newSession, signedUp } from '../lib/session';
import type { Scenario } from '../scenario';

interface SeedJson {
  groups: { group: string; codes: { code: string; parent: string | null; attrs: Record<string, unknown> }[] }[];
}

/** 계약의 초기값 원본 (doc/06_코드_초기값.json) */
function seed(): SeedJson {
  const url = new URL('../../../doc/06_코드_초기값.json', import.meta.url);
  return JSON.parse(readFileSync(url, 'utf-8')) as SeedJson;
}

const format: Scenario = {
  group: '코드',
  name: 'GET /codes: 초기값과 같은 그룹·코드·parent, ETag 304',
  async run(ctx) {
    const codes = await loadCodes(true);
    const s = seed();
    eq(codes.raw.groups.map((g) => g.group), s.groups.map((g) => g.group), '그룹 순서');
    for (const g of s.groups) {
      const got = codes.raw.groups.find((x) => x.group === g.group)!;
      const want = g.codes.map((c) => c.code);
      // 다른 시나리오나 관리자가 코드를 더할 수 있으니 초기값이 모두 들어 있는지만 본다
      ok(want.every((c) => got.codes.some((x) => x.code === c)), `${g.group}: 초기값 코드 전부 있음`, got.codes.map((x) => x.code));
      ok(got.codes.every((c, i, a) => i === 0 || a[i - 1].sortOrder <= c.sortOrder), `${g.group}: sortOrder 순서`);
    }
    for (const c of codes.raw.groups.find((g) => g.group === 'SUB_TYPE')!.codes) {
      ok(codes.activeSet('CATEGORY').has(c.parent ?? '') || !c.active, `SUB_TYPE ${c.code}: parent가 CATEGORY 코드`, c.parent);
    }
    const color = codes.active('COLOR').find((c) => c.code === 'navy')!;
    ok(/^#[0-9A-Fa-f]{6}$/.test(String(color.attrs.hex)) && Array.isArray(color.attrs.pairs), 'COLOR attrs: hex·pairs', color.attrs);
    ok(codes.etag.length > 2, 'ETag 헤더', codes.etag);

    const again = await newSession().api.codes.list(codes.etag);
    eq(again.changed, false, '같은 ETag로 다시 요청하면 304(변경 없음)');
    ctx.note(`코드 version = ${codes.raw.version}`);
  },
};

const newCodes: Scenario = {
  group: '코드',
  name: '새 코드(원피스·청바지·데님·플로럴·페미닌)로 등록, 모르는 코드·짝 불일치 400',
  async run() {
    const u = await signedUp('codes');
    const dress = await u.api.wardrobe.create(
      item({ name: '데님 셔츠 원피스', category: 'DRESS', subType: 'SHIRT_DRESS', color: 'denim', pattern: 'FLORAL', styles: ['FEMININE'] }),
    );
    eq([dress.category, dress.subType, dress.color, dress.pattern], ['DRESS', 'SHIRT_DRESS', 'denim', 'FLORAL'], '원피스 저장값');
    const jeans = await u.api.wardrobe.create(item({ name: '청바지', category: 'BOTTOM', subType: 'JEANS', color: 'denim' }));
    eq(jeans.subType, 'JEANS', '청바지 저장');

    const st = await u.api.wardrobe.stats();
    eq(st.byCategory.DRESS, 1, '통계 byCategory.DRESS');

    await expectApiError(u.api.wardrobe.create(item({ color: 'fuchsia' })), { status: 400, code: 'VALIDATION_FAILED' }, '모르는 색');
    await expectApiError(
      u.api.wardrobe.create(item({ category: 'TOP', subType: 'DRESS' })),
      { status: 400, code: 'VALIDATION_FAILED' },
      '원피스 세부 종류를 상의로',
    );
    await expectApiError(
      u.api.wardrobe.create(item({ styles: ['NEAT', 'GOTHIC'] })),
      { status: 400, code: 'VALIDATION_FAILED' },
      '모르는 스타일',
    );
    const list = await u.api.wardrobe.list({ category: 'DRESS' });
    eq(list.items.map((i) => i.id), [dress.id], '목록 필터 category=DRESS');
  },
};

export const codeScenarios: Scenario[] = [format, newCodes];
