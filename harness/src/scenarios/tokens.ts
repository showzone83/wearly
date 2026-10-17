// Access 만료 자동 갱신·single-flight·Refresh 회전과 재사용 감지
import { eq, expectApiError, fail, isNonEmptyString, ok } from '../lib/assert';
import { WAIT_FOR_EXPIRY_MS } from '../lib/config';
import { sleep } from '../lib/fixtures';
import { checkErrorBody, raw } from '../lib/http';
import { signedUp, tokensOf } from '../lib/session';
import type { Scenario } from '../scenario';

const autoRefresh: Scenario = {
  group: '토큰',
  name: 'Access 만료(4.5초 대기) → 자동 갱신 후 성공, 저장소 토큰 교체',
  needs: ['shortTtl'],
  async run() {
    const u = await signedUp('ttl');
    const t0 = await tokensOf(u);
    await sleep(WAIT_FOR_EXPIRY_MS);

    // 서버가 만료를 TOKEN_EXPIRED로 알려야 앱이 갱신한다
    const expired = await raw('GET', '/users/me', { token: t0.accessToken });
    checkErrorBody(expired, 401, 'TOKEN_EXPIRED', '만료된 access 원시 요청');

    const mark = u.mark();
    const setBefore = u.counts.set;
    const me = await u.api.users.me();
    eq(me.email, u.email, '갱신 뒤 users.me()');
    eq(u.refreshCallsSince(mark), 1, '/auth/refresh 호출 횟수');
    eq(u.counts.set - setBefore, 1, 'tokenStore.set 호출 횟수');
    eq(u.counts.authLost, 0, 'onAuthLost 호출 없음');

    const t1 = await tokensOf(u);
    ok(t1.accessToken !== t0.accessToken, 'access 토큰이 바뀜');
    ok(t1.refreshToken !== t0.refreshToken, 'refresh 토큰도 회전됨');

    // 새 토큰으로 바로 한 번 더: 추가 갱신 없음
    const mark2 = u.mark();
    await u.api.wardrobe.stats();
    eq(u.refreshCallsSince(mark2), 0, '갱신 직후 요청은 refresh 없이 성공');
  },
};

const singleFlight: Scenario = {
  group: '토큰',
  name: '만료 상태에서 동시 요청 5개 → refresh 1번 (single-flight)',
  needs: ['shortTtl'],
  async run() {
    const u = await signedUp('flight');
    const t0 = await tokensOf(u);
    await sleep(WAIT_FOR_EXPIRY_MS);

    const mark = u.mark();
    const setBefore = u.counts.set;
    const results = await Promise.allSettled([
      u.api.users.me(),
      u.api.profile.get(),
      u.api.wardrobe.list(),
      u.api.wardrobe.stats(),
      u.api.users.me(),
    ]);
    const failed = results.flatMap((r, i) => (r.status === 'rejected' ? [`#${i + 1}: ${String(r.reason)}`] : []));
    eq(failed, [], '5개 요청 모두 성공');
    eq(u.refreshCallsSince(mark), 1, '/auth/refresh 호출 횟수');
    eq(u.counts.set - setBefore, 1, 'tokenStore.set 호출 횟수');
    eq(u.counts.authLost, 0, 'onAuthLost 호출 없음');
    ok((await tokensOf(u)).refreshToken !== t0.refreshToken, 'refresh 토큰 회전');
  },
};

const rotation: Scenario = {
  group: '토큰',
  name: 'refresh 회전, 이전 토큰 재사용 → 401 REFRESH_REUSED, family 전체 폐기',
  async run(ctx) {
    const u = await signedUp('rotate');
    const t0 = await tokensOf(u);

    const r1 = await raw('POST', '/auth/refresh', { body: { refreshToken: t0.refreshToken } });
    eq(r1.status, 200, '첫 회전 상태');
    const t1 = r1.json;
    ok(isNonEmptyString(t1?.accessToken) && isNonEmptyString(t1?.refreshToken), '회전 응답은 AuthResponse', t1);
    ok(t1.refreshToken !== t0.refreshToken, '새 refresh 토큰 발급');
    eq(t1.user?.email, u.email, '회전 응답 user');

    // 이미 회전된 R0 재사용 → 같은 family 전체 폐기
    const reused = await raw('POST', '/auth/refresh', { body: { refreshToken: t0.refreshToken } });
    checkErrorBody(reused, 401, 'REFRESH_REUSED', '회전된 R0 재사용');

    // 최신 R1도 함께 무효
    const latest = await raw('POST', '/auth/refresh', { body: { refreshToken: t1.refreshToken } });
    checkErrorBody(latest, 401, ['REFRESH_INVALID', 'REFRESH_REUSED'], 'family 폐기 뒤 최신 R1');
    ctx.note(`family 폐기 뒤 최신 refresh 토큰 응답 코드 = ${latest.json?.code}`);

    const garbage = await raw('POST', '/auth/refresh', { body: { refreshToken: 'no-such-token' } });
    checkErrorBody(garbage, 401, 'REFRESH_INVALID', '없는 refresh 토큰');

    const missing = await raw('POST', '/auth/refresh', { body: {} });
    if (missing.status !== 400 && missing.status !== 401) fail('refreshToken 누락', '400 또는 401', missing.status);
  },
};

const authLost: Scenario = {
  group: '토큰',
  name: '재사용 감지로 family 폐기 뒤 만료 → 앱 갱신 실패, 저장소 비움, onAuthLost 1번',
  needs: ['shortTtl'],
  async run() {
    const u = await signedUp('lost');
    const t0 = await tokensOf(u);
    // 다른 기기가 R0를 훔쳐 먼저 회전하고, 다시 R0를 쓴 상황
    const stolen = await raw('POST', '/auth/refresh', { body: { refreshToken: t0.refreshToken } });
    eq(stolen.status, 200, '탈취 측 회전');
    const reuse = await raw('POST', '/auth/refresh', { body: { refreshToken: t0.refreshToken } });
    checkErrorBody(reuse, 401, 'REFRESH_REUSED', 'R0 재사용');

    await sleep(WAIT_FOR_EXPIRY_MS);
    const mark = u.mark();
    await expectApiError(u.api.users.me(), { status: 401, code: 'TOKEN_EXPIRED' }, '갱신 실패 뒤 원래 오류(TOKEN_EXPIRED)');
    eq(u.refreshCallsSince(mark), 1, '/auth/refresh 시도 1번');
    eq(u.counts.authLost, 1, 'onAuthLost 호출 횟수');
    eq(await u.store.get(), null, '저장소 비움');

    // 다시 호출하면 토큰 없이 나가 UNAUTHORIZED로 끝난다
    await expectApiError(u.api.users.me(), { status: 401, code: 'UNAUTHORIZED' }, '저장소 빈 뒤 재호출');

    // 탈취 측 토큰(R1)도 무효
    const thief = await raw('POST', '/auth/refresh', { body: { refreshToken: stolen.json.refreshToken } });
    checkErrorBody(thief, 401, ['REFRESH_INVALID', 'REFRESH_REUSED'], '탈취 측 R1');
  },
};

export const tokenScenarios: Scenario[] = [rotation, autoRefresh, singleFlight, authLost];
