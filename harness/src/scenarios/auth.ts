// 헬스·인증 오류 형식·회원가입·로그인
import { eq, expectApiError, isIsoDateTime, isNonEmptyString, ok } from '../lib/assert';
import { checkErrorBody, raw } from '../lib/http';
import { newSession, randomEmail, signedUp, TEST_PASSWORD, tokensOf } from '../lib/session';
import type { Scenario } from '../scenario';

const health: Scenario = {
  group: '기본',
  name: '헬스 체크 /actuator/health → UP',
  async run() {
    const res = await raw('GET', '/actuator/health');
    eq(res.status, 200, 'HTTP 상태');
    eq(res.json?.status, 'UP', '헬스 status');
  },
};

const unauthorized: Scenario = {
  group: '기본',
  name: '토큰 없이 보호 API → 401 UNAUTHORIZED, 오류 JSON 형식',
  async run() {
    // 원시 요청으로 오류 본문 형식 확인
    const res = await raw('GET', '/users/me');
    checkErrorBody(res, 401, 'UNAUTHORIZED', '토큰 없음');

    // 형식이 틀린 토큰
    const bad = await raw('GET', '/users/me', { token: 'not-a-jwt' });
    checkErrorBody(bad, 401, 'TOKEN_INVALID', '형식 오류 토큰');

    // 앱 모듈로 호출해도 같은 코드가 ApiError로 나온다
    const s = newSession();
    const err = await expectApiError(s.api.users.me(), { status: 401, code: 'UNAUTHORIZED' }, '앱 모듈 users.me()');
    ok(Array.isArray(err.fieldErrors), 'ApiError.fieldErrors는 배열', err.fieldErrors);
    eq(s.counts.authLost, 0, '토큰이 없을 때는 onAuthLost를 부르지 않음');

    // 저장소에 형식이 틀린 토큰 → TOKEN_INVALID: 갱신하지 않고 저장소 비움 + onAuthLost 1번
    const broken = newSession();
    await broken.store.set({ accessToken: 'not-a-jwt', refreshToken: 'whatever' });
    const mark = broken.mark();
    await expectApiError(broken.api.users.me(), { status: 401, code: 'TOKEN_INVALID' }, '앱 모듈: 틀린 토큰');
    eq(broken.refreshCallsSince(mark), 0, 'TOKEN_INVALID는 refresh를 시도하지 않음');
    eq(await broken.store.get(), null, 'TOKEN_INVALID 뒤 저장소 비움');
    eq(broken.counts.authLost, 1, 'TOKEN_INVALID 뒤 onAuthLost 호출 횟수');
  },
};

const cors: Scenario = {
  group: '기본',
  name: 'CORS: Expo 웹 출처 허용, 다른 출처 거부',
  async run() {
    for (const origin of ['http://localhost:8081', 'http://localhost:19006', 'http://127.0.0.1:8081']) {
      const res = await raw('OPTIONS', '/auth/login', {
        headers: {
          Origin: origin,
          'Access-Control-Request-Method': 'POST',
          'Access-Control-Request-Headers': 'content-type,authorization',
        },
      });
      ok(res.status === 200 || res.status === 204, `${origin} 사전 요청 상태 200/204`, res.status);
      eq(res.headers.get('access-control-allow-origin'), origin, `${origin} Access-Control-Allow-Origin`);
    }
    const evil = await raw('OPTIONS', '/auth/login', {
      headers: { Origin: 'http://evil.example', 'Access-Control-Request-Method': 'POST' },
    });
    ok(evil.headers.get('access-control-allow-origin') !== 'http://evil.example', '허용 안 된 출처는 거부', {
      status: evil.status,
      allowOrigin: evil.headers.get('access-control-allow-origin'),
    });
  },
};

const signup: Scenario = {
  group: '인증',
  name: '회원가입 → 토큰 저장, users.me() 일치, 이메일 소문자 저장',
  async run(ctx) {
    const s = newSession();
    const email = randomEmail('signup').replace('h-', 'H-').replace('@harness', '@Harness');
    const name = '가입테스트';
    await s.api.auth.signup({ email, password: TEST_PASSWORD, name });

    const t = await s.store.get();
    ok(t && isNonEmptyString(t.accessToken) && isNonEmptyString(t.refreshToken), '저장소에 access·refresh 토큰 저장', t);
    eq(s.counts.set, 1, 'tokenStore.set 호출 횟수');

    const me = await s.api.users.me();
    ok(typeof me.id === 'string' && me.id.length > 0, 'user.id는 문자열 (계약 4장)', me.id);
    eq(me.email, email.toLowerCase(), 'email은 소문자로 저장');
    eq(me.name, name, 'name');
    ok(isIsoDateTime(me.createdAt), 'createdAt은 ISO-8601', me.createdAt);

    // 원시 응답 모양 (AuthResponse)
    const rawEmail = randomEmail('signup-raw');
    const res = await raw('POST', '/auth/signup', { body: { email: rawEmail, password: TEST_PASSWORD, name } });
    eq(res.status, 201, '가입 HTTP 상태');
    const b = res.json;
    ok(isNonEmptyString(b?.accessToken) && isNonEmptyString(b?.refreshToken), 'AuthResponse 토큰 필드', b);
    ok(typeof b.accessTokenExpiresIn === 'number' && b.accessTokenExpiresIn > 0, 'accessTokenExpiresIn(초)', b.accessTokenExpiresIn);
    ok(typeof b.refreshTokenExpiresIn === 'number' && b.refreshTokenExpiresIn > b.accessTokenExpiresIn, 'refreshTokenExpiresIn(초) > access', b.refreshTokenExpiresIn);
    ok(typeof b.user?.id === 'string', 'AuthResponse.user.id는 문자열', b.user);
    if (!ctx.external) eq(b.accessTokenExpiresIn, 3, 'accessTokenExpiresIn = WEARLY_ACCESS_TTL(PT3S)');
    ok(/^[A-Za-z0-9_-]{43}=?$/.test(b.refreshToken), 'refresh 토큰은 base64url (32바이트 → 43자)', b.refreshToken);
  },
};

