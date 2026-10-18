// 회사 계정(SSO) 로그인 콜백 (doc/22 4.2). 백엔드가 IdP 확인을 마치고 이 주소로 보낸다:
//   성공 /sso/callback?code=<한 번만 쓰는 교환 코드> → POST /auth/sso/exchange → 관리자 화면
//   실패 /sso/callback?error=<오류 코드>&status=<HTTP 상태>[&reason=…]&traceId=… → 로그인 화면에 문구
import { useEffect } from 'react';

import { ApiError } from '@/api';

import { useAuthStore } from '@/stores/useAuthStore';

import { useLocation, useNavigate } from 'react-router';

/** 교환 코드는 한 번만 쓴다. 개발 모드(StrictMode)에서 effect가 두 번 돌아도 한 번만 보낸다 */
const usedCodes = new Set<string>();

/** 콜백 쿼리의 오류 → ApiError (문구는 lib/errorText.ts가 code·params로 정한다) */
const callbackError = (q: URLSearchParams): ApiError => {
  const reason = q.get('reason');
  return new ApiError(Number(q.get('status')) || 400, q.get('error') || 'SSO_TOKEN_INVALID', {
    params: reason ? { reason } : {},
    traceId: q.get('traceId'),
  });
};

export const SsoCallbackPage = () => {
  const completeSso = useAuthStore((s) => s.completeSso);
  const failSso = useAuthStore((s) => s.failSso);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const q = new URLSearchParams(location.search);
    const code = q.get('code');
    if (!code) {
      failSso(callbackError(q));
      navigate('/login', { replace: true });
      return;
    }
    if (usedCodes.has(code)) return;
    usedCodes.add(code);
    completeSso(code)
      .then((signedIn) => navigate(signedIn ? '/' : '/login', { replace: true }))
      .catch((e: unknown) => {
        failSso(e);
        navigate('/login', { replace: true });
      });
  }, [location.search, completeSso, failSso, navigate]);

  return (
    <div className="login-wrap">
      <div className="login-card">
        <p className="muted">
          <span className="spinner" aria-hidden /> 회사 계정으로 로그인하는 중…
        </p>
      </div>
    </div>
  );
};
