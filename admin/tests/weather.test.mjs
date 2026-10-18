// 순수 함수 단위 테스트: 날씨 관리 화면(src/lib/weather.ts).
// 실행: npm run test:unit
import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  estimateMonthlyCalls,
  fmtTemp,
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
