import { useState } from 'react';
import type { FormEvent } from 'react';

import { API_URL, errorMessage, isApiError } from '@/api';

import { Button, Notice } from '@/components/ui';

import { useAuthStore } from '@/stores/useAuthStore';

import { Navigate, useLocation } from 'react-router';

import { errorTextWithTrace, fieldErrorText } from '@/lib/errorText';

const FIELD_NAME: Record<string, string> = { email: '이메일', password: '비밀번호' };

/** 로그인 오류 문구. 문구는 코드로 정하고(lib/errorText.ts), 입력 오류는 "이메일: 형식이 올바르지 않아요." */
const loginErrorText = (e: unknown): string => {
  if (isApiError(e) && e.code === 'VALIDATION_FAILED' && e.fieldErrors.length) {
    const f = e.fieldErrors[0];
    return `${FIELD_NAME[f.field] ?? f.field}: ${fieldErrorText(f)}`;
  }
  return isApiError(e) ? errorTextWithTrace(e) : errorMessage(e);
};

export const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const user = useAuthStore((s) => s.user);
  const login = useAuthStore((s) => s.login);
  const lostReason = useAuthStore((s) => s.lostReason);
  const location = useLocation();

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
    </div>
  );
};
