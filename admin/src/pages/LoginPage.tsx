// 로그인: 이메일·비밀번호 → (2단계 인증이 필요한 관리자면) TOTP 등록 또는 코드 입력 → (처음 등록이면) 복구 코드 확인 (doc/22)
// 회사 계정(SSO, doc/22 4.2)이 켜져 있으면 위에 공급자 버튼을 보인다. 이메일·비밀번호는 비상 관리자 계정용으로 남는다.
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';

import { api, API_URL, errorMessage, isApiError } from '@/api';
import type { IMfaSetupResponse, ISsoProvider } from '@/api';

import { MfaSetupView, RecoveryCodesView, TotpInput } from '@/components/Mfa';
import { Button, Notice } from '@/components/ui';

import { useAuthStore } from '@/stores/useAuthStore';
import type { IMfaChallenge } from '@/stores/useAuthStore';

import { Navigate, useLocation } from 'react-router';

import { errorTextWithTrace, fieldErrorText } from '@/lib/errorText';
import { isTotpCode, normalizeRecoveryCode } from '@/lib/mfa';

const FIELD_NAME: Record<string, string> = {
  email: '이메일',
  password: '비밀번호',
  code: '인증 코드',
  recoveryCode: '복구 코드',
};

/** 로그인 오류 문구. 문구는 코드로 정하고(lib/errorText.ts), 입력 오류는 "이메일: 형식이 올바르지 않아요." */
const loginErrorText = (e: unknown): string => {
  if (isApiError(e) && e.code === 'VALIDATION_FAILED' && e.fieldErrors.length) {
    const f = e.fieldErrors[0];
    return `${FIELD_NAME[f.field] ?? f.field}: ${fieldErrorText(f)}`;
  }
  return isApiError(e) ? errorTextWithTrace(e) : errorMessage(e);
};

const Brand = ({ sub }: { sub: string }) => {
  return (
    <>
      <div className="login-brand">
        <span className="brand-name">Wearly</span>
        <span className="brand-tag">관리자</span>
      </div>
      <p className="muted login-sub">{sub}</p>
    </>
  );
};

/** 회사 계정 버튼 (GET /auth/sso/providers). 꺼져 있거나 서버에 닿지 못하면 아무것도 보이지 않는다 */
const SsoButtons = () => {
  const [providers, setProviders] = useState<ISsoProvider[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);

  const startSso = useAuthStore((s) => s.startSso);

  useEffect(() => {
    let alive = true;
    api.auth
      .ssoProviders()
      .then((list) => {
        if (alive) setProviders(list);
      })
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, []);

  const handleStart = (id: string) => {
    setBusyId(id);
    startSso(id);
  };

  if (!providers.length) return null;
  return (
    <div className="sso-block">
      {providers.map((p) => (
        <Button
          key={p.id}
          variant="primary"
          className="btn-block"
          busy={busyId === p.id}
          disabled={busyId !== null}
          onClick={() => handleStart(p.id)}
        >
          {p.displayName} 로그인
        </Button>
      ))}
      <div className="login-divider">
        <span>또는 비상 관리자 계정</span>
      </div>
    </div>
  );
};

/** 1단계: 이메일·비밀번호 */
const CredentialsStep = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const login = useAuthStore((s) => s.login);
  const lostReason = useAuthStore((s) => s.lostReason);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !password) {
      setError('이메일과 비밀번호를 입력해 주세요.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), password);
    } catch (err) {
      setError(loginErrorText(err));
      setBusy(false);
    }
  };

  return (
    <form className="login-card" onSubmit={submit} noValidate>
      <Brand sub="관리자 계정으로 로그인하세요." />
      {lostReason && !error ? <Notice tone="warn">{lostReason}</Notice> : null}
      {error ? <Notice tone="error">{error}</Notice> : null}
      <SsoButtons />
      <label className="field">
        <span className="field-label">이메일</span>
        <input
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoFocus
        />
      </label>
      <label className="field">
        <span className="field-label">비밀번호</span>
        <input
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>
      <Button type="submit" variant="primary" busy={busy} className="btn-block">
        로그인
      </Button>
      <p className="api-url login-api">서버: {API_URL}</p>
    </form>
  );
};