const duplicate: Scenario = {
  group: '인증',
  name: '같은 이메일(대소문자 다르게) 재가입 → 409 EMAIL_TAKEN',
  async run() {
    const first = await signedUp('dup');
    const s = newSession();
    await expectApiError(
      s.api.auth.signup({ email: first.email.toUpperCase(), password: TEST_PASSWORD, name: '중복' }),
      { status: 409, code: 'EMAIL_TAKEN' },
      '대문자 이메일 재가입',
    );
    eq(await s.store.get(), null, '실패한 가입은 토큰을 저장하지 않음');

    const res = await raw('POST', '/auth/signup', { body: { email: first.email, password: TEST_PASSWORD, name: '중복' } });
    checkErrorBody(res, 409, 'EMAIL_TAKEN', '같은 이메일 원시 요청');
  },
};

const weakPassword: Scenario = {
  group: '인증',
  name: '약한 비밀번호·잘못된 이름 → 400 VALIDATION_FAILED + fieldErrors',
  async run() {
    const cases: { password: string; name: string; field: string; why: string }[] = [
      { password: 'abcdefgh', name: '하네스', field: 'password', why: '숫자 없음' },
      { password: '12345678', name: '하네스', field: 'password', why: '영문 없음' },
      { password: 'ab12', name: '하네스', field: 'password', why: '8자 미만' },
      { password: `a1${'x'.repeat(63)}`, name: '하네스', field: 'password', why: '64자 초과' },
      { password: TEST_PASSWORD, name: '', field: 'name', why: '이름 비어 있음' },
      { password: TEST_PASSWORD, name: '가'.repeat(21), field: 'name', why: '이름 20자 초과' },
    ];
    for (const c of cases) {
      const s = newSession();
      const err = await expectApiError(
        s.api.auth.signup({ email: randomEmail('weak'), password: c.password, name: c.name }),
        { status: 400, code: 'VALIDATION_FAILED' },
        `가입 검증 (${c.why})`,
      );
      const fields = (err.fieldErrors ?? []).map((f) => f.field);
      ok(fields.includes(c.field), `${c.why}: fieldErrors에 '${c.field}' 포함`, err.fieldErrors);
      ok(
        (err.fieldErrors ?? []).every((f) => isNonEmptyString(f.message)),
        `${c.why}: fieldErrors 항목마다 message`,
        err.fieldErrors,
      );
    }
    const res = await raw('POST', '/auth/signup', { body: { email: 'not-an-email', password: TEST_PASSWORD, name: '하네스' } });
    checkErrorBody(res, 400, 'VALIDATION_FAILED', '이메일 형식 오류');
    ok(res.json.fieldErrors.some((f: { field?: string }) => f.field === 'email'), "fieldErrors에 'email' 포함", res.json.fieldErrors);
  },
};

const login: Scenario = {
  group: '인증',
  name: '로그인 성공(이메일 대소문자 무관) / 틀린 비밀번호·없는 이메일 → 401 INVALID_CREDENTIALS',
  async run() {
    const u = await signedUp('login');
    const before = await tokensOf(u);

    const s = newSession();
    await s.api.auth.login({ email: u.email.toUpperCase(), password: u.password });
    const t = await tokensOf(s);
    ok(t.refreshToken !== before.refreshToken, '로그인마다 새 refresh 토큰');
    const me = await s.api.users.me();
    eq(me.email, u.email, '로그인한 사용자 email');

    const wrong = newSession();
    await expectApiError(
      wrong.api.auth.login({ email: u.email, password: 'Wrong12345' }),
      { status: 401, code: 'INVALID_CREDENTIALS' },
      '틀린 비밀번호',
    );
    eq(await wrong.store.get(), null, '로그인 실패 시 저장소 비어 있음');
    eq(wrong.counts.authLost, 0, '로그인 실패는 onAuthLost를 부르지 않음');

    await expectApiError(
      newSession().api.auth.login({ email: randomEmail('nobody'), password: TEST_PASSWORD }),
      { status: 401, code: 'INVALID_CREDENTIALS' },
      '없는 이메일 (어느 쪽인지 알리지 않음)',
    );
  },
};

export const authScenarios: Scenario[] = [health, unauthorized, cors, signup, duplicate, weakPassword, login];
