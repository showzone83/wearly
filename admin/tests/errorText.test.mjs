// 오류 문구 표(src/lib/errorText.ts)와 서버 오류 코드 카탈로그(backend/src/main/resources/error-codes.json)가 맞는지,
// params 해석이 맞는지 확인한다. 실행: npm run test:unit
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

import {
  apiKeyReasonText,
  CLIENT_ERROR_TEXT,
  codeUsagesText,
  errorText,
  errorTextWithTrace,
  FIELD_ERROR_TEXT,
  fieldErrorText,
  MFA_SECRET_KEY_MISSING_TEXT,
  remainingAttemptsText,
  retryAfterMinutes,
  SERVER_ERROR_TEXT,
  shortTraceId,
  statusText,
  systemTraceId,
} from '../src/lib/errorText.ts';

const catalog = JSON.parse(
  readFileSync(new URL('../../backend/src/main/resources/error-codes.json', import.meta.url), 'utf8'),
);
const has = (obj, key) => Object.prototype.hasOwnProperty.call(obj, key);

test('카탈로그의 업무 오류 코드가 모두 표에 있다 (관리자와 무관한 코드는 null로 명시)', () => {
  const missing = catalog.errorCodes.map((e) => e.code).filter((c) => !has(SERVER_ERROR_TEXT, c));
  assert.deepEqual(missing, [], `errorText.ts의 SERVER_ERROR_TEXT에 없는 코드: ${missing.join(', ')}`);
});

test('표에 카탈로그에 없는 서버 코드가 없다 (지워진 코드 정리)', () => {
  const codes = new Set(catalog.errorCodes.map((e) => e.code));
  const extra = Object.keys(SERVER_ERROR_TEXT).filter((c) => !codes.has(c));
  assert.deepEqual(extra, [], `카탈로그에 없는 코드: ${extra.join(', ')}`);
});

test('사이트가 만드는 코드는 카탈로그 코드와 겹치지 않는다', () => {
  const codes = new Set(catalog.errorCodes.map((e) => e.code));
  for (const c of Object.keys(CLIENT_ERROR_TEXT)) assert.ok(!codes.has(c), `${c}는 서버 코드와 겹친다`);
});

test('카탈로그의 필드 오류 코드가 모두 표에 있고, 남는 코드가 없다', () => {
  const codes = catalog.fieldErrorCodes.map((e) => e.code);
  const missing = codes.filter((c) => !has(FIELD_ERROR_TEXT, c));
  assert.deepEqual(missing, [], `FIELD_ERROR_TEXT에 없는 필드 코드: ${missing.join(', ')}`);
  const extra = Object.keys(FIELD_ERROR_TEXT).filter((c) => !codes.includes(c));
  assert.deepEqual(extra, [], `카탈로그에 없는 필드 코드: ${extra.join(', ')}`);
});

test('카탈로그의 모든 코드는 빈 문구가 아니고 {이름} 자리가 남지 않는다 (params 없이도)', () => {
  for (const e of catalog.errorCodes) {
    const t = errorText({ status: e.status, code: e.code });
    assert.ok(t.trim(), e.code);
    assert.ok(!/\{\w+\}/.test(t), `${e.code}: ${t}`);
  }
  for (const f of catalog.fieldErrorCodes) {
    for (const field of f.fields.length ? f.fields : ['x']) {
      const t = fieldErrorText({ field, code: f.code });
      assert.ok(t.trim(), `${f.code}:${field}`);
      assert.ok(!/\{\w+\}|undefined|null/.test(t), `${f.code}:${field} → ${t}`);
    }
  }
});

test('관리자와 무관한 코드(null)는 상태별 기본 문구', () => {
  assert.equal(SERVER_ERROR_TEXT.WARDROBE_INSUFFICIENT, null);
  assert.equal(errorText({ status: 400, code: 'WARDROBE_INSUFFICIENT' }), statusText(400));
  assert.equal(errorText({ status: 429, code: 'WEATHER_LIMIT', params: { kind: 'SEARCH' } }), statusText(429));
});

