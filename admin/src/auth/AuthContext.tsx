// 로그인한 관리자 상태. 토큰은 sessionStorage(api/tokenStore.ts)에 있고, 여기서는 화면용 사용자 정보만 들고 있다.
import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { api, onAuthLost, tokenStore, type UserDto } from '@/api';

interface AuthState {
  user: UserDto | null;
  /** 로그인 화면에 보여 줄 '왜 로그아웃됐는지' 문구 */
  lostReason: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

function initialUser(): UserDto | null {
  return tokenStore.get() ? tokenStore.getUser() : null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(initialUser);
  const [lostReason, setLostReason] = useState<string | null>(null);
  const queryClient = useQueryClient();

  useEffect(
    () =>
      onAuthLost((reason) => {
        setUser(null);
        queryClient.clear();
        setLostReason(
          reason.code === 'FORBIDDEN' || reason.code === 'NOT_ADMIN'
            ? '관리자 권한이 없어 로그아웃했습니다.'
            : reason.code === 'TOKEN_INVALID'
              ? '계정 상태가 바뀌어 로그아웃했습니다. 다시 로그인해 주세요.'
              : '로그인이 만료됐습니다. 다시 로그인해 주세요.',
        );
      }),
    [queryClient],
  );

  // 새로 고침 뒤에는 저장된 토큰으로 내 정보를 다시 확인한다 (역할이 바뀌었을 수 있다)
  useEffect(() => {
    if (!tokenStore.get()) return;
    let cancelled = false;
    api.auth
      .me()
      .then((me) => {
        if (cancelled) return;
        if (me.role && me.role !== 'ADMIN') {
          tokenStore.clear();
          setUser(null);
          setLostReason('관리자 계정이 아닙니다.');
          return;
        }
        tokenStore.setUser(me);
        setUser(me);
      })
      .catch(() => undefined); // 인증 오류는 onAuthLost가 처리한다. 네트워크 오류면 화면에서 다시 시도한다
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const auth = await api.auth.login({ email, password });
    setLostReason(null);
    setUser(auth.user);
  }, []);

  const logout = useCallback(async () => {
    await api.auth.logout();
    queryClient.clear();
    setLostReason(null);
    setUser(null);
  }, [queryClient]);

  const value = useMemo(() => ({ user, lostReason, login, logout }), [user, lostReason, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('AuthProvider 밖에서 useAuth를 썼습니다.');
  return ctx;
}