/** 2단계(처음): 인증 앱 등록 — QR·수동 입력 키 → 코드 확인 */
const MfaEnrollStep = ({ challenge }: { challenge: IMfaChallenge }) => {
  const [setup, setSetup] = useState<IMfaSetupResponse | null>(null);
  const [setupError, setSetupError] = useState<string | null>(null);
  /** "새 QR 받기"를 누를 때마다 늘려 등록 시작을 다시 부른다 */
  const [nonce, setNonce] = useState(0);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const verifyMfa = useAuthStore((s) => s.verifyMfa);
  const abortMfaIfFatal = useAuthStore((s) => s.abortMfaIfFatal);
  const cancelMfa = useAuthStore((s) => s.cancelMfa);

  useEffect(() => {
    let alive = true;
    api.auth
      .mfaSetup(challenge.mfaToken)
      .then((res) => {
        if (!alive) return;
        setSetup(res);
        setSetupError(null);
      })
      .catch((e: unknown) => {
        if (!alive || abortMfaIfFatal(e)) return;
        setSetupError(loginErrorText(e));
      });
    return () => {
      alive = false;
    };
  }, [challenge.mfaToken, nonce, abortMfaIfFatal]);

  const handleRetrySetup = () => {
    setSetup(null);
    setSetupError(null);
    setCode('');
    setNonce((n) => n + 1);
  };
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!isTotpCode(code)) {
      setError('인증 앱에 나온 6자리 숫자를 입력해 주세요.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await verifyMfa({ code });
    } catch (err) {
      setError(loginErrorText(err));
      setBusy(false);
    }
  };

  return (
    <form className="login-card login-card-wide" onSubmit={submit} noValidate>
      <Brand sub="2단계 인증을 등록해 주세요. 관리자는 로그인할 때마다 인증 앱의 코드가 필요해요." />
      <p className="small muted">계정: {challenge.email}</p>
      {setupError ? (
        <Notice tone="error">
          {setupError}{' '}
          <button type="button" className="link-btn" onClick={handleRetrySetup}>
            다시 시도
          </button>
        </Notice>
      ) : null}
      {setup ? <MfaSetupView setup={setup} /> : !setupError ? <span className="spinner" aria-hidden /> : null}
      {error ? <Notice tone="error">{error}</Notice> : null}
      <label className="field">
        <span className="field-label">인증 코드</span>
        <TotpInput value={code} onChange={setCode} />
      </label>
      <Button type="submit" variant="primary" busy={busy} disabled={!setup} className="btn-block">
        확인하고 등록
      </Button>
      <div className="row gap-sm login-links">
        <button type="button" className="link-btn small" onClick={handleRetrySetup} disabled={!setup}>
          새 QR 받기
        </button>
        <button type="button" className="link-btn small" onClick={() => cancelMfa()}>
          처음으로
        </button>
      </div>
    </form>
  );
};

/** 2단계(등록됨): 인증 앱 코드 또는 복구 코드 */
const MfaCodeStep = ({ challenge }: { challenge: IMfaChallenge }) => {
  const [mode, setMode] = useState<'totp' | 'recovery'>('totp');
  const [code, setCode] = useState('');
  const [recovery, setRecovery] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const verifyMfa = useAuthStore((s) => s.verifyMfa);
  const cancelMfa = useAuthStore((s) => s.cancelMfa);

  const switchMode = () => {
    setMode(mode === 'totp' ? 'recovery' : 'totp');
    setError(null);
  };
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const recoveryCode = normalizeRecoveryCode(recovery);
    if (mode === 'totp' && !isTotpCode(code)) {
      setError('인증 앱에 나온 6자리 숫자를 입력해 주세요.');
      return;
    }
    if (mode === 'recovery' && !recoveryCode) {
      setError('복구 코드를 입력해 주세요.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await verifyMfa(mode === 'totp' ? { code } : { recoveryCode });
    } catch (err) {
      setError(loginErrorText(err));
      setBusy(false);
    }
  };

  return (
    <form className="login-card" onSubmit={submit} noValidate>
      <Brand
        sub={mode === 'totp' ? '인증 앱에 나온 6자리 코드를 입력하세요.' : '저장해 둔 복구 코드 하나를 입력하세요.'}
      />
      <p className="small muted">계정: {challenge.email}</p>
      {error ? <Notice tone="error">{error}</Notice> : null}
      {mode === 'totp' ? (
        <label className="field">
          <span className="field-label">인증 코드</span>
          <TotpInput value={code} onChange={setCode} autoFocus />
        </label>
      ) : (
        <label className="field">
          <span className="field-label">복구 코드</span>
          <input
            className="mono"
            autoComplete="off"
            spellCheck={false}
            placeholder="abcde-fghjk"
            value={recovery}
            onChange={(e) => setRecovery(e.target.value)}
            autoFocus
          />
          <span className="field-hint">복구 코드는 한 번 쓰면 다시 쓸 수 없어요.</span>
        </label>
      )}
      <Button type="submit" variant="primary" busy={busy} className="btn-block">
        확인
      </Button>
      <div className="row gap-sm login-links">
        <button type="button" className="link-btn small" onClick={switchMode}>
          {mode === 'totp' ? '복구 코드로 들어가기' : '인증 앱 코드로 들어가기'}
        </button>
        <button type="button" className="link-btn small" onClick={() => cancelMfa()}>
          처음으로
        </button>
      </div>
    </form>
  );
};

/** 3단계(처음 등록): 복구 코드 10개 → "저장했어요" → 관리자 화면 */
const RecoveryCodesStep = ({ codes }: { codes: string[] }) => {
  const email = useAuthStore((s) => s.pendingUser?.email ?? null);
  const acknowledge = useAuthStore((s) => s.acknowledgeRecoveryCodes);

  return (
    <div className="login-card login-card-wide">
      <Brand sub="2단계 인증을 등록했어요. 복구 코드를 저장해 주세요." />
      <RecoveryCodesView codes={codes} email={email} doneLabel="저장했어요, 관리자 화면으로" onDone={acknowledge} />
    </div>
  );
};

export const LoginPage = () => {
  const user = useAuthStore((s) => s.user);
  const mfa = useAuthStore((s) => s.mfa);
  const recoveryCodes = useAuthStore((s) => s.recoveryCodes);
  const location = useLocation();

  if (user) {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={from && from !== '/login' ? from : '/'} replace />;
  }

  return (
    <div className="login-wrap">
      {recoveryCodes ? (
        <RecoveryCodesStep codes={recoveryCodes} />
      ) : mfa ? (
        mfa.enrolled ? (
          <MfaCodeStep key={mfa.mfaToken} challenge={mfa} />
        ) : (
          <MfaEnrollStep key={mfa.mfaToken} challenge={mfa} />
        )
      ) : (
        <CredentialsStep />
      )}
    </div>
  );
};
