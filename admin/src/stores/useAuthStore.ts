// 로그인한 관리자 상태 (zustand). 토큰은 sessionStorage(api/tokenStore.ts)에 그대로 두고, 여기서는 화면용 사용자 정보와 상태만 든다.
// 화면에서는 useAuthStore((s) => s.user)처럼 선택자로 구독하고, 컴포넌트 밖에서는 useAuthStore.getState()로 부른다.
// 2단계 인증(doc/22): 로그인 응답이 대기(mfaToken)면 mfa에 두고(메모리만) 등록 또는 코드 입력 화면을 거친다.
import { api, isApiError, onAuthLost, tokenStore } from '@/api';
import type { ApiError, IUserDto } from '@/api';

import { create } from 'zustand';

import { errorText } from '@/lib/errorText';
import { queryClient } from '@/lib/queryClient';

/**
 * - signedOut: 로그인 전이거나 로그인 상태를 잃음
 * - restoring: 새로 고침 뒤 저장된 토큰·사용자로 화면을 먼저 보여 주고, 서버에 내 정보를 다시 확인하는 중
 * - signedIn: 로그인 또는 확인 완료
 */
export type TAuthStatus = 'signedOut' | 'restoring' | 'signedIn';

/** 비밀번호는 맞았고 2단계 인증을 기다리는 중. 대기 토큰은 이 메모리에만 둔다 (sessionStorage에 넣지 않는다) */
export interface IMfaChallenge {
  mfaToken: string;
  /** false면 TOTP 등록 화면, true면 코드 입력 화면 */
  enrolled: boolean;
  email: string;
}

interface IAuthStore {
  user: IUserDto | null;
  status: TAuthStatus;
  /** 로그인 화면에 보여 줄 '왜 로그아웃됐는지' 문구 */
  lostReason: string | null;
  /** 2단계 인증 대기 */
  mfa: IMfaChallenge | null;
  /**
   * 처음 등록하며 받은 복구 코드 10개. 토큰은 이미 저장했지만, "저장했어요"를 누를 때까지 user를 비워 두어
   * 로그인 화면(복구 코드 표시)에 머문다. 그동안 로그인한 관리자는 pendingUser
   */
  recoveryCodes: string[] | null;
  pendingUser: IUserDto | null;
  /** 로그인 때 받은 남은 복구 코드 수 (3 이하이면 화면 위에 경고). 경고를 닫거나 다시 등록하면 null */
  recoveryCodesLeft: number | null;
  login: (email: string, password: string) => Promise<void>;
  /** 대기 토큰으로 코드(또는 복구 코드)를 확인한다. 대기 토큰이 무효·잠김이면 첫 화면으로 돌아가고 오류를 다시 던진다 */
  verifyMfa: (input: { code?: string; recoveryCode?: string }) => Promise<void>;
  /** 2단계 인증 오류가 처음부터 다시 로그인해야 하는 것이면(대기 토큰 무효·잠김) 첫 화면으로 돌리고 true */
  abortMfaIfFatal: (e: unknown) => boolean;
  /** 2단계 인증을 그만두고 첫 화면으로. reason이 있으면 로그인 화면에 보인다 */
  cancelMfa: (reason?: string | null) => void;
  /** 복구 코드를 저장했다고 확인 → 관리자 화면으로 */
  acknowledgeRecoveryCodes: () => void;
  setRecoveryCodesLeft: (n: number | null) => void;
  logout: () => Promise<void>;
  /** API 클라이언트가 갱신 실패 등으로 로그인 상태를 잃었을 때 (tokenStore는 이미 비워진 뒤) */
  handleAuthLost: (reason: ApiError) => void;
  /** 새로 고침 뒤 저장된 토큰으로 내 정보를 다시 확인한다 (역할이 바뀌었을 수 있다) */
  restore: () => Promise<void>;
}

const lostReasonText = (reason: ApiError): string => {
  if (reason.code === 'FORBIDDEN' || reason.code === 'NOT_ADMIN') return '관리자 권한이 없어 로그아웃했습니다.';
  // 2단계 인증을 거치지 않은 세션 (doc/22)
  if (reason.code === 'MFA_REQUIRED') return '2단계 인증이 필요해요. 다시 로그인해 주세요.';
  // 정지·탈퇴, 또는 다른 관리자가 이 세션을 강제 로그아웃함 (doc/16 4장)
  if (reason.code === 'TOKEN_INVALID')
    return '다른 곳에서 로그아웃되었거나 계정 상태가 바뀌었습니다. 다시 로그인해 주세요.';
  if (reason.code === 'REFRESH_REUSED') return errorText(reason);
  return '로그인이 만료됐습니다. 다시 로그인해 주세요.';
};

