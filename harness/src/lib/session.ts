// 앱과 같은 코드(mobile/src/api)로 클라이언트를 만든다.
// - 토큰 저장소를 감싸 set·clear 호출 횟수를 센다.
// - 클라이언트의 fetch 주입 옵션으로 이 세션이 보낸 요청을 기록한다 (/auth/refresh가 몇 번 나갔는지).
// - onAuthLost 호출 횟수를 센다.
import { randomUUID } from 'node:crypto';
import { createApiClient } from '../../../mobile/src/api/client';
import { createMemoryTokenStore, type TokenStore } from '../../../mobile/src/api/tokenStore';
import { apiBase } from './config';

export const TEST_PASSWORD = 'Harness1234';

export function randomEmail(tag = 'u'): string {
  return `h-${tag}-${randomUUID().slice(0, 12)}@harness.wearly.test`;
}

export interface SentRequest {
  method: string;
  /** /api/v1 뒤 경로 (쿼리 제외) */
  path: string;
}

export function newSession() {
  const inner = createMemoryTokenStore();
  const counts = { set: 0, clear: 0, authLost: 0 };
  const sent: SentRequest[] = [];
  const recordingFetch: typeof fetch = (input, init) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const path = new URL(url).pathname.replace(/^\/api\/v1/, '');
    sent.push({ method: (init?.method ?? 'GET').toUpperCase(), path });
    return fetch(input, init);
  };
  const store: TokenStore = {
    get: () => inner.get(),
    set: async (t) => {
      counts.set++;
      await inner.set(t);
    },
    clear: async () => {
      counts.clear++;
      await inner.clear();
    },
  };
  const api = createApiClient({
    baseUrl: apiBase(),
    tokenStore: store,
    onAuthLost: () => {
      counts.authLost++;
    },
    fetch: recordingFetch,
  });
  /** 지금까지 보낸 요청 수. refreshCallsSince와 함께 쓴다 */
  const mark = () => sent.length;
  /** mark 이후 보낸 /auth/refresh 요청 수 */
  const refreshCallsSince = (m: number) =>
    sent.slice(m).filter((r) => r.method === 'POST' && r.path === '/auth/refresh').length;
  return { api, store, counts, sent, mark, refreshCallsSince };
}

export type Session = ReturnType<typeof newSession>;

export interface UserSession extends Session {
  email: string;
  password: string;
  name: string;
}

/** 무작위 이메일로 가입까지 마친 독립 사용자 */
export async function signedUp(tag = 'u', name = '하네스'): Promise<UserSession> {
  const s = newSession();
  const email = randomEmail(tag);
  await s.api.auth.signup({ email, password: TEST_PASSWORD, name });
  return { ...s, email, password: TEST_PASSWORD, name };
}

/** 저장소의 토큰 (없으면 실패) */
export async function tokensOf(s: Session) {
  const t = await s.store.get();
  if (!t) throw new Error('토큰 저장소가 비어 있음');
  return t;
}
