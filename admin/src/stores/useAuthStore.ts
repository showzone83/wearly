// 로그인한 관리자 상태 (zustand). 토큰은 sessionStorage(api/tokenStore.ts)에 그대로 두고, 여기서는 화면용 사용자 정보와 상태만 든다.
// 화면에서는 useAuthStore((s) => s.user)처럼 선택자로 구독하고, 컴포넌트 밖에서는 useAuthStore.getState()로 부른다.
import { api, onAuthLost, tokenStore } from '@/api';
import type { ApiError, IUserDto } from '@/api';

import { create } from 'zustand';

import { queryClient } from '@/lib/queryClient';

/**
 * - signedOut: 로그인 전이거나 로그인 상태를 잃음
 * - restoring: 새로 고침 뒤 저장된 토큰·사용자로 화면을 먼저 보여 주고, 서버에 내 정보를 다시 확인하는 중
 * - signedIn: 로그인 또는 확인 완료
 */
export type TAuthStatus = 'signedOut' | 'restoring' | 'signedIn';

interface IAuthStore {
  user: IUserDto | null;
  status: TAuthStatus;
  /** 로그인 화면에 보여 줄 '왜 로그아웃됐는지' 문구 */
  lostReason: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** API 클라이언트가 갱신 실패 등으로 로그인 상태를 잃었을 때 (tokenStore는 이미 비워진 뒤) */
  handleAuthLost: (reason: ApiError) => void;
  /** 새로 고침 뒤 저장된 토큰으로 내 정보를 다시 확인한다 (역할이 바뀌었을 수 있다) */
  restore: () => Promise<void>;
}

const lostReasonText = (reason: ApiError): string => {
  if (reason.code === 'FORBIDDEN' || reason.code === 'NOT_ADMIN') return '관리자 권한이 없어 로그아웃했습니다.';
  if (reason.code === 'TOKEN_INVALID') return '계정 상태가 바뀌어 로그아웃했습니다. 다시 로그인해 주세요.';
  return '로그인이 만료됐습니다. 다시 로그인해 주세요.';
};

const storedUser = tokenStore.get() ? tokenStore.getUser() : null;

export const useAuthStore = create<IAuthStore>()((set, get) => ({
  user: storedUser,
  status: storedUser ? 'restoring' : 'signedOut',
  lostReason: null,

  login: async (email, password) => {
    const auth = await api.auth.login({ email, password });
    set({ user: auth.user, status: 'signedIn', lostReason: null });
  },

  logout: async () => {
    await api.auth.logout();
    queryClient.clear();
    set({ user: null, status: 'signedOut', lostReason: null });
  },

  handleAuthLost: (reason) => {
    queryClient.clear();
    set({
      user: null,
      status: 'signedOut',
      lostReason: lostReasonText(reason),
    });
  },

  restore: async () => {
    if (!tokenStore.get()) return;
    try {
      const me = await api.auth.me();
      if (!tokenStore.get()) return; // 확인하는 사이 로그아웃·인증 상실됨
      if (me.role && me.role !== 'ADMIN') {
        tokenStore.clear();
        set({
          user: null,
          status: 'signedOut',
          lostReason: '관리자 계정이 아닙니다.',
        });
        return;
      }
      tokenStore.setUser(me);
      set({ user: me, status: 'signedIn' });
    } catch {
      // 인증 오류는 handleAuthLost가 처리한다. 네트워크 오류면 저장된 정보로 계속 보여 주고 화면에서 다시 시도한다
      if (get().status === 'restoring') set({ status: 'signedIn' });
    }
  },
}));

// API 클라이언트의 인증 상실 알림을 스토어로 잇는다. 개발 중 모듈을 다시 불러오면 이전 구독을 푼다.
const unsubscribe = onAuthLost((reason) => useAuthStore.getState().handleAuthLost(reason));
import.meta.hot?.dispose(unsubscribe);
