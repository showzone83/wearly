// 관리 화면 공통 부품. UI 라이브러리 없이 styles.css의 클래스만 쓴다.
import { useEffect, type ButtonHTMLAttributes, type ReactNode } from 'react';

import { errorMessage, isApiError } from '@/api';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

export function Button({
  variant = 'secondary',
  size,
  busy,
  className,
  children,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; size?: 'sm'; busy?: boolean }) {
  return (
    <button
      type="button"
      className={['btn', `btn-${variant}`, size === 'sm' ? 'btn-sm' : '', className ?? ''].join(' ')}
      disabled={disabled || busy}
      {...rest}
    >
      {busy ? <span className="spinner" aria-hidden /> : null}
      {children}
    </button>
  );
}

export function PageHeader({ title, desc, actions }: { title: string; desc?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1 className="page-title">{title}</h1>
        {desc ? <p className="page-desc">{desc}</p> : null}
      </div>
      {actions ? <div className="page-actions">{actions}</div> : null}
    </header>
  );
}

export function Card({ title, actions, children, className }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`card ${className ?? ''}`}>
      {title || actions ? (
        <div className="card-head">
          {title ? <h2 className="card-title">{title}</h2> : <span />}
          {actions}
        </div>
      ) : null}
      {children}
    </section>
  );
}

type Tone = 'green' | 'gray' | 'red' | 'beige' | 'ink';

export function Badge({ tone = 'gray', children, title }: { tone?: Tone; children: ReactNode; title?: string }) {
  return (
    <span className={`badge badge-${tone}`} title={title}>
      {children}
    </span>
  );
}

export function Loading({ label = '불러오는 중…' }: { label?: string }) {
  return (
    <div className="loading">
      <span className="spinner" aria-hidden /> {label}
    </div>
  );
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null;
  const code = isApiError(error) ? error.code : null;
  return (
    <div className="alert alert-error" role="alert">
      <div>
        <strong>{errorMessage(error)}</strong>
        {code ? <span className="alert-code">{code}</span> : null}
      </div>
      {onRetry ? (
        <Button size="sm" onClick={onRetry}>
          다시 시도
        </Button>
      ) : null}
    </div>
  );
}

export function Notice({ tone = 'info', children }: { tone?: 'info' | 'warn' | 'error' | 'success'; children: ReactNode }) {
  return <div className={`alert alert-${tone}`}>{children}</div>;
}

export function Empty({ children = '데이터가 없습니다.' }: { children?: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function Field({
  label,
  hint,
  error,
  children,
  required,
}: {
  label: ReactNode;
  hint?: ReactNode;
  error?: string;
  children: ReactNode;
  required?: boolean;
}) {
  return (
    <div className={`field ${error ? 'field-invalid' : ''}`}>
      <div className="field-label">
        {label}
        {required ? <span className="req">*</span> : null}
      </div>
      {children}
      {error ? <div className="field-error">{error}</div> : hint ? <div className="field-hint">{hint}</div> : null}
    </div>
  );
}

export function Switch({
  checked,
  onChange,
  disabled,
  label,
  title,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
  label?: ReactNode;
  title?: string;
}) {
  return (
    <label className={`switch ${disabled ? 'switch-disabled' : ''}`} title={title}>
      <input type="checkbox" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
      <span className="switch-track" aria-hidden>
        <span className="switch-thumb" />
      </span>
      {label ? <span className="switch-label">{label}</span> : null}
    </label>
  );
}

export function Segmented<T extends string | number>({
  value,
  options,
  onChange,
}: {
  value: T | undefined;
  options: { value: T; label: ReactNode }[];
  onChange: (v: T) => void;
}) {
  return (
    <div className="segmented" role="radiogroup">
      {options.map((o) => (
        <button
          type="button"
          role="radio"
          aria-checked={o.value === value}
          key={String(o.value)}
          className={o.value === value ? 'on' : ''}
          onClick={() => onChange(o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Pagination({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / Math.max(1, size)));
  return (
    <div className="pagination">
      <span className="muted">
        총 {total.toLocaleString('ko-KR')}건 · {page + 1}/{pages}쪽
      </span>
      <div className="row gap-xs">
        <Button size="sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          이전
        </Button>
        <Button size="sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
          다음
        </Button>
      </div>
    </div>
  );
}

/** 오른쪽에서 나오는 패널 (추가·수정 폼, 상세 보기) */
export function Drawer({ title, onClose, children, footer, wide }: { title: ReactNode; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="drawer-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <aside className={`drawer ${wide ? 'drawer-wide' : ''}`} role="dialog" aria-modal="true">
        <div className="drawer-head">
          <h2 className="drawer-title">{title}</h2>
          <button type="button" className="icon-btn" aria-label="닫기" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="drawer-body">{children}</div>
        {footer ? <div className="drawer-foot">{footer}</div> : null}
      </aside>
    </div>
  );
}

export function JsonView({ value, maxHeight }: { value: unknown; maxHeight?: number }) {
  const text = value === undefined ? '—' : JSON.stringify(value, null, 2);
  return (
    <pre className="json" style={maxHeight ? { maxHeight } : undefined}>
      {text}
    </pre>
  );
}

export function Stat({ label, value, sub }: { label: string; value: ReactNode; sub?: ReactNode }) {
  return (
    <div className="stat">
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {sub ? <div className="stat-sub">{sub}</div> : null}
    </div>
  );
}