test('모르는 코드는 상태별 기본 문구, 5xx는 일시적인 오류', () => {
  assert.equal(errorText({ status: 409, code: 'SOMETHING_NEW' }), statusText(409));
  assert.match(errorText({ status: 502, code: 'SOMETHING_NEW' }), /일시적인 오류/);
  assert.match(errorText({ status: 500, code: 'INTERNAL_ERROR' }), /일시적인 오류/);
  assert.match(errorText({ status: 418, code: 'X' }), /HTTP 418/);
  assert.match(errorText({ status: 0, code: 'NETWORK' }), /연결할 수 없어요/);
  assert.match(errorText({ status: 0, code: 'NETWORK', params: { reason: 'TIMEOUT' } }), /너무 늦어요/);
  // HTTP_ERROR는 상태 문구로 (PARSE만 따로)
  assert.equal(errorText({ status: 404, code: 'HTTP_ERROR' }), statusText(404));
  assert.match(errorText({ status: 200, code: 'HTTP_ERROR', params: { reason: 'PARSE' } }), /읽지 못했어요/);
});

test('SECRET_KEY_MISSING은 설정 키 이름까지 알려 준다', () => {
  const t = errorText({ status: 409, code: 'SECRET_KEY_MISSING' });
  assert.match(t, /WEARLY_SECRET_KEY/);
  assert.match(t, /wearly\.ai\.keys/);
});

test('2단계 인증 화면의 SECRET_KEY_MISSING은 AI 키가 아니라 마스터 키 형식을 안내한다', () => {
  assert.match(MFA_SECRET_KEY_MISSING_TEXT, /2단계 인증/);
  assert.match(MFA_SECRET_KEY_MISSING_TEXT, /WEARLY_SECRET_KEY/);
  assert.match(MFA_SECRET_KEY_MISSING_TEXT, /openssl rand -base64 32/);
  assert.doesNotMatch(MFA_SECRET_KEY_MISSING_TEXT, /API 키/);
});

test('API_KEY_INVALID.reason 해석', () => {
  assert.match(apiKeyReasonText('API_401'), /거절.*401/);
  assert.match(apiKeyReasonText('API_429'), /한도/);
  assert.match(apiKeyReasonText('API_503'), /서버 오류.*503/);
  assert.match(apiKeyReasonText('API_418'), /418/);
  assert.match(apiKeyReasonText('TIMEOUT'), /늦어요/);
  assert.match(apiKeyReasonText('BAD_RESPONSE'), /읽지 못했어요/);
  assert.match(apiKeyReasonText(undefined), /확인하지 못했어요/);
  assert.match(errorText({ status: 400, code: 'API_KEY_INVALID', params: { reason: 'API_403' } }), /403/);
});

test('CODE_IN_USE.usages 해석', () => {
  const usages = [
    { kind: 'ITEM', count: 12 },
    { kind: 'PROFILE', count: 3 },
    { kind: 'SUB_TYPE', count: 0 },
  ];
  assert.equal(codeUsagesText(usages), '옷 12개 · 회원 프로필 3개');
  assert.equal(codeUsagesText(undefined), '');
  const t = errorText({ status: 409, code: 'CODE_IN_USE', params: { usages } });
  assert.match(t, /\(옷 12개 · 회원 프로필 3개\)/);
  assert.match(t, /비활성화/);
  assert.doesNotMatch(errorText({ status: 409, code: 'CODE_IN_USE' }), /\(\)/);
});

