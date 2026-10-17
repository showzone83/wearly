// 사이트 전체가 함께 쓰는 API 클라이언트 하나. 로그인 상태를 잃으면 구독자(AuthProvider)에게 알린다.
import { API_URL, createAdminApi } from './client';
import type { ApiError } from './errors';

type AuthLostListener = (reason: ApiError) => void;
const listeners = new Set<AuthLostListener>();

export function onAuthLost(listener: AuthLostListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export const api = createAdminApi({
  baseUrl: API_URL,
  onAuthLost: (reason) => listeners.forEach((l) => l(reason)),
});

export { API_URL };
export { ApiError, errorMessage, isApiError } from './errors';
export { tokenStore } from './tokenStore';
export type * from './types';
