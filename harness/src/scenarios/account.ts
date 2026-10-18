// 로그아웃·회원 탈퇴
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

const deleteMe: Scenario = {
  group: '계정',
  name: '회원 탈퇴 → 기존 access 401, refresh 401, 로그인 INVALID_CREDENTIALS, 같은 이메일 재가입 가능',
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
      // 나의 정보도 탈퇴하면 프로필과 함께 지워진다
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

    await u.api.users.deleteMe();
    eq(await u.store.get(), null, 'deleteMe 뒤 저장소 비움');

    const me = await raw('GET', '/users/me', { token: t0.accessToken });
    checkErrorBody(me, 401, ['TOKEN_INVALID', 'TOKEN_EXPIRED'], '탈퇴 뒤 기존 access 토큰');
    if (me.json?.code === 'TOKEN_EXPIRED') {
      ctx.note('탈퇴 뒤 access 확인이 3초 TTL보다 늦어 TOKEN_EXPIRED로 판정됨 (TOKEN_INVALID 확인 못 함)');
    }

    const ref = await raw('POST', '/auth/refresh', { body: { refreshToken: t0.refreshToken } });
    checkErrorBody(ref, 401, 'REFRESH_INVALID', '탈퇴 뒤 refresh 토큰');

    // 다른 기기의 앱 모듈: 탈퇴한 회원 토큰 → 오류, 저장소 비움, onAuthLost 1번
    await expectApiError(device2.api.users.me(), { status: 401, code: ['TOKEN_INVALID', 'TOKEN_EXPIRED'] }, '탈퇴 뒤 다른 기기 요청');
    eq(await device2.store.get(), null, '탈퇴 뒤 다른 기기 저장소 비움');
    eq(device2.counts.authLost, 1, '탈퇴 뒤 다른 기기 onAuthLost 호출 횟수');

    await expectApiError(
      newSession().api.auth.login({ email: u.email, password: TEST_PASSWORD }),
      { status: 401, code: 'INVALID_CREDENTIALS' },
      '탈퇴 뒤 로그인',
    );

    // 자격 증명이 지워졌으니 같은 이메일로 새로 가입할 수 있고, 옷장·프로필은 비어 있다
    const fresh = newSession();
    await fresh.api.auth.signup({ email: u.email, password: TEST_PASSWORD, name: '새사람' });
    eq((await fresh.api.wardrobe.stats()).total, 0, '재가입 계정의 옷장은 비어 있음');
    const freshProfile = await fresh.api.profile.get();
    eq(freshProfile.onboarded, false, '재가입 계정의 프로필은 기본값');
    eq(
      [freshProfile.ageRange, freshProfile.bodyType, freshProfile.heightCm, freshProfile.fitPreference],
      [null, null, null, null],
      '재가입 계정의 나의 정보는 비어 있음 (탈퇴 때 지워짐)',
    );
  },
};

export const accountScenarios: Scenario[] = [logout, deleteMe];