test('날씨 관리자 코드: WEATHER_NO_PROVIDER.reason·keyName, WEATHER_REFRESH_RUNNING', () => {
  assert.match(
    errorText({
      status: 409,
      code: 'WEATHER_NO_PROVIDER',
      params: { reason: 'KEY_MISSING', keyName: 'WEATHERAPI_KEY' },
    }),
    /WEATHERAPI_KEY/,
  );
  assert.match(
    errorText({ status: 409, code: 'WEATHER_NO_PROVIDER', params: { reason: 'NOT_IMPLEMENTED' } }),
    /구현이 없어요/,
  );
  assert.match(errorText({ status: 409, code: 'WEATHER_NO_PROVIDER' }), /월 한도/);
  assert.match(errorText({ status: 409, code: 'WEATHER_REFRESH_RUNNING' }), /이미 받고 있어요/);
  assert.match(errorText({ status: 502, code: 'AI_UNAVAILABLE', params: { feature: 'ITEM_ANALYZE' } }), /사진 분석/);
});

test('2단계 인증 코드: MFA_CODE_INVALID.remainingAttempts → 남은 횟수', () => {
  assert.equal(
    errorText({ status: 400, code: 'MFA_CODE_INVALID', params: { remainingAttempts: 3 } }),
    '인증 코드가 맞지 않아요. (남은 횟수 3번)',
  );
  // 문자열로 와도 읽는다
  assert.match(
    errorText({ status: 400, code: 'MFA_CODE_INVALID', params: { remainingAttempts: '1' } }),
    /남은 횟수 1번/,
  );
  // params가 없으면 횟수 없이
  assert.equal(errorText({ status: 400, code: 'MFA_CODE_INVALID' }), '인증 코드가 맞지 않아요.');
  assert.equal(remainingAttemptsText(undefined), '');
  assert.equal(remainingAttemptsText(-1), '');
});

test('2단계 인증 잠김: MFA_LOCKED.retryAfterSeconds → 분 단위 올림', () => {
  assert.equal(retryAfterMinutes(900), 15);
  assert.equal(retryAfterMinutes(61), 2);
  assert.equal(retryAfterMinutes(5), 1);
  assert.equal(retryAfterMinutes(0), null);
  assert.equal(retryAfterMinutes(undefined), null);
  assert.match(
    errorText({ status: 429, code: 'MFA_LOCKED', params: { retryAfterSeconds: 840 } }),
    /잠시 잠겼어요\. 14분 뒤 다시 시도해 주세요\.$/,
  );
  assert.match(errorText({ status: 429, code: 'MFA_LOCKED' }), /잠시 후 다시 시도해 주세요/);
});

test('관리자 계정 코드: LAST_ADMIN·MFA_TOKEN_INVALID·MFA_REQUIRED, 필드별 문구', () => {
  assert.match(errorText({ status: 409, code: 'LAST_ADMIN' }), /마지막 관리자/);
  assert.match(errorText({ status: 401, code: 'MFA_TOKEN_INVALID' }), /처음부터 다시 로그인/);
  assert.match(errorText({ status: 403, code: 'MFA_REQUIRED' }), /2단계 인증/);
  assert.match(fieldErrorText({ field: 'role', code: 'CONFLICT' }), /정지된 회원/);
  assert.match(fieldErrorText({ field: 'newPassword', code: 'INVALID_FORMAT' }), /영문과 숫자/);
  assert.match(fieldErrorText({ field: 'id', code: 'SELF_NOT_ALLOWED' }), /자기 자신/);
});

