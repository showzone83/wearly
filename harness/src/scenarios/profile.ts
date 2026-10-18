// 취향·근무 패턴 프로필
import { eq, expectApiError, ok } from '../lib/assert';
import { DEFAULT_PROFILE_NAME, DEFAULT_PROFILE_WITHOUT_NAME } from '../lib/fixtures';
import { signedUp } from '../lib/session';
import type { ProfileDto } from '../../../mobile/src/api/types';
import type { Scenario } from '../scenario';

const profile: Scenario = {
  group: '프로필',
  name: '처음 GET → 기본값·onboarded false / PUT 후 GET 일치 / name이 users.me()에 반영',
  async run(ctx) {
    const u = await signedUp('profile', '프로필주인');

    const initial = await u.api.profile.get();
    eq(initial.onboarded, false, '저장 전 onboarded');
    const { name: initialName, onboarded: _o, ...rest } = initial;
    eq(rest, DEFAULT_PROFILE_WITHOUT_NAME, '저장 전 기본값 (앱 DEFAULT_PROFILE과 같음, name 제외)');
    ok(
      initialName === u.name || initialName === DEFAULT_PROFILE_NAME,
      `저장 전 name은 가입 이름('${u.name}') 또는 기본 이름('${DEFAULT_PROFILE_NAME}')`,
      initialName,
    );
    ctx.note(`저장 전 프로필 name = '${initialName}'`);

    const next: ProfileDto = {
      name: '바뀐이름',
      dressCodes: ['FORMAL', 'CASUAL'],
      moods: ['CLASSIC', 'STREET'],
      colors: ['black', 'camel', 'burgundy'],
      workType: 'CUSTOM',
      workDays: [1, 3, 5],
      workRule: false,
      offRule: true,
      notify: { morning: false, evening: true, dMinus1: false },
      ageRange: 'AGE_30_LATE',
      bodyType: 'STRAIGHT',
      heightCm: 175,
      fitPreference: 'REGULAR',
      onboarded: true,
    };
    const saved = await u.api.profile.put(next);
    eq(saved, next, 'PUT 응답');
    eq(await u.api.profile.get(), next, 'PUT 뒤 GET');

    const me = await u.api.users.me();
    eq(me.name, '바뀐이름', 'PUT의 name이 users.me()에 반영');

    // 한 번 더 바꿔도 덮어쓴다
    const again: ProfileDto = { ...next, workType: 'WEEKEND', workDays: [0, 6], moods: [], onboarded: true };
    eq(await u.api.profile.put(again), again, '두 번째 PUT 응답');
    eq(await u.api.profile.get(), again, '두 번째 PUT 뒤 GET');

    await expectApiError(
      u.api.profile.put({ ...next, name: '' }),
      { status: 400, code: 'VALIDATION_FAILED' },
      '빈 name으로 PUT',
    );
  },
};

const personal: Scenario = {
  group: '프로필',
  name: '나의 정보(연령대·체형·키·핏 선호): 저장·비우기·코드·키 범위 검증',
  async run() {
    const u = await signedUp('personal');
    const base = { ...(await u.api.profile.get()), onboarded: true };
    eq(
      [base.ageRange, base.bodyType, base.heightCm, base.fitPreference],
      [null, null, null, null],
      '처음에는 모두 null',
    );

    const filled: ProfileDto = {
      ...base,
      ageRange: 'AGE_20_LATE',
      bodyType: 'NATURAL',
      heightCm: 168,
      fitPreference: 'OVERSIZED',
    };
    eq(await u.api.profile.put(filled), filled, '나의 정보 저장');
    eq(await u.api.profile.get(), filled, '저장 뒤 GET');

    const cleared: ProfileDto = { ...filled, ageRange: null, bodyType: null, heightCm: null, fitPreference: null };
    eq(await u.api.profile.put(cleared), cleared, 'null로 보내면 비운다');

    await expectApiError(
      u.api.profile.put({ ...filled, ageRange: 'AGE_99' }),
      { status: 400, code: 'VALIDATION_FAILED' },
      '모르는 연령대 코드',
    );
    await expectApiError(
      u.api.profile.put({ ...filled, heightCm: 250 }),
      { status: 400, code: 'VALIDATION_FAILED' },
      '키 범위(130~210) 밖',
    );
  },
};

export const profileScenarios: Scenario[] = [profile, personal];