const storedUser = tokenStore.get() ? tokenStore.getUser() : null;

/** 대기 토큰이 끝나면 화면도 첫 화면으로 돌린다 (서버 수명보다 조금 일찍) */
let mfaTimer: ReturnType<typeof setTimeout> | null = null;
const MFA_EXPIRED_TEXT = '인증 시간이 지났어요. 처음부터 다시 로그인해 주세요.';

const clearMfaTimer = (): void => {
  if (mfaTimer) clearTimeout(mfaTimer);
  mfaTimer = null;
};

/** 로그인 흐름의 임시 상태를 모두 비운 값 */
const NO_PENDING = { mfa: null, recoveryCodes: null, pendingUser: null } as const;

export const useAuthStore = create<IAuthStore>()((set, get) => ({
  user: storedUser,
  status: storedUser ? 'restoring' : 'signedOut',
  lostReason: null,
  ...NO_PENDING,
  recoveryCodesLeft: null,

  login: async (email, password) => {
    clearMfaTimer();
    const result = await api.auth.login({ email, password });
    if (result.kind === 'signedIn') {
      set({ ...NO_PENDING, user: result.auth.user, status: 'signedIn', lostReason: null, recoveryCodesLeft: null });
      return;
    }
    set({ ...NO_PENDING, mfa: { mfaToken: result.mfaToken, enrolled: result.enrolled, email }, lostReason: null });
    const ms = Math.max(30, result.expiresIn - 5) * 1000;
    mfaTimer = setTimeout(() => {
      if (get().mfa?.mfaToken === result.mfaToken) get().cancelMfa(MFA_EXPIRED_TEXT);
    }, ms);
  },

  verifyMfa: async (input) => {
    const mfa = get().mfa;
    if (!mfa) throw new Error(MFA_EXPIRED_TEXT);
    try {
      const res = await api.auth.mfaVerify({ mfaToken: mfa.mfaToken, ...input });
      clearMfaTimer();
      if (res.recoveryCodes?.length) {
        // 처음 등록: 복구 코드를 보여 주고 확인을 받은 뒤 들어간다
        set({ mfa: null, recoveryCodes: res.recoveryCodes, pendingUser: res.user, recoveryCodesLeft: null });
        return;
      }
      set({
        ...NO_PENDING,
        user: res.user,
        status: 'signedIn',
        lostReason: null,
        recoveryCodesLeft: res.recoveryCodesLeft,
      });
    } catch (e) {
      get().abortMfaIfFatal(e);
      throw e;
    }
  },

  abortMfaIfFatal: (e) => {
    if (!isApiError(e)) return false;
    // 대기 토큰 무효·만료, 잠김(대기 토큰도 무효가 된다), 등록 상태가 바뀜 → 처음부터
    if (e.code === 'MFA_TOKEN_INVALID' || e.code === 'MFA_LOCKED' || e.code === 'MFA_SETUP_REQUIRED') {
      get().cancelMfa(errorText(e));
      return true;
    }
    return false;
  },

  cancelMfa: (reason = null) => {
    clearMfaTimer();
    set({ mfa: null, lostReason: reason });
  },

  acknowledgeRecoveryCodes: () => {
    const user = get().pendingUser;
    if (!user) {
      set({ ...NO_PENDING });
      return;
    }
    set({ ...NO_PENDING, user, status: 'signedIn', lostReason: null });
  },

  setRecoveryCodesLeft: (n) => {
    set({ recoveryCodesLeft: n });
  },

  logout: async () => {
    clearMfaTimer();
    await api.auth.logout();
    queryClient.clear();
    set({ ...NO_PENDING, user: null, status: 'signedOut', lostReason: null, recoveryCodesLeft: null });
  },

  handleAuthLost: (reason) => {
    clearMfaTimer();
    queryClient.clear();
    set({
      ...NO_PENDING,
      user: null,
      status: 'signedOut',
      lostReason: lostReasonText(reason),
      recoveryCodesLeft: null,
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