test('필드 오류 params 해석', () => {
  assert.equal(fieldErrorText({ field: 'label', code: 'TOO_LONG', params: { max: 50 } }), '50자까지 쓸 수 있어요.');
  assert.equal(fieldErrorText({ field: 'apiKey', code: 'TOO_SHORT', params: { min: 20 } }), '20자 이상 입력해 주세요.');
  assert.match(
    fieldErrorText({ field: 'attrs.hex', code: 'INVALID_FORMAT', params: { format: '#RRGGBB' } }),
    /#RRGGBB/,
  );
  assert.match(fieldErrorText({ field: 'priority', code: 'INVALID_TYPE', params: { expected: 'integer' } }), /정수/);
  assert.match(
    fieldErrorText({ field: 'status', code: 'NOT_ALLOWED', params: { allowed: ['ACTIVE', 'SUSPENDED'] } }),
    /ACTIVE, SUSPENDED/,
  );
  assert.equal(fieldErrorText({ field: 'codes', code: 'INCOMPLETE', params: { expected: 7 } }).startsWith('7개'), true);
  assert.equal(fieldErrorText({ field: 'x', code: 'UNKNOWN_FIELD_CODE' }), '입력값을 확인해 주세요.');
  // 관리자 화면용 필드별 문구 (경로의 [n]은 지운다)
  assert.match(fieldErrorText({ field: 'attrs.pairs[1]', code: 'SELF_NOT_ALLOWED' }), /자기 색/);
  assert.match(fieldErrorText({ field: 'options.baseUrl', code: 'DISABLED_BY_SERVER' }), /allow-base-url/);
});

test('OUT_OF_RANGE: 숫자·한쪽만·날짜·사진 크기(바이트)', () => {
  assert.equal(
    fieldErrorText({ field: 'intervalMinutes', code: 'OUT_OF_RANGE', params: { min: 10, max: 1440 } }),
    '10~1440 사이 값이어야 해요.',
  );
  assert.equal(
    fieldErrorText({ field: 'monthlyQuota', code: 'OUT_OF_RANGE', params: { min: 0 } }),
    '0 이상이어야 해요.',
  );
  assert.equal(
    fieldErrorText({ field: 'date', code: 'OUT_OF_RANGE', params: { min: '2026-10-18', max: '2027-10-18' } }),
    '2026-10-18~2027-10-18 사이 날짜여야 해요.',
  );
  assert.equal(
    fieldErrorText({ field: 'size', code: 'OUT_OF_RANGE', params: { min: 1, max: 10 * 1024 * 1024 } }),
    '10MB까지 올릴 수 있어요.',
  );
  // 목록 쪽 크기(size)는 숫자 그대로
  assert.equal(
    fieldErrorText({ field: 'size', code: 'OUT_OF_RANGE', params: { min: 1, max: 100 } }),
    '1~100 사이 값이어야 해요.',
  );
  assert.equal(fieldErrorText({ field: 'workDays[0]', code: 'OUT_OF_RANGE' }), '허용 범위를 벗어났어요.');
});

test('VALIDATION_FAILED는 필드 오류를 덧붙인다', () => {
  const t = errorText({
    status: 400,
    code: 'VALIDATION_FAILED',
    fieldErrors: [
      { field: 'label', code: 'REQUIRED', params: {} },
      { field: 'sortOrder', code: 'INVALID_TYPE', params: { expected: 'integer' } },
    ],
  });
  assert.match(t, /^입력값을 확인해 주세요\. label: 필수 값이에요\. \/ sortOrder: /);
});

test('오류 번호: 시스템 오류(5xx)에만, traceId 앞 8자', () => {
  const traceId = 'ab12cd34ef5678900000000000000000';
  assert.equal(shortTraceId(traceId), 'ab12cd34');
  assert.equal(systemTraceId({ status: 500, code: 'INTERNAL_ERROR', traceId }), traceId);
  assert.equal(systemTraceId({ status: 503, code: 'SERVICE_UNAVAILABLE', traceId }), traceId);
  assert.equal(systemTraceId({ status: 409, code: 'CONFLICT', traceId }), null);
  assert.equal(systemTraceId({ status: 500, code: 'INTERNAL_ERROR' }), null);
  assert.match(errorTextWithTrace({ status: 500, code: 'INTERNAL_ERROR', traceId }), /\(오류 번호 ab12cd34\)$/);
  assert.doesNotMatch(errorTextWithTrace({ status: 400, code: 'CODE_EXISTS', traceId }), /오류 번호/);
});
