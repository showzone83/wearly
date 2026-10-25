// 로그아웃·회원 탈퇴(탈퇴 신청·취소, doc/27)·비밀번호 변경과 변경 주기(doc/28)
import { eq, expectApiError } from '../lib/assert';
import { item } from '../lib/fixtures';
import { checkErrorBody, raw } from '../lib/http';
import { newSession, signedUp, TEST_PASSWORD, tokensOf } from '../lib/session';
import type { Scenario } from '../scenario';

const logout: Scenario = {
  group: '계정',
  name: 'logout → 저장소 비움, 이전 refresh로 갱신 불가',
  async run() {
    const u = await signedUp('logout');
    const t0 = await tokensOf(u);
    await u.api.auth.logout();
    eq(await u.store.get(), null, 'logout 뒤 저장소 비움');
    eq(u.counts.authLost, 0, 'logout은 onAuthLost를 부르지 않음');

    const res = await raw('POST', '/auth/refresh', { body: { refreshToken: t0.refreshToken } });
    checkErrorBody(res, 401, ['REFRESH_INVALID'], '로그아웃한 refresh 토큰');

    const unknown = await raw('POST', '/auth/logout', { body: { refreshToken: 'no-such-token' } });
    eq(unknown.status, 204, '없는 토큰으로 logout도 204');

    // 같은 계정의 다른 세션은 영향 없음
    const other = newSession();
    await other.api.auth.login({ email: u.email, password: TEST_PASSWORD });
    const again = newSession();
    await again.api.auth.login({ email: u.email, password: TEST_PASSWORD });
    await again.api.auth.logout();
    eq((await other.api.users.me()).email, u.email, '다른 세션은 로그아웃 영향 없음');
    const ot = await tokensOf(other);
    const ref = await raw('POST', '/auth/refresh', { body: { refreshToken: ot.refreshToken } });
    eq(ref.status, 200, '다른 세션 refresh는 유효');
  },
};

const withdraw: Scenario = {
  group: '계정',
  name: '탈퇴 신청(사유 필수) → 기존 access·refresh 401, 다른 기기 onAuthLost → 로그인 403 ACCOUNT_WITHDRAWN(purgeAt) → 같은 이메일 재가입 막힘 → cancelWithdrawal 로그인 → 옷장·나의 정보 그대로',
  async run(ctx) {
    const u = await signedUp('bye');
    await u.api.wardrobe.create(item());
    await u.api.profile.put({
      name: '떠날사람',
      dressCodes: ['CASUAL'],
      moods: [],
      colors: [],
      workType: 'WEEKDAY',
      workDays: [1, 2, 3, 4, 5],
      workRule: true,
      offRule: true,
      notify: { morning: true, evening: true, dMinus1: true },
      // 나의 정보도 탈퇴 예정 동안 남고, 취소하면 그대로다
      ageRange: 'AGE_40',
      bodyType: 'UNKNOWN',
      heightCm: 180,
      fitPreference: 'SLIM',
      onboarded: true,
    });
    // 같은 계정으로 로그인해 둔 다른 기기
    const device2 = newSession();
    await device2.api.auth.login({ email: u.email, password: TEST_PASSWORD });
    const t0 = await tokensOf(u);

    // 사유는 꼭 고른다 (틀리면 아무것도 바뀌지 않는다)
    const bad = await expectApiError(u.api.users.withdraw({ reason: 'BORED' as never }), { status: 400, code: 'VALIDATION_FAILED' }, '모르는 사유');
    eq(bad.fieldErrors.map((f) => `${f.field}:${f.code}`), ['reason:NOT_ALLOWED'], '사유 값 오류');

    const out = await u.api.users.withdraw({ reason: 'HARD_TO_USE', reasonText: '하네스' });
    eq(await u.store.get(), null, '탈퇴 신청 뒤 저장소 비움');
    const days = (Date.parse(out.purgeAt) - Date.parse(out.requestedAt)) / 86_400_000;
    eq(Math.round(days), 30, '삭제 예정은 30일 뒤');

    const me = await raw('GET', '/users/me', { token: t0.accessToken });
    checkErrorBody(me, 401, ['TOKEN_INVALID', 'TOKEN_EXPIRED'], '탈퇴 신청 뒤 기존 access 토큰');
    if (me.json?.code === 'TOKEN_EXPIRED') {
      ctx.note('탈퇴 뒤 access 확인이 3초 TTL보다 늦어 TOKEN_EXPIRED로 판정됨 (TOKEN_INVALID 확인 못 함)');
    }
    const ref = await raw('POST', '/auth/refresh', { body: { refreshToken: t0.refreshToken } });
    checkErrorBody(ref, 401, 'REFRESH_INVALID', '탈퇴 신청 뒤 refresh 토큰');

    // 다른 기기의 앱 모듈: 토큰 → 오류, 저장소 비움, onAuthLost 1번
    await expectApiError(device2.api.users.me(), { status: 401, code: ['TOKEN_INVALID', 'TOKEN_EXPIRED'] }, '탈퇴 신청 뒤 다른 기기 요청');
    eq(await device2.store.get(), null, '탈퇴 신청 뒤 다른 기기 저장소 비움');
    eq(device2.counts.authLost, 1, '탈퇴 신청 뒤 다른 기기 onAuthLost 호출 횟수');

    // 비밀번호가 맞으면 탈퇴 예정 안내 (삭제 예정 시각)
    const blocked = await expectApiError(
      newSession().api.auth.login({ email: u.email, password: TEST_PASSWORD }),
      { status: 403, code: 'ACCOUNT_WITHDRAWN' },
      '탈퇴 예정 계정 로그인',
    );
    eq(blocked.params?.purgeAt, out.purgeAt, '안내의 삭제 예정 시각');
    await expectApiError(
      newSession().api.auth.signup({ email: u.email, password: TEST_PASSWORD, name: '새사람' }),
      { status: 409, code: 'EMAIL_TAKEN' },
      '유예 중 같은 이메일 재가입',
    );

    // 취소를 고르면 되돌리고 옷장·나의 정보를 그대로 쓴다
    const back = newSession();
    await back.api.auth.login({ email: u.email, password: TEST_PASSWORD, cancelWithdrawal: true });
    eq((await back.api.wardrobe.stats()).total, 1, '취소 뒤 옷장 그대로');
    const profile = await back.api.profile.get();
    eq([profile.ageRange, profile.heightCm, profile.onboarded], ['AGE_40', 180, true], '취소 뒤 나의 정보 그대로');
  },
};

