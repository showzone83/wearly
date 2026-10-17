// 앱 API 모듈(mobile/src/api)을 거치지 않는 회원·관리자 세션.
// 앱 모듈과 상관없이 서버 계약만 확인하는 시나리오(앱 설정·옷 사용 중지)가 쓴다.
// 하네스 서버는 Access 수명이 3초라서, 401 TOKEN_EXPIRED가 오면 다시 로그인하고 한 번 더 부른다.
import { randomUUID } from 'node:crypto';
import { eq } from './assert';
import { HARNESS_ADMIN_EMAIL, HARNESS_ADMIN_PASSWORD } from './config';
import { raw, type RawOptions, type RawResponse } from './http';

/** session.ts의 TEST_PASSWORD와 같은 값 (session.ts는 앱 모듈을 불러오므로 여기서는 쓰지 않는다) */
const PASSWORD = 'Harness1234';

function randomEmail(tag: string): string {
  return `h-${tag}-${randomUUID().slice(0, 12)}@harness.wearly.test`;
}

export interface RawUser {
  email: string;
  password: string;
  /** 회원 ID (문자열) */
  id: string;
  /** 지금 Access 토큰 (만료됐을 수 있다) */
  token(): string;
  /** 토큰을 붙여 부른다. 만료면 다시 로그인해 한 번 더 */
  call(method: string, path: string, opts?: Omit<RawOptions, 'token'>): Promise<RawResponse>;
}

async function login(email: string, password: string): Promise<{ accessToken: string; user: { id: string } }> {
  const res = await raw('POST', '/auth/login', { body: { email, password } });
  eq(res.status, 200, `로그인 200 (${email})`);
  return res.json;
}

function session(email: string, password: string, id: string, first: string): RawUser {
  let access = first;
  return {
    email,
    password,
    id,
    token: () => access,
    async call(method, path, opts = {}) {
      let res = await raw(method, path, { ...opts, token: access });
      if (res.status === 401 && res.json?.code === 'TOKEN_EXPIRED') {
        access = (await login(email, password)).accessToken;
        res = await raw(method, path, { ...opts, token: access });
      }
      return res;
    },
  };
}

/** 무작위 이메일로 가입한 회원 */
export async function rawSignup(tag = 'raw', name = '하네스'): Promise<RawUser> {
  const email = randomEmail(tag);
  const res = await raw('POST', '/auth/signup', { body: { email, password: PASSWORD, name } });
  eq(res.status, 201, `가입 201 (${email})`);
  return session(email, PASSWORD, res.json.user.id, res.json.accessToken);
}

/** run.sh가 만든 관리자 */
export async function rawAdmin(): Promise<RawUser> {
  const res = await login(HARNESS_ADMIN_EMAIL, HARNESS_ADMIN_PASSWORD);
  return session(HARNESS_ADMIN_EMAIL, HARNESS_ADMIN_PASSWORD, res.user.id, res.accessToken);
}
