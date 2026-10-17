import { useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router';

import { API_URL, errorMessage, isApiError } from '@/api';
import { useAuthStore } from '@/stores/useAuthStore';
import { Button, Notice } from '@/components/ui';

const loginErrorText = (e: unknown): string => {
  if (!isApiError(e)) return errorMessage(e);
  switch (e.code) {
    case 'INVALID_CREDENTIALS':
      return '이메일 또는 비밀번호가 맞지 않습니다.';
    case 'ACCOUNT_SUSPENDED':
      return '정지된 계정입니다.';
    case 'NOT_ADMIN':
      return '관리자 계정이 아닙니다.';
    case 'VALIDATION_FAILED':
      return e.fieldErrors[0]?.message ?? e.message;
    default:
      return errorMessage(e);
  }
};

export const LoginPage = () => {
  const user = useAuthStore((s) => s.user);
  const login = useAuthStore((s) => s.login);
  const lostReason = useAuthStore((s) => s.lostReason);
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (user) {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={from && from !== '/login' ? from : '/'} replace />;
  }

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
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit} noValidate>
        <div className="login-brand">
          <span className="brand-name">Wearly</span>
          <span className="brand-tag">관리자</span>
        </div>
        <p className="muted login-sub">관리자 계정으로 로그인하세요.</p>
        {lostReason && !error ? <Notice tone="warn">{lostReason}</Notice> : null}
        {error ? <Notice tone="error">{error}</Notice> : null}
        <label className="field">
          <span className="field-label">이메일</span>
          <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus />
        </label>
        <label className="field">
          <span className="field-label">비밀번호</span>
          <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        <Button type="submit" variant="primary" busy={busy} className="btn-block">
          로그인
        </Button>
        <p className="api-url login-api">서버: {API_URL}</p>
      </form>
    </div>
  );
};
