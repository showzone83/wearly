// 내 계정: 비밀번호 변경, 2단계 인증 상태·켜기·다시 등록 (doc/22)
// 비밀번호·인증 코드는 React 상태에만 잠깐 두고 쿼리 캐시·URL·저장소에 남기지 않는다(성공·실패 뒤 입력칸을 비운다).
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';

import { api, isApiError, tokenStore } from '@/api';
import type { IMfaSetupResponse } from '@/api';

import { MfaSetupView, RecoveryCodesView, TotpInput } from '@/components/Mfa';
import { Badge, Button, Card, Drawer, ErrorBox, Field, Loading, Notice, PageHeader, Segmented } from '@/components/ui';

import { useAuthStore } from '@/stores/useAuthStore';
import { useConfirmStore } from '@/stores/useConfirmStore';
import { useToastStore } from '@/stores/useToastStore';

import { useQuery, useQueryClient } from '@tanstack/react-query';

import { errorTextWithTrace, fieldErrorOf } from '@/lib/errorText';
import { fmtDateTime } from '@/lib/format';
import { isTotpCode, lowRecoveryCodesText, normalizeRecoveryCode, passwordRuleError } from '@/lib/mfa';
import { nextChangeText } from '@/lib/passwordDue';
import { isUnsupportedApi, qk, useMyPasswordStatus } from '@/lib/queries';

type TPasswordField = 'currentPassword' | 'newPassword' | 'confirm';

