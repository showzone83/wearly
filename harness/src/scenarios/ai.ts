// AI 사진 분석 (목업 공급자)
import { COLOR, SUBTYPE } from '../../../mobile/src/data/constants';
import { eq, expectApiError, isNonEmptyString, ok } from '../lib/assert';
import { HARNESS_AI_DAILY_LIMIT } from '../lib/config';
import { checkErrorBody, raw } from '../lib/http';
import { oversizedPngBlob, pngBlob, solidPng } from '../lib/png';
import { signedUp, tokensOf } from '../lib/session';
import type { AnalyzeResponse, Category, Pattern, Season, StyleTag } from '../../../mobile/src/api/types';
import type { Scenario } from '../scenario';

const CATEGORIES: Category[] = ['TOP', 'BOTTOM', 'OUTER', 'SHOES', 'BAG', 'ACC'];
const PATTERNS: Pattern[] = ['SOLID', 'STRIPE', 'CHECK', 'ETC'];
const SEASONS: Season[] = ['SPRING', 'SUMMER', 'FALL', 'WINTER'];
const STYLES: StyleTag[] = ['NEAT', 'MINIMAL', 'CASUAL', 'CLASSIC', 'STREET'];

/** 제안값이 앱의 허용 값 안에 있는지 */
function checkSuggestion(r: AnalyzeResponse, label: string): void {
  const s = r.suggestion;
  ok(isNonEmptyString(s.name), `${label}: name`, s.name);
  ok(CATEGORIES.includes(s.category), `${label}: category 허용 값`, s.category);
  ok(s.subType in SUBTYPE, `${label}: subType 허용 값`, s.subType);
  eq(SUBTYPE[s.subType].category, s.category, `${label}: category·subType 짝 (앱 SUBTYPE 표)`);
  ok(s.color in COLOR, `${label}: color 허용 값`, s.color);
  ok(PATTERNS.includes(s.pattern), `${label}: pattern 허용 값`, s.pattern);
  ok(Array.isArray(s.seasons) && s.seasons.every((x) => SEASONS.includes(x)), `${label}: seasons 허용 값`, s.seasons);
  ok(new Set(s.seasons).size === s.seasons.length, `${label}: seasons 중복 없음`, s.seasons);
  ok(Array.isArray(s.styles) && s.styles.every((x) => STYLES.includes(x)), `${label}: styles 허용 값`, s.styles);
  ok([1, 2, 3].includes(s.formality), `${label}: formality 1~3`, s.formality);
  ok(typeof s.confidence === 'number' && s.confidence >= 0 && s.confidence <= 1, `${label}: confidence 0~1`, s.confidence);
  eq(r.provider, 'mock', `${label}: provider`);
  ok(r.model === null || typeof r.model === 'string', `${label}: model은 null 또는 문자열`, r.model);
  ok(Number.isInteger(r.remainingToday) && r.remainingToday >= 0, `${label}: remainingToday는 0 이상 정수`, r.remainingToday);
}

const analyze: Scenario = {
  group: 'AI',
  name: 'analyze(목업): 허용 값 범위, 같은 이미지 같은 결과, remainingToday 감소',
  async run(ctx) {
    const u = await signedUp('ai');
    const r1 = await u.api.wardrobe.analyze(pngBlob([30, 40, 90]));
    checkSuggestion(r1, '첫 분석');
    if (!ctx.external) eq(r1.remainingToday, HARNESS_AI_DAILY_LIMIT - 1, '첫 분석 뒤 remainingToday (한도 3)');

    const r2 = await u.api.wardrobe.analyze(pngBlob([30, 40, 90]));
    checkSuggestion(r2, '같은 이미지');
    eq(r2.suggestion, r1.suggestion, '같은 이미지 → 같은 제안 (SHA-256 기반)');
    eq(r2.remainingToday, r1.remainingToday - 1, '두 번째 뒤 remainingToday 1 감소');

    ctx.note(`목업 제안 예: ${r1.suggestion.name} / ${r1.suggestion.category}·${r1.suggestion.subType}·${r1.suggestion.color}, model=${r1.model}`);

    // 다른 사용자는 자기 한도를 따로 쓴다
    const other = await signedUp('ai-other');
    const r3 = await other.api.wardrobe.analyze(pngBlob([30, 40, 90]));
    eq(r3.suggestion, r1.suggestion, '다른 사용자도 같은 이미지면 같은 제안');
    eq(r3.remainingToday, r1.remainingToday, '다른 사용자의 사용량은 섞이지 않음');

    // 파트 이름 image 로 원시 요청
    const t = await tokensOf(u);
    const form = new FormData();
    form.append('image', new Blob([solidPng([200, 10, 10])], { type: 'image/png' }), 'raw.png');
    const res = await raw('POST', '/wardrobe/items/analyze', { token: t.accessToken, body: form });
    eq(res.status, 200, "원시 multipart(파트 'image') 상태");
    eq(res.json?.remainingToday, r2.remainingToday - 1, '원시 요청도 사용량에 포함');
  },
};

const limit: Scenario = {
  group: 'AI',
  name: '일일 한도 초과 → 429 AI_LIMIT_EXCEEDED (실패 호출은 세지 않음)',
  async run(ctx) {
    const u = await signedUp('ai-limit');
    let remaining = Number.POSITIVE_INFINITY;
    let successes = 0;
    // remainingToday를 보고 한도까지 쓴다 (--external이어도 동작, 최대 30회)
    while (remaining > 0 && successes < 30) {
      const r = await u.api.wardrobe.analyze(pngBlob([successes * 7, 100, 200]));
      ok(r.remainingToday < remaining, `${successes + 1}번째 분석에서 remainingToday 감소`, r.remainingToday);
      remaining = r.remainingToday;
      successes++;
    }
    ok(remaining === 0, '30회 안에 한도에 도달', { successes, remaining });
    if (!ctx.external) eq(successes, HARNESS_AI_DAILY_LIMIT, '성공 횟수 = WEARLY_AI_DAILY_LIMIT(3)');
    else ctx.note(`외부 서버 AI 일일 한도 = ${successes}`);

    await expectApiError(
      u.api.wardrobe.analyze(pngBlob([1, 2, 3])),
      { status: 429, code: 'AI_LIMIT_EXCEEDED' },
      '한도 초과 분석',
    );
    const t = await tokensOf(u);
    const form = new FormData();
    form.append('image', pngBlob([4, 5, 6]), 'x.png');
    const res = await raw('POST', '/wardrobe/items/analyze', { token: t.accessToken, body: form });
    checkErrorBody(res, 429, 'AI_LIMIT_EXCEEDED', '한도 초과 원시 요청');
  },
};

const tooLarge: Scenario = {
  group: 'AI',
  name: '10MB 넘는 이미지 → 413 IMAGE_TOO_LARGE, 한도 차감 없음',
  async run(ctx) {
    const u = await signedUp('ai-big');
    await expectApiError(
      u.api.wardrobe.analyze(oversizedPngBlob(10 * 1024 * 1024 + 64 * 1024)),
      { status: 413, code: 'IMAGE_TOO_LARGE' },
      '10MB + 64KB 이미지',
    );
    const r = await u.api.wardrobe.analyze(pngBlob([9, 9, 9]));
    if (!ctx.external) eq(r.remainingToday, HARNESS_AI_DAILY_LIMIT - 1, '실패한 호출은 한도에서 빼지 않음');
  },
};

export const aiScenarios: Scenario[] = [analyze, limit, tooLarge];
