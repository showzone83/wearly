// 순수 함수 단위 테스트: 날씨 관리 화면(src/lib/weather.ts).
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  callKindOf,
  callKindText,
  callRegionText,
  estimateMonthlyCalls,
  fmtTemp,
  gridKeyText,
  parseGridKey,
  runDurationMs,
  statusDetail,
  usageLevel,
  usagePercent,
} from '../src/lib/weather.ts';

test('예상 월 호출 수: 17개 지역 × 1회 × 30분 주기 = 하루 816회, 한 달 24,480회', () => {
  assert.equal(estimateMonthlyCalls(17, 1, 30), 24_480);
  assert.equal(estimateMonthlyCalls(17, 1, 60), 12_240);
  assert.equal(estimateMonthlyCalls(17, 2, 30), 48_960);
  assert.equal(estimateMonthlyCalls(0, 1, 30), 0);
  assert.equal(estimateMonthlyCalls(17, 1, 0), 0);
});

test('사용량 구간: 80% 주황, 100% 빨강, 한도 없음', () => {
  assert.equal(usageLevel(10, 100), 'ok');
  assert.equal(usageLevel(79, 100), 'ok');
  assert.equal(usageLevel(80, 100), 'warn');
  assert.equal(usageLevel(100, 100), 'over');
  assert.equal(usageLevel(150, 100), 'over');
  assert.equal(usageLevel(0, 0), 'over');
  assert.equal(usageLevel(5000, null), 'none');
});

test('사용량 막대 너비는 0~100', () => {
  assert.equal(usagePercent(50, 200), 25);
  assert.equal(usagePercent(500, 200), 100);
  assert.equal(usagePercent(5, null), 0);
  assert.equal(usagePercent(5, 0), 100);
});

test('공급자 상태 설명: 키 없음이 먼저, 정상이면 없음', () => {
  assert.match(statusDetail('OK', 'MISSING', null), /키가 없어/);
  assert.match(statusDetail('KEY_INVALID', 'CONFIGURED', null), /차단 해제/);
  assert.match(statusDetail('OPEN', 'CONFIGURED', '14:30'), /14:30까지/);
  assert.match(statusDetail('HALF_OPEN', 'CONFIGURED', null), /다시 시험/);
  assert.equal(statusDetail('OK', 'CONFIGURED', null), null);
  assert.equal(statusDetail('OK', 'MOCK', null), null);
});

test('기온·소요 시간 표시', () => {
  assert.equal(fmtTemp(18), '18°');
  assert.equal(fmtTemp(18.25), '18.3°');
  assert.equal(fmtTemp(-2.5), '-2.5°');
  assert.equal(fmtTemp(null), '—');
  assert.equal(runDurationMs('2026-10-17T00:00:00.000Z', '2026-10-17T00:00:02.500Z'), 2500);
  assert.equal(runDurationMs('2026-10-17T00:00:00.000Z', null), null);
});

test('호출 종류: callKind가 없으면 실행이 있을 때만 FORECAST, 이름은 표에서', () => {
  assert.equal(callKindOf({ callKind: 'SEARCH', runId: null }), 'SEARCH');
  assert.equal(callKindOf({ callKind: null, runId: '12' }), 'FORECAST');
  assert.equal(callKindOf({ callKind: '', runId: null }), '');
  assert.equal(callKindText('FORECAST'), '정기·수동 실행');
  assert.equal(callKindText('ON_DEMAND'), '다른 장소 날씨');
  assert.equal(callKindText('SEARCH'), '장소 검색');
  assert.equal(callKindText('CLIMATE'), '평년값');
  assert.equal(callKindText('NEW_KIND'), 'NEW_KIND');
  assert.equal(callKindText(''), '—');
});

test('격자 키 → 좌표 표시, 지역 칸', () => {
  assert.deepEqual(parseGridKey('35.7,139.7'), { lat: 35.7, lon: 139.7 });
  assert.deepEqual(parseGridKey('-33.9, 151.2'), { lat: -33.9, lon: 151.2 });
  assert.equal(parseGridKey('SEOUL'), null);
  assert.equal(parseGridKey('91,0'), null);
  assert.equal(gridKeyText('37.6,127.0'), '좌표 37.6, 127.0');
  assert.equal(gridKeyText('SEOUL'), null);
  const label = (code) => (code === 'SEOUL' ? '서울' : code);
  assert.equal(callRegionText({ callKind: 'FORECAST', regionCode: 'SEOUL' }, label), '서울');
  assert.equal(callRegionText({ callKind: 'ON_DEMAND', regionCode: '35.7,139.7' }, label), '좌표 35.7, 139.7');
  assert.equal(callRegionText({ callKind: 'CLIMATE', regionCode: '37.6,127.0' }, label), '좌표 37.6, 127.0');
  assert.equal(callRegionText({ callKind: 'SEARCH', regionCode: null }, label), '—');
  assert.equal(callRegionText({ callKind: 'SEARCH', regionCode: 'SEOUL' }, label), '—');
});