/** 비밀번호 변경 */
const PasswordCard = () => {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<TPasswordField, string>>>({});
  const [error, setError] = useState<unknown>(null);

  const queryClient = useQueryClient();
  const toast = useToastStore((s) => s.show);
  const { data: status } = useMyPasswordStatus();

  /** 다음에 바꿀 날 (변경 주기, doc/28) */
  const nextChange = nextChangeText(status);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: Partial<Record<TPasswordField, string>> = {};
    if (!current) errs.currentPassword = '지금 비밀번호를 입력해 주세요.';
    const rule = passwordRuleError(next);
    if (!next) errs.newPassword = '새 비밀번호를 입력해 주세요.';
    else if (rule) errs.newPassword = rule;
    else if (next === current) errs.newPassword = '지금 비밀번호와 다른 비밀번호를 입력해 주세요.';
    if (!errs.newPassword && confirm !== next) errs.confirm = '새 비밀번호와 같게 입력해 주세요.';
    setFieldErrors(errs);
    setError(null);
    if (Object.keys(errs).length) return;

    setBusy(true);
    try {
      await api.me.changePassword({ currentPassword: current, newPassword: next });
      setCurrent('');
      setNext('');
      setConfirm('');
      toast('비밀번호를 바꿨어요. 다른 기기의 로그인은 모두 끊겼어요.');
      void queryClient.invalidateQueries({ queryKey: ['sessions'] });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
      // 변경 주기를 지금부터 다시 센다 → 화면 위 안내 띠가 사라진다
      void queryClient.invalidateQueries({ queryKey: qk.myPassword });
    } catch (err) {
      setCurrent('');
      if (isApiError(err) && err.code === 'REAUTH_FAILED') {
        setFieldErrors({ currentPassword: '지금 비밀번호가 맞지 않아요.' });
      } else if (isApiError(err) && err.code === 'VALIDATION_FAILED' && err.fieldErrors.length) {
        setFieldErrors({
          currentPassword: fieldErrorOf(err.fieldErrors, 'currentPassword'),
          newPassword: fieldErrorOf(err.fieldErrors, 'newPassword'),
        });
      } else {
        setError(err);
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="비밀번호 변경">
      <form className="form w-form" onSubmit={submit} noValidate>
        <p className="muted small">
          바꾸면 다른 기기(다른 브라우저·앱)의 내 로그인은 모두 끊기고, 지금 화면은 그대로예요.
          {nextChange ? (
            <>
              <br />
              {nextChange}
            </>
          ) : null}
        </p>
        <Field label="지금 비밀번호" error={fieldErrors.currentPassword} required>
          <input
            type="password"
            autoComplete="current-password"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
          />
        </Field>
        <Field label="새 비밀번호" hint="8~64자, 영문과 숫자를 함께" error={fieldErrors.newPassword} required>
          <input type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
        </Field>
        <Field label="새 비밀번호 확인" error={fieldErrors.confirm} required>
          <input
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
        </Field>
        <ErrorBox error={error} />
        <div className="row">
          <Button type="submit" variant="primary" busy={busy}>
            비밀번호 바꾸기
          </Button>
        </div>
      </form>
    </Card>
  );
};

type TEnrollStep = 'reset' | 'setup' | 'codes';

/**
 * 2단계 인증 켜기·다시 등록 패널.
 * 다시 등록: 지금 비밀번호 + 코드(또는 복구 코드)로 초기화 → 등록 시작(QR) → 코드 확인 → 새 복구 코드.
 */
const MfaEnrollDrawer = ({ reenroll, onClose }: { reenroll: boolean; onClose: () => void }) => {
  const [step, setStep] = useState<TEnrollStep>(reenroll ? 'reset' : 'setup');
  /** 이번 패널에서 초기화까지 마쳤다 (닫으면 2단계 인증이 꺼진 채로 남는다) */
  const [resetDone, setResetDone] = useState(false);
  // 초기화 입력
  const [password, setPassword] = useState('');
  const [mode, setMode] = useState<'totp' | 'recovery'>('totp');
  const [code, setCode] = useState('');
  const [recovery, setRecovery] = useState('');
  // 등록
  const [setup, setSetup] = useState<IMfaSetupResponse | null>(null);
  const [setupNonce, setSetupNonce] = useState(0);
  const [setupError, setSetupError] = useState<unknown>(null);
  const [enableCode, setEnableCode] = useState('');
  const [codes, setCodes] = useState<string[] | null>(null);
  // 공통
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | undefined>(undefined);

  const queryClient = useQueryClient();
  const email = useAuthStore((s) => s.user?.email ?? null);
  const setRecoveryCodesLeft = useAuthStore((s) => s.setRecoveryCodesLeft);
  const toast = useToastStore((s) => s.show);
  const askConfirm = useConfirmStore((s) => s.ask);

  useEffect(() => {
    if (step !== 'setup') return;
    let alive = true;
    api.me
      .mfaSetup()
      .then((res) => {
        if (alive) setSetup(res);
      })
      .catch((e: unknown) => {
        if (alive) setSetupError(e);
      });
    return () => {
      alive = false;
    };
  }, [step, setupNonce]);

  const refreshStatus = () => {
    void queryClient.invalidateQueries({ queryKey: qk.myMfa });
    void queryClient.invalidateQueries({ queryKey: ['audit'] });
    void queryClient.invalidateQueries({ queryKey: ['users'] });
  };
  const handleClose = () => {
    if (step !== 'codes') {
      onClose();
      return;
    }
    void askConfirm({
      title: '복구 코드',
      message: '복구 코드를 저장하지 않고 닫을까요? 이 코드는 다시 볼 수 없어요.',
      confirmLabel: '저장하지 않고 닫기',
      cancelLabel: '돌아가기',
      tone: 'danger',
    }).then((ok) => {
      if (ok) onClose();
    });
  };
  const handleRetrySetup = () => {
    setSetup(null);
    setSetupError(null);
    setEnableCode('');
    setSetupNonce((n) => n + 1);
  };
  const submitReset = async (e: FormEvent) => {
    e.preventDefault();
    const recoveryCode = normalizeRecoveryCode(recovery);
    setPasswordError(password ? undefined : '지금 비밀번호를 입력해 주세요.');
    if (!password) return;
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
      await api.me.mfaReset(
        mode === 'totp' ? { currentPassword: password, code } : { currentPassword: password, recoveryCode },
      );
      setResetDone(true);
      setStep('setup');
      refreshStatus();
      void queryClient.invalidateQueries({ queryKey: ['sessions'] });
    } catch (err) {
      if (isApiError(err) && err.code === 'REAUTH_FAILED') setPasswordError('지금 비밀번호가 맞지 않아요.');
      else if (isApiError(err) && err.code === 'MFA_NOT_ENROLLED') {
        // 이미 꺼져 있다 → 바로 등록으로
        setStep('setup');
        refreshStatus();
      } else setError(isApiError(err) ? errorTextWithTrace(err) : String(err));
    } finally {
      setPassword('');
      setCode('');
      setRecovery('');
      setBusy(false);
    }
  };
  const submitEnable = async (e: FormEvent) => {
    e.preventDefault();
    if (!isTotpCode(enableCode)) {
      setError('인증 앱에 나온 6자리 숫자를 입력해 주세요.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await api.me.mfaEnable(enableCode);
      setCodes(res.recoveryCodes);
      setStep('codes');
      setRecoveryCodesLeft(null);
      refreshStatus();
      toast('2단계 인증을 켰어요. 다음 로그인부터 인증 앱의 코드가 필요해요.');
    } catch (err) {
      setError(isApiError(err) ? errorTextWithTrace(err) : String(err));
      // 등록용 비밀이 만료됐으면 새 QR부터
      if (isApiError(err) && err.code === 'MFA_SETUP_REQUIRED') handleRetrySetup();
    } finally {
      setEnableCode('');
      setBusy(false);
    }
  };

  return (
    <Drawer
      title={reenroll ? '2단계 인증 다시 등록' : '2단계 인증 켜기'}
      onClose={handleClose}
      footer={
        step === 'codes' ? null : (
          <>
            <Button onClick={handleClose}>{resetDone ? '닫기 (나중에 등록)' : '취소'}</Button>
            {step === 'reset' ? (
              <Button variant="danger" type="submit" form="mfa-reset-form" busy={busy}>
                초기화하고 다시 등록
              </Button>
            ) : (
              <Button variant="primary" type="submit" form="mfa-enable-form" busy={busy} disabled={!setup}>
                확인하고 켜기
              </Button>
            )}
          </>
        )
      }
    >
      {step === 'reset' ? (
        <form id="mfa-reset-form" className="form" onSubmit={submitReset} noValidate>
          <p className="muted">
            지금 인증 앱 등록과 복구 코드를 지우고 새로 등록합니다. 다른 기기의 내 로그인은 모두 끊기고, 지금 화면은
            그대로예요. 휴대폰을 바꿨거나 복구 코드를 다 썼을 때 쓰세요.
          </p>
          <Field label="지금 비밀번호" error={passwordError} required>
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoFocus
            />
          </Field>
          <div className="mfa-code-tabs">
            <Segmented
              value={mode}
              options={[
                { value: 'totp', label: '인증 앱 코드' },
                { value: 'recovery', label: '복구 코드' },
              ]}
              onChange={(v) => {
                setMode(v);
                setError(null);
              }}
            />
          </div>
          {mode === 'totp' ? (
            <Field label="인증 코드" hint="지금 등록된 인증 앱의 6자리 코드" required>
              <TotpInput value={code} onChange={setCode} />
            </Field>
          ) : (
            <Field label="복구 코드" hint="인증 앱을 쓸 수 없으면 저장해 둔 복구 코드 하나" required>
              <input
                className="mono"
                autoComplete="off"
                spellCheck={false}
                placeholder="abcde-fghjk"
                value={recovery}
                onChange={(e) => setRecovery(e.target.value)}
              />
            </Field>
          )}
          {error ? <Notice tone="error">{error}</Notice> : null}
        </form>
      ) : step === 'setup' ? (
        <form id="mfa-enable-form" className="form" onSubmit={submitEnable} noValidate>
          {resetDone ? (
            <Notice tone="warn">
              이전 등록을 지웠어요. 지금 닫으면 2단계 인증이 꺼진 채로 남고, 서버 설정이 필수라면 다음 로그인 때 다시
              등록하게 돼요.
            </Notice>
          ) : null}
          {setupError ? <ErrorBox error={setupError} onRetry={handleRetrySetup} /> : null}
          {setup ? <MfaSetupView setup={setup} /> : !setupError ? <Loading label="등록 준비 중…" /> : null}
          <Field label="인증 코드" required>
            <TotpInput value={enableCode} onChange={setEnableCode} />
          </Field>
          {error ? <Notice tone="error">{error}</Notice> : null}
          {setup ? (
            <button type="button" className="link-btn small mfa-code-tabs" onClick={handleRetrySetup}>
              새 QR 받기
            </button>
          ) : null}
        </form>
      ) : codes ? (
        <RecoveryCodesView codes={codes} email={email} doneLabel="저장했어요, 닫기" onDone={onClose} />
      ) : null}
    </Drawer>
  );
};

export const MyAccountPage = () => {
  /** 2단계 인증 패널: 켜기(enable) 또는 다시 등록(reenroll) */
  const [flow, setFlow] = useState<'enable' | 'reenroll' | null>(null);

  const user = useAuthStore((s) => s.user);
  const { data, error, isLoading, refetch } = useQuery({
    queryKey: qk.myMfa,
    queryFn: () => api.me.mfaStatus(),
  });

  const unsupported = isUnsupportedApi(error);
  const low = data?.enrolled ? lowRecoveryCodesText(data.recoveryCodesLeft) : null;
  /** 회사 계정(SSO)으로 들어온 세션이면 그 공급자 ID (doc/22 4.2) */
  const ssoProvider = tokenStore.getSsoProvider();

  return (
    <>
      <PageHeader title="내 계정" desc="로그인한 관리자 계정의 비밀번호와 2단계 인증(인증 앱 코드)을 관리합니다." />

      <Card title="계정">
        <dl className="kv">
          <dt>이름</dt>
          <dd>{user?.name ?? '—'}</dd>
          <dt>이메일</dt>
          <dd>{user?.email ?? '—'}</dd>
          <dt>회원 ID</dt>
          <dd className="mono">{user?.id ?? '—'}</dd>
          <dt>로그인 방식</dt>
          <dd>{ssoProvider ? `회사 계정 (${ssoProvider})` : '이메일·비밀번호'}</dd>
        </dl>
        {ssoProvider ? (
          <Notice tone="info">
            회사 계정으로 들어왔어요. 회사 계정 비밀번호와 2단계 인증은 회사 계정 관리(IdP)에서 바꿉니다. 회사 계정으로
            처음 만든 관리자는 이 사이트의 비밀번호가 없어 아래 비밀번호 변경·2단계 인증은 쓸 수 없어요.
          </Notice>
        ) : null}
      </Card>

      <PasswordCard />

      <Card
        title="2단계 인증"
        actions={
          data ? (
            data.enrolled ? (
              <Button onClick={() => setFlow('reenroll')}>2단계 인증 다시 등록</Button>
            ) : (
              <Button variant="primary" onClick={() => setFlow('enable')}>
                2단계 인증 켜기
              </Button>
            )
          ) : null
        }
      >
        {unsupported ? (
          <Notice tone="warn">
            서버가 아직 이 기능을 모릅니다. 백엔드에 2단계 인증 API(<span className="mono">GET /admin/me/mfa</span>,
            doc/22)가 들어가면 여기에 보입니다.
          </Notice>
        ) : (
          <ErrorBox error={error} onRetry={() => void refetch()} />
        )}
        {isLoading ? <Loading /> : null}
        {data ? (
          <>
            <dl className="kv">
              <dt>상태</dt>
              <dd>
                {data.enrolled ? <Badge tone="green">켜짐</Badge> : <Badge tone="gray">꺼짐</Badge>}{' '}
                <span className="muted small">
                  {data.enrolled ? '로그인할 때 인증 앱의 코드를 확인해요.' : '비밀번호만으로 로그인해요.'}
                </span>
              </dd>
              <dt>등록일</dt>
              <dd>{data.enrolled ? fmtDateTime(data.enrolledAt) : '—'}</dd>
              <dt>남은 복구 코드</dt>
              <dd>
                {data.enrolled && data.recoveryCodesLeft !== null ? `${data.recoveryCodesLeft}개 (처음 10개)` : '—'}
              </dd>
              <dt>서버 설정</dt>
              <dd>
                {data.required ? (
                  <>
                    <Badge tone="ink">필수</Badge>{' '}
                    <span className="muted small">모든 관리자가 등록해야 로그인할 수 있어요.</span>
                  </>
                ) : (
                  <>
                    <Badge tone="gray">선택</Badge>{' '}
                    <span className="muted small">켜면 다음 로그인부터 코드가 필요해요.</span>
                  </>
                )}
              </dd>
            </dl>
            {low ? <Notice tone="warn">{low}</Notice> : null}
            {!data.enrolled && data.required ? (
              <Notice tone="warn">
                서버 설정이 필수인데 등록돼 있지 않아요. 지금 등록하지 않으면 다음 로그인 때 등록 화면이 나와요.
              </Notice>
            ) : null}
          </>
        ) : null}
      </Card>

      {flow ? <MfaEnrollDrawer key={flow} reenroll={flow === 'reenroll'} onClose={() => setFlow(null)} /> : null}
    </>
  );
};