const password: Scenario = {
  group: '계정',
  name: '비밀번호 변경 주기: 상태(90일 기한) → 같은 비밀번호 400 NOT_ALLOWED·틀린 비밀번호 403 → 변경 204 → 다른 기기 onAuthLost·옛 비밀번호 401·새 비밀번호 로그인 → 연장',
  async run() {
    const u = await signedUp('pw');
    const s0 = await u.api.password.status();
    eq([s0.due, s0.intervalDays], [false, 90], '가입 직후 상태');
    eq(Math.round((Date.parse(s0.dueAt ?? '') - Date.parse(s0.changedAt ?? '')) / 86_400_000), 90, '기한은 90일 뒤');

    const device2 = newSession();
    await device2.api.auth.login({ email: u.email, password: TEST_PASSWORD });

    const same = await expectApiError(
      u.api.password.change({ currentPassword: TEST_PASSWORD, newPassword: TEST_PASSWORD }),
      { status: 400, code: 'VALIDATION_FAILED' },
      '같은 비밀번호로 변경',
    );
    eq(same.fieldErrors.map((f) => `${f.field}:${f.code}`), ['newPassword:NOT_ALLOWED'], '같은 비밀번호 필드 오류');
    await expectApiError(
      u.api.password.change({ currentPassword: 'wrong1234', newPassword: 'newpass99' }),
      { status: 403, code: 'REAUTH_FAILED' },
      '지금 비밀번호 틀림',
    );

    await u.api.password.change({ currentPassword: TEST_PASSWORD, newPassword: 'newpass99' });
    eq((await u.api.users.me()).email, u.email, '바꾼 기기는 그대로');
    await expectApiError(device2.api.users.me(), { status: 401, code: ['TOKEN_INVALID', 'TOKEN_EXPIRED'] }, '다른 기기 요청');
    eq(device2.counts.authLost, 1, '다른 기기 onAuthLost 호출 횟수');
    await expectApiError(
      newSession().api.auth.login({ email: u.email, password: TEST_PASSWORD }),
      { status: 401, code: 'INVALID_CREDENTIALS' },
      '옛 비밀번호 로그인',
    );
    const again = newSession();
    await again.api.auth.login({ email: u.email, password: 'newpass99' });

    const later = await again.api.password.postpone();
    eq(later.due, false, '연장 뒤 기한 전');
    const days = (Date.parse(later.dueAt ?? '') - Date.now()) / 86_400_000;
    eq(Math.round(days), 90, '연장하면 지금부터 90일');
  },
};

export const accountScenarios: Scenario[] = [logout, withdraw, password];
