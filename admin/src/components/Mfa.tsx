// 관리자 2단계 인증(TOTP, doc/22) 부품: 로그인 화면(pages/LoginPage)과 내 계정 화면(pages/MyAccountPage)이 함께 쓴다.
import { useEffect, useState } from 'react';

import type { IMfaSetupResponse } from '@/api';

import { Button, Notice } from '@/components/ui';

import { toDataURL } from 'qrcode';

import { fmtDateTime } from '@/lib/format';
import { formatSecret, normalizeTotp, recoveryCodesFileText, TOTP_DIGITS } from '@/lib/mfa';

/** 클립보드에 쓴다. 안 되는 환경(http 원격 주소 등)이면 false */
const copyText = async (text: string): Promise<boolean> => {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
};

/** 복사 버튼: 누르면 잠깐 "복사했어요" */
const CopyButton = ({ text, label }: { text: string; label: string }) => {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle');

  const handleCopy = async () => {
    const ok = await copyText(text);
    setState(ok ? 'copied' : 'failed');
    setTimeout(() => setState('idle'), 1800);
  };

  return (
    <Button size="sm" onClick={() => void handleCopy()}>
      {state === 'copied' ? '복사했어요' : state === 'failed' ? '복사하지 못했어요' : label}
    </Button>
  );
};

/** 인증 앱의 6자리 코드 입력칸. 숫자만 받는다 */
export const TotpInput = ({
  value,
  onChange,
  autoFocus,
}: {
  value: string;
  onChange: (v: string) => void;
  autoFocus?: boolean;
}) => {
  return (
    <input
      className="totp-input"
      inputMode="numeric"
      autoComplete="one-time-code"
      placeholder="123456"
      maxLength={TOTP_DIGITS + 2}
      value={value}
      autoFocus={autoFocus}
      onChange={(e) => onChange(normalizeTotp(e.target.value))}
      aria-label="인증 코드 6자리"
    />
  );
};

/**
 * 등록 시작 응답(secret·otpauthUrl)을 QR과 수동 입력 키로 보여 준다.
 * QR은 브라우저에서 만든다(qrcode) — 비밀을 다른 서버로 보내지 않는다.
 */
export const MfaSetupView = ({ setup }: { setup: IMfaSetupResponse }) => {
  /** 만든 QR 이미지(data URL)와 그 원본 주소. 주소가 바뀌면 새로 만든다 */
  const [qr, setQr] = useState<{ url: string; src: string | null } | null>(null);

  useEffect(() => {
    let alive = true;
    toDataURL(setup.otpauthUrl, { margin: 1, width: 192, errorCorrectionLevel: 'M' })
      .then((src) => {
        if (alive) setQr({ url: setup.otpauthUrl, src });
      })
      .catch(() => {
        if (alive) setQr({ url: setup.otpauthUrl, src: null });
      });
    return () => {
      alive = false;
    };
  }, [setup.otpauthUrl]);

  const current = qr?.url === setup.otpauthUrl ? qr : null;

  return (
    <div className="mfa-setup">
      <ol className="mfa-steps">
        <li>휴대폰 인증 앱(Google Authenticator, Microsoft Authenticator, 1Password 등)에서 QR을 찍으세요.</li>
        <li>QR을 찍을 수 없으면 아래 키를 직접 입력하세요 (시간 기반, 6자리).</li>
        <li>앱에 나온 6자리 코드를 입력해 확인하세요.</li>
      </ol>
      <div className="mfa-qr">
        {current?.src ? (
          <img src={current.src} width={192} height={192} alt="인증 앱 등록 QR 코드" />
        ) : current ? (
          <span className="muted small">QR을 만들지 못했어요. 아래 키를 직접 입력하세요.</span>
        ) : (
          <span className="spinner" aria-hidden />
        )}
      </div>
      <div className="field">
        <span className="field-label">수동 입력 키</span>
        <div className="row gap-sm wrap">
          <code className="mfa-secret">{formatSecret(setup.secret)}</code>
          <CopyButton text={setup.secret} label="키 복사" />
        </div>
      </div>
    </div>
  );
};

/**
 * 처음 등록·다시 등록 때 한 번만 보여 주는 복구 코드 10개. "저장했어요"를 체크해야 다음으로 간다.
 * 코드는 이 화면을 벗어나면 다시 볼 수 없다.
 */
export const RecoveryCodesView = ({
  codes,
  email,
  doneLabel,
  onDone,
}: {
  codes: string[];
  email: string | null;
  doneLabel: string;
  onDone: () => void;
}) => {
  const [saved, setSaved] = useState(false);

  const handleDownload = () => {
    const text = recoveryCodesFileText(codes, email, fmtDateTime(new Date().toISOString()));
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'wearly-admin-recovery-codes.txt';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  return (
    <div className="mfa-recovery">
      <Notice tone="warn">
        복구 코드는 <strong>지금 한 번만</strong> 보여 줍니다. 휴대폰을 잃어버려 인증 앱을 쓸 수 없을 때 코드 하나로 한
        번 로그인할 수 있어요. 비밀번호 관리자 등 안전한 곳에 저장해 두세요.
      </Notice>
      <ol className="mfa-codes">
        {codes.map((c) => (
          <li key={c} className="mono">
            {c}
          </li>
        ))}
      </ol>
      <div className="row gap-sm wrap">
        <CopyButton text={codes.join('\n')} label="모두 복사" />
        <Button size="sm" onClick={handleDownload}>
          내려받기 (.txt)
        </Button>
      </div>
      <label className="check">
        <input type="checkbox" checked={saved} onChange={(e) => setSaved(e.target.checked)} />
        복구 코드를 안전한 곳에 저장했어요
      </label>
      <Button variant="primary" className="btn-block" disabled={!saved} onClick={onDone}>
        {doneLabel}
      </Button>
    </div>
  );
};
