// 관리자 2단계 인증 TOTP (doc/22). 하네스 서버는 필수를 끄고(WEARLY_ADMIN_MFA_REQUIRED=false) 마스터 키를 켠다(run.sh).
// 하네스 관리자는 그대로 비밀번호만으로 쓰고, 이 시나리오는 새 회원을 관리자로 지정해 스스로 TOTP를 등록한 뒤
// 관리자 사이트 로그인(대기 토큰 → 코드·복구 코드 확인)을 확인한다.
// 하네스 서버는 Access 수명이 3초라서 관리자 API는 Refresh로 갱신하며 부른다(갱신해도 같은 세션이라 2단계 인증 표시가 남는다).
import { createHmac } from 'node:crypto';
import { eq, ok } from '../lib/assert';
import { checkErrorBody, raw, type RawResponse } from '../lib/http';
import { rawAdmin, rawSignup } from '../lib/rawUser';
import type { Scenario } from '../scenario';

/** rawUser.ts와 같은 비밀번호 */
const PASSWORD = 'Harness1234';
const SITE = { 'X-Wearly-Client': 'admin' };

/** RFC 6238 TOTP (HMAC-SHA1, 6자리, 30초) — 인증 앱 역할 */
function totp(base32: string, step: number): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const bytes: number[] = [];
  let buffer = 0;
  let bits = 0;
  for (const ch of base32.toUpperCase()) {
    buffer = (buffer << 5) | alphabet.indexOf(ch);
    bits += 5;
    if (bits >= 8) {
      bytes.push((buffer >> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const h = createHmac('sha1', Buffer.from(bytes)).update(counter).digest();
  const o = h[h.length - 1] & 0x0f;
  const bin = ((h[o] & 0x7f) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3];
  return String(bin % 1_000_000).padStart(6, '0');
}

const nowStep = (): number => Math.floor(Date.now() / 1000 / 30);

/** 토큰 한 쌍. 만료(TOKEN_EXPIRED)면 Refresh로 갱신하고 한 번 더 부른다 */
function session(first: { accessToken: string; refreshToken: string }) {
  let tokens = first;
  return {
    get access() {
      return tokens.accessToken;
    },
    async call(method: string, path: string, body?: unknown): Promise<RawResponse> {
      let res = await raw(method, path, { token: tokens.accessToken, body });
      if (res.status === 401 && res.json?.code === 'TOKEN_EXPIRED') {
        const r = await raw('POST', '/auth/refresh', { body: { refreshToken: tokens.refreshToken } });
        eq(r.status, 200, 'Refresh 갱신 200');
        tokens = { accessToken: r.json.accessToken, refreshToken: r.json.refreshToken };
        res = await raw(method, path, { token: tokens.accessToken, body });
      }
      return res;
    },
  };
}

const enrollAndVerify: Scenario = {
  group: '관리자',
  name: '2단계 인증: 지정한 관리자가 TOTP 등록 → 관리자 사이트 로그인은 대기 토큰 → 코드·복구 코드 확인, 앱 세션은 403 MFA_REQUIRED',
  needs: ['admin'],
  async run(ctx) {
    const admin = await rawAdmin();
    const u = await rawSignup('mfa');
    eq((await admin.call('PATCH', `/admin/users/${u.id}`, { body: { role: 'ADMIN' } })).status, 200, '관리자로 지정');

    // 필수가 꺼져 있고 등록하지 않았으면 관리자 사이트 로그인도 바로 토큰
    const first = await raw('POST', '/auth/login', { body: { email: u.email, password: PASSWORD }, headers: SITE });
    eq(first.status, 200, '관리자 사이트 로그인 200');
    ok(typeof first.json.accessToken === 'string' && first.json.mfa === undefined, '미등록·필수 꺼짐: 바로 토큰', first.json);
    const me = session(first.json);
    const status = await me.call('GET', '/admin/me/mfa');
    eq(status.status, 200, '내 2단계 인증 상태 200');
    eq([status.json.enrolled, status.json.required], [false, false], '미등록 · 서버 필수 꺼짐');

    // 스스로 등록: setup → enable(TOTP)
    const setup = await me.call('POST', '/admin/me/mfa/setup');
    eq(setup.status, 200, '등록 시작 200');
    const secret: string = setup.json.secret;
    ok(/^[A-Z2-7]{32}$/.test(secret) && String(setup.json.otpauthUrl).startsWith('otpauth://totp/'), 'secret·otpauthUrl 형식', setup.json);
    checkErrorBody(await me.call('POST', '/admin/me/mfa/enable', { code: '12ab' }), 400, 'VALIDATION_FAILED', '코드 형식 오류');
    const step = nowStep();
    const enabled = await me.call('POST', '/admin/me/mfa/enable', { code: totp(secret, step) });
    eq(enabled.status, 200, '등록 확인 200');
    const recovery: string[] = enabled.json.recoveryCodes;
    ok(Array.isArray(recovery) && recovery.length === 10 && recovery.every((c) => /^[a-z2-9]{5}-[a-z2-9]{5}$/.test(c)), '복구 코드 10개', recovery);
    checkErrorBody(await me.call('POST', '/admin/me/mfa/setup'), 409, 'MFA_ALREADY_ENROLLED', '등록 뒤 다시 시작');

    // 관리자 사이트 로그인: 이제 대기 토큰
    const pending = await raw('POST', '/auth/login', { body: { email: u.email, password: PASSWORD }, headers: SITE });
    eq(pending.status, 200, '등록 뒤 관리자 사이트 로그인 200');
    eq([pending.json.mfa?.required, pending.json.mfa?.enrolled, pending.json.mfaTokenExpiresIn], [true, true, 300], '대기 응답 mfa·수명');
    ok(pending.json.accessToken === undefined && typeof pending.json.mfaToken === 'string', '대기 응답에는 토큰이 없다', pending.json);
    const mfaToken: string = pending.json.mfaToken;
    checkErrorBody(await raw('GET', '/admin/dashboard', { token: mfaToken }), 401, 'TOKEN_INVALID', '대기 토큰으로 관리자 API');

    // 같은 단계 코드는 재사용이라 거부, 복구 코드는 한 번만
    const reused = await raw('POST', '/auth/mfa/verify', { body: { mfaToken, code: totp(secret, step) }, headers: SITE });
    checkErrorBody(reused, 400, 'MFA_CODE_INVALID', '같은 코드 재사용');
    eq(reused.json.params?.remainingAttempts, 4, '남은 횟수 4');
    const verified = await raw('POST', '/auth/mfa/verify', { body: { mfaToken, recoveryCode: recovery[0] }, headers: SITE });
    eq(verified.status, 200, '복구 코드로 확인 200');
    eq([verified.json.recoveryCodes, verified.json.recoveryCodesLeft, verified.json.user?.role], [null, 9, 'ADMIN'], '복구 코드 응답');
    const site = session(verified.json);
    eq((await site.call('GET', '/admin/dashboard')).status, 200, '확인한 세션으로 관리자 API 200');
    checkErrorBody(
      await raw('POST', '/auth/mfa/verify', { body: { mfaToken, code: totp(secret, step + 1) }, headers: SITE }),
      401,
      'MFA_TOKEN_INVALID',
      '쓴 대기 토큰 다시',
    );

    // 앱 로그인 세션(2단계 인증 없음)은 관리자 API만 403
    const app = await raw('POST', '/auth/login', { body: { email: u.email, password: PASSWORD } });
    eq(app.status, 200, '앱 로그인 200 (응답 모양 그대로)');
    checkErrorBody(await raw('GET', '/admin/dashboard', { token: app.json.accessToken }), 403, 'MFA_REQUIRED', '앱 세션으로 관리자 API');
    eq((await raw('GET', '/users/me', { token: app.json.accessToken })).status, 200, '앱 세션으로 앱 API는 200');

    // 로그인 기록
    const events = await site.call('GET', `/admin/auth-events?userId=${u.id}&size=100`);
    eq(events.status, 200, '관리자 로그인 기록 200');
    const types = (events.json.items as { type: string }[]).map((e) => e.type);
    ok(['MFA_SUCCESS', 'MFA_FAILED', 'LOGIN'].every((t) => types.includes(t)), '로그인 기록에 MFA 성공·실패·로그인', types);
    checkErrorBody(await site.call('GET', '/admin/auth-events?type=NOPE'), 400, 'VALIDATION_FAILED', '모르는 종류');

    // 하네스 관리자가 초기화 → 그 관리자의 세션이 끊긴다 → 역할 해제로 정리
    eq((await admin.call('DELETE', `/admin/users/${u.id}/mfa`)).status, 204, '다른 관리자의 2단계 인증 초기화 204');
    checkErrorBody(await raw('GET', '/admin/dashboard', { token: site.access }), 401, ['TOKEN_INVALID', 'TOKEN_EXPIRED'], '초기화된 관리자의 세션');
    eq((await admin.call('PATCH', `/admin/users/${u.id}`, { body: { role: 'USER' } })).status, 200, '관리자 해제');
    ctx.note(`MFA 시나리오 회원 ${u.id}: 등록·복구 코드·초기화·해제까지 확인`);
  },
};

export const mfaScenarios: Scenario[] = [enrollAndVerify];
