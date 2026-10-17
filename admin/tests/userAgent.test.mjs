// 순수 함수 단위 테스트: User-Agent 요약(src/lib/userAgent.ts), 상대 시간(src/lib/format.ts의 fmtRelative).
// 실행: npm run test:unit  (Node 22.18+는 .ts의 타입을 지우고 바로 읽는다)
import assert from 'node:assert/strict';
import { test } from 'node:test';

import { fmtRelative } from '../src/lib/format.ts';
import { summarizeUserAgent } from '../src/lib/userAgent.ts';

const label = (ua) => summarizeUserAgent(ua).label;

test('User-Agent 요약', () => {
  // 없음
  assert.equal(label(null), '—');
  assert.equal(label('  '), '—');
  // Expo Go (iOS·Android)
  assert.equal(label('Expo/2.32.17 CFNetwork/1568.100.1 Darwin/24.0.0'), 'iOS · Expo Go');
  assert.equal(label('Exponent/2.31.2 (Android 14; Pixel 8)'), 'Android · Expo Go');
  // React Native 기본 UA
  assert.equal(label('Wearly/1 CFNetwork/1568.100.1 Darwin/24.0.0'), 'iOS · Wearly 앱');
  assert.equal(label('okhttp/4.12.0'), 'Android · 앱(okhttp)');
  assert.equal(label('SomeApp/3 CFNetwork/1568 Darwin/24.0.0'), 'iOS · 앱(CFNetwork)');
  // 브라우저
  assert.equal(
    label(
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36',
    ),
    'Mac · Chrome',
  );
  assert.equal(
    label(
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Safari/605.1.15',
    ),
    'Mac · Safari',
  );
  assert.equal(
    label(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0',
    ),
    'Windows · Edge',
  );
  assert.equal(
    label(
      'Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1',
    ),
    'iOS · Safari',
  );
  assert.equal(
    label(
      'Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/141.0 Mobile/15E148 Safari/604.1',
    ),
    'iOS · Chrome',
  );
  assert.equal(
    label(
      'Mozilla/5.0 (Linux; Android 14; SM-S921N) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/27.0 Chrome/125.0 Mobile Safari/537.36',
    ),
    'Android · 삼성 인터넷',
  );
  assert.equal(label('Mozilla/5.0 (X11; Linux x86_64; rv:139.0) Gecko/20100101 Firefox/139.0'), 'Linux · Firefox');
  // 도구
  assert.equal(label('curl/8.7.1'), 'curl');
  assert.equal(label('node'), 'Node');
  // 모르는 값은 원문(40자까지)
  assert.equal(label('mystery-client'), 'mystery-client');
  assert.equal(label('x'.repeat(50)), `${'x'.repeat(40)}…`);
  assert.deepEqual(summarizeUserAgent('okhttp/4.12.0'), {
    os: 'Android',
    agent: '앱(okhttp)',
    label: 'Android · 앱(okhttp)',
  });
});

test('상대 시간', () => {
  const now = Date.parse('2026-10-17T12:00:00Z');
  const ago = (ms) => new Date(now - ms).toISOString();
  assert.equal(fmtRelative(null, now), '—');
  assert.equal(fmtRelative('not-a-date', now), 'not-a-date');
  assert.equal(fmtRelative(ago(-5_000), now), '방금 전'); // 서버 시계가 조금 빠른 경우
  assert.equal(fmtRelative(ago(59_000), now), '방금 전');
  assert.equal(fmtRelative(ago(3 * 60_000), now), '3분 전');
  assert.equal(fmtRelative(ago(59 * 60_000 + 59_000), now), '59분 전');
  assert.equal(fmtRelative(ago(2 * 3_600_000), now), '2시간 전');
  assert.equal(fmtRelative(ago(5 * 86_400_000), now), '5일 전');
  assert.equal(fmtRelative('2026-08-01T03:00:00Z', now), '2026. 08. 01.');
});
