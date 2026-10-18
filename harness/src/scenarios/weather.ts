// 장소별 날씨 (doc/19): GET /weather/day(region 공개 · lat/lon 로그인), 날짜 검증, GET /weather/places(로그인)
// 앱 API 모듈을 거치지 않고 원시 HTTP(raw)로만 확인한다. 하네스 백엔드는 목업 날씨·목업 평년값(WEARLY_WEATHER_PROVIDER=mock)으로 돈다.
import { eq, isIsoDateTime, ok } from '../lib/assert';
import { checkErrorBody, raw } from '../lib/http';
import { rawAdmin, rawSignup } from '../lib/rawUser';
import type { Scenario } from '../scenario';

const DAY_KEYS = ['climate', 'date', 'day', 'fetchedAt', 'forecastUntil', 'place', 'source'];

/** 한국 날짜 YYYY-MM-DD (오늘 + days) */
function seoulDate(days = 0): string {
  const d = new Date(Date.now() + 9 * 3600_000 + days * 86400_000);
  return d.toISOString().slice(0, 10);
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** 관리자 "지금 실행"으로 서울을 받아 둔다 (시작 실행과 겹쳐 409면 잠깐 뒤 다시) */
async function refreshSeoul(): Promise<void> {
  const admin = await rawAdmin();
  const deadline = Date.now() + 30_000;
  let started = await admin.call('POST', '/admin/weather/refresh', { body: { regionCode: 'SEOUL' } });
  while (started.status === 409 && Date.now() < deadline) {
    await sleep(300);
    started = await admin.call('POST', '/admin/weather/refresh', { body: { regionCode: 'SEOUL' } });
  }
  eq(started.status, 202, '서울 지금 실행 202');
  while (Date.now() < deadline) {
    const run = await admin.call('GET', `/admin/weather/runs/${started.json.runId}`);
    if (run.json?.run?.status !== 'RUNNING') {
      eq(run.json.run.status, 'SUCCESS', '서울 실행 성공 (목업 공급자)');
      return;
    }
    await sleep(200);
  }
  throw new Error('서울 날씨 실행이 끝나지 않았어요');
}

const regionDay: Scenario = {
  group: '장소 날씨',
  name: 'GET /weather/day region 방식: 토큰 없이 200, 오늘은 FORECAST, 40일 뒤는 CLIMATE(평년값), 틀린 토큰도 무시',
  needs: ['admin'],
  async run(ctx) {
    await refreshSeoul();
    const today = seoulDate();
    const res = await raw('GET', `/weather/day?region=SEOUL&date=${today}`);
    eq(res.status, 200, '토큰 없이 200');
    eq(Object.keys(res.json).sort(), DAY_KEYS, '응답 키');
    eq(res.json.source, 'FORECAST', '오늘은 예보');
    eq(res.json.date, today, 'date');
    eq(res.json.place, { regionCode: 'SEOUL', label: '서울', lat: 37.5665, lon: 126.978 }, 'place');
    eq(Object.keys(res.json.day).sort(), ['cond', 'condText', 'max', 'min', 'rainProb', 'temp'], 'day 키');
    ok(['SUNNY', 'CLOUDY', 'RAIN'].includes(res.json.day.cond), 'day.cond', res.json.day.cond);
    ok(res.json.forecastUntil >= today, 'forecastUntil은 오늘이나 그 뒤', res.json.forecastUntil);
    ok(isIsoDateTime(res.json.fetchedAt), 'fetchedAt은 ISO 시각', res.json.fetchedAt);
    eq(res.json.climate, null, 'FORECAST면 climate null');

    const far = seoulDate(40);
    const climate = await raw('GET', `/weather/day?region=SEOUL&date=${far}`);
    eq(climate.status, 200, '40일 뒤 200');
    eq(climate.json.source, 'CLIMATE', '예보 밖은 평년값');
    eq([climate.json.day.cond, climate.json.day.condText], [null, null], 'CLIMATE면 cond·condText null');
    ok(climate.json.day.min < climate.json.day.max, '평년 최저 < 최고', climate.json.day);
    ok(/^\d{4}-\d{4}$/.test(climate.json.climate.years), 'climate.years = YYYY-YYYY', climate.json.climate);
    ok(climate.json.climate.rainDayRatio >= 0 && climate.json.climate.rainDayRatio <= 1, 'rainDayRatio 0~1', climate.json.climate);
    eq(climate.json.day.rainProb, Math.round(climate.json.climate.rainDayRatio * 100), 'rainProb = 비 온 날 비율 × 100 (반올림)');
    eq(climate.json.forecastUntil, res.json.forecastUntil, '같은 장소라 forecastUntil도 같음');

    eq((await raw('GET', `/weather/day?region=SEOUL&date=${today}`, { token: 'not-a-jwt' })).status, 200, '틀린 토큰이 같이 와도 200');
    ctx.note(`서울 ${far} 평년 ${climate.json.day.min}~${climate.json.day.max}° (${climate.json.climate.years})`);
  },
};

const coordinateDay: Scenario = {
  group: '장소 날씨',
  name: 'GET /weather/day lat/lon 방식: 토큰 없이 401, 로그인하면 격자 좌표로 FORECAST·CLIMATE, /weather/places도 로그인 필요',
  async run(ctx) {
    const today = seoulDate();
    checkErrorBody(await raw('GET', `/weather/day?lat=35.68&lon=139.69&date=${today}`), 401, 'UNAUTHORIZED', '좌표 방식 토큰 없이');
    checkErrorBody(await raw('GET', `/weather/day?lat=999&date=x`), 401, 'UNAUTHORIZED', '좌표가 틀려도 로그인이 먼저');
    checkErrorBody(await raw('GET', `/weather/places?q=tokyo`), 401, 'UNAUTHORIZED', '장소 검색 토큰 없이');
    if (ctx.external) {
      ctx.note('--external: 좌표 응답 값은 서버 공급자 설정에 따라 달라 401만 확인');
      return;
    }

    const user = await rawSignup('weather');
    // 남반구 바다 위 무작위 격자 (다른 실행과 겹치지 않게)
    const lat = -50 + Math.floor(Math.random() * 100) / 10;
    const lon = -150 + Math.floor(Math.random() * 1000) / 10;
    const res = await user.call('GET', `/weather/day?lat=${lat}&lon=${lon}&date=${today}`);
    eq(res.status, 200, '로그인하면 200');
    eq(Object.keys(res.json).sort(), DAY_KEYS, '응답 키');
    eq(res.json.source, 'FORECAST', '오늘은 예보 (요청 때 한 번 받음)');
    eq(res.json.place.regionCode, null, '좌표 장소는 regionCode null');
    eq(res.json.place.lat, Math.round(lat * 10) / 10, 'place.lat은 격자(소수 첫째 자리)');
    eq(res.json.place.lon, Math.round(lon * 10) / 10, 'place.lon은 격자');

    const far = await user.call('GET', `/weather/day?lat=${lat}&lon=${lon}&date=${seoulDate(60)}`);
    eq(far.status, 200, '60일 뒤 200');
    eq(far.json.source, 'CLIMATE', '예보 밖은 그 격자의 평년값');
    eq(far.json.place.regionCode, null, 'CLIMATE도 regionCode null');

    const search = await user.call('GET', `/weather/places?q=${encodeURIComponent('도쿄')}`);
    eq(search.status, 200, '검색 200');
    eq(Object.keys(search.json), ['items'], '검색 응답 키');
    const item = search.json.items[0];
    eq(Object.keys(item).sort(), ['country', 'label', 'lat', 'lon', 'region', 'regionCode'], '검색 항목 키');
    eq([item.label, item.country, item.regionCode], ['도쿄', '일본', null], '목업 검색 결과는 한글 이름 (해외는 regionCode null)');
    const busan = await user.call('GET', '/weather/places?q=busan');
    eq([busan.json.items[0].label, busan.json.items[0].regionCode], ['부산', 'BUSAN'], '영어 검색어도 한글 이름, 국내 결과는 30km 안 REGION 코드');
    const empty = await user.call('GET', `/weather/places?q=${encodeURIComponent('   ')}`);
    checkErrorBody(empty, 400, 'VALIDATION_FAILED', '빈 검색어');
    eq(empty.json.fieldErrors, [{ field: 'q', code: 'REQUIRED' }], '빈 검색어 fieldErrors: q REQUIRED');
    ctx.note(`격자 ${res.json.place.lat},${res.json.place.lon} 예보 마지막 날 ${res.json.forecastUntil}`);
  },
};

const dateValidation: Scenario = {
  group: '장소 날씨',
  name: 'GET /weather/day 날짜 검증: 없음·형식·과거·1년 넘음 → 400 VALIDATION_FAILED(date), 없는 지역 → 400(region)',
  async run() {
    const cases: [string, string, string][] = [
      ['region=SEOUL', '날짜 없음', 'REQUIRED'],
      ['region=SEOUL&date=2026-13-01', '없는 달', 'INVALID_FORMAT'],
      ['region=SEOUL&date=20261020', '형식', 'INVALID_FORMAT'],
      [`region=SEOUL&date=${seoulDate(-1)}`, '어제', 'OUT_OF_RANGE'],
      [`region=SEOUL&date=${seoulDate(367)}`, '1년 넘음', 'OUT_OF_RANGE'],
    ];
    for (const [q, label, code] of cases) {
      const res = await raw('GET', `/weather/day?${q}`);
      checkErrorBody(res, 400, 'VALIDATION_FAILED', label);
      eq(res.json.fieldErrors.map((e: { field: string }) => e.field), ['date'], `${label}: fieldErrors`);
      eq(res.json.fieldErrors[0].code, code, `${label}: 필드 코드`);
    }
    const region = await raw('GET', `/weather/day?region=NOWHERE&date=${seoulDate()}`);
    checkErrorBody(region, 400, 'VALIDATION_FAILED', '없는 지역');
    eq(region.json.fieldErrors, [{ field: 'region', code: 'NOT_FOUND' }], '없는 지역 fieldErrors: region NOT_FOUND');
  },
};

export const weatherScenarios: Scenario[] = [regionDay, coordinateDay, dateValidation];
