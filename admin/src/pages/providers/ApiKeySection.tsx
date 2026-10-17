// AI 공급자 API 키 웹 입력 (doc/07 6장, 쓰기 전용 · 서버에서 AES-GCM 암호화 저장).
//
// 키 값을 다루는 규칙:
// - 키 입력칸은 비제어(uncontrolled) input이다. 값은 React 상태에 두지 않고, 저장 버튼을 누른 순간 DOM에서 한 번 읽고
//   성공·실패와 관계없이 바로 비운다.
// - 읽은 값은 mutation 변수로만 서버에 보낸다(JSON 본문). URL·쿼리 문자열·sessionStorage·localStorage·쿼리 캐시 키에 넣지 않는다.
// - mutation은 gcTime 0 + 끝나면 reset()이라 MutationCache에도 변수가 남지 않는다.
// - 입력칸에 name을 붙이지 않는다. 만에 하나 스크립트가 멈춰 브라우저가 폼을 기본 제출(GET)해도 쿼리 문자열에 실리지 않게.
// - 오류를 로그로 남기지 않는다(console 금지). 화면에는 서버 오류 메시지만 보여 준다(서버가 키 조각을 지운 문구).
import { useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useRef, useState, type FormEvent, type ReactNode } from 'react';

import { api, errorMessage, isApiError, type AiProvider } from '@/api';
import { useToast } from '@/components/toast';
import { Badge, Button, Field, Modal, Notice } from '@/components/ui';
import { fmtDateTime } from '@/lib/format';
import { qk, useProviders } from '@/lib/queries';

const SECRET_MISSING_TEXT = '서버에 WEARLY_SECRET_KEY가 없어 웹 입력을 쓸 수 없어요';
/** 07 6장: 앞뒤 공백을 지운 뒤 20~300자, 공백·줄바꿈 없음 */
const KEY_MIN = 20;
const KEY_MAX = 300;

const masked = (last4: string | null) => `••••${last4 ?? ''}`;

/** 저장·삭제 응답(공급자 행)을 목록 캐시에 바로 반영하고, 목록·변경 이력을 다시 읽는다 */
function applyRow(queryClient: QueryClient, row: AiProvider | null) {
  if (row) {
    queryClient.setQueryData<AiProvider[]>(qk.providers, (list) => list?.map((p) => (p.providerId === row.providerId ? { ...p, ...row, today: row.today ?? p.today } : p)));
  }
  void queryClient.invalidateQueries({ queryKey: qk.providers });
  void queryClient.invalidateQueries({ queryKey: ['audit'] });
}

interface KeyFormErrors {
  apiKey?: string;
  currentPassword?: string;
  form?: ReactNode;
}

/** 서버 오류 → 입력칸별 안내 (07 6장 오류 코드) */
function toKeyErrors(e: unknown): KeyFormErrors {
  if (!isApiError(e)) return { form: errorMessage(e) };
  switch (e.code) {
    case 'REAUTH_FAILED':
      return { currentPassword: '비밀번호가 맞지 않아요. 다시 입력해 주세요.' };
    case 'API_KEY_INVALID':
      return { apiKey: `키가 유효하지 않아요. ${e.message}` };
    case 'SECRET_KEY_MISSING':
      return { form: `${SECRET_MISSING_TEXT}. 서버 관리자에게 WEARLY_SECRET_KEY 설정을 요청하거나, 환경변수 키를 쓰세요.` };
    case 'VALIDATION_FAILED': {
      const out: KeyFormErrors = {};
      const rest: string[] = [];
      for (const f of e.fieldErrors) {
        if (f.field === 'apiKey') out.apiKey = f.message;
        else if (f.field === 'currentPassword') out.currentPassword = f.message;
        else rest.push(`${f.field}: ${f.message}`);
      }
      if (rest.length) out.form = rest.join(' / ');
      if (!out.apiKey && !out.currentPassword && !out.form) out.form = e.message;
      return out;
    }
    case 'NOT_FOUND':
      return { form: '공급자를 찾을 수 없어요. 삭제됐거나, 서버가 아직 웹 키 입력을 지원하지 않아요.' };
    default:
      return { form: `${e.message} (${e.code})` };
  }
}

/** 표에 넣는 짧은 출처 표시 */
export function KeySourceBadge({ provider: p }: { provider: AiProvider }) {
  if (p.keySource === 'DB') {
    return (
      <span className="key-cell" title={`웹에서 넣은 키 · ${fmtDateTime(p.keyUpdatedAt)}${p.keyUpdatedBy ? ` · ${p.keyUpdatedBy}` : ''}`}>
        <Badge tone="green">웹 입력</Badge>
        <span className="mono small">{masked(p.keyLast4)}</span>
      </span>
    );
  }
  if (p.keySource === 'ENV') {
    return (
      <span className="key-cell" title="서버 환경변수의 키를 씁니다">
        <Badge tone="green">환경변수</Badge>
        <span className="mono small">{p.apiKeyEnv}</span>
      </span>
    );
  }
  return (
    <span className="key-cell" title={`웹 키도 없고 ${p.apiKeyEnv} 값도 없습니다`}>
      <Badge tone="red">없음</Badge>
      <span className="mono small muted">{p.apiKeyEnv}</span>
    </span>
  );
}

/** 공급자 상세(수정 패널)의 "API 키" 영역. 목록 캐시에서 최신 행을 읽어 저장·삭제 직후 바로 바뀐다 */
export function ApiKeySection({ providerId, fallback }: { providerId: string; fallback: AiProvider }) {
  const { data } = useProviders();
  const p = data?.find((x) => x.providerId === providerId) ?? fallback;
  const [dialog, setDialog] = useState<'set' | 'delete' | null>(null);
  const storeOff = p.secretStoreEnabled === false;

  return (
    <section className="key-box" aria-label="API 키">
      <h3 className="section-title">API 키</h3>
      <div className="key-source">
        {p.keySource === 'DB' ? (
          <>
            <Badge tone="green">웹 입력</Badge>
            <span className="mono">{masked(p.keyLast4)}</span>
            <span className="muted small">{fmtDateTime(p.keyUpdatedAt)}</span>
            {p.keyUpdatedBy ? <span className="muted small">{p.keyUpdatedBy}</span> : null}
          </>
        ) : p.keySource === 'ENV' ? (
          <>
            <Badge tone="green">환경변수</Badge>
            <span className="mono">{p.apiKeyEnv}</span>
          </>
        ) : (
          <>
            <Badge tone="red">없음</Badge>
            <span className="muted small">
              웹 키도 없고 서버 환경변수 <span className="mono">{p.apiKeyEnv}</span>도 비어 있어요.
            </span>
          </>
        )}
      </div>
      {storeOff ? <Notice tone="warn">{SECRET_MISSING_TEXT}. 환경변수 키는 그대로 쓸 수 있어요.</Notice> : null}
      <div className="key-actions">
        <Button size="sm" variant="primary" disabled={storeOff} title={storeOff ? SECRET_MISSING_TEXT : undefined} onClick={() => setDialog('set')}>
          키 입력·교체
        </Button>
        {p.keySource === 'DB' ? (
          <Button size="sm" variant="ghost" className="text-danger" onClick={() => setDialog('delete')}>
            웹 키 삭제(환경변수로 되돌리기)
          </Button>
        ) : null}
      </div>
      <p className="muted small">
        웹에서 넣은 키가 있으면 환경변수 <span className="mono">{p.apiKeyEnv}</span>보다 먼저 씁니다. 저장한 키는 다시 볼 수 없고 마지막 4자만 보여요.
      </p>
      {dialog === 'set' ? <ApiKeyModal provider={p} onClose={() => setDialog(null)} /> : null}
      {dialog === 'delete' ? <DeleteKeyModal provider={p} onClose={() => setDialog(null)} /> : null}
    </section>
  );
}

/** 키 입력·교체 대화상자 */
function ApiKeyModal({ provider, onClose }: { provider: AiProvider; onClose: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const keyRef = useRef<HTMLInputElement>(null);
  const pwRef = useRef<HTMLInputElement>(null);
  const [password, setPassword] = useState('');
  const [verify, setVerify] = useState(true);
  const [errors, setErrors] = useState<KeyFormErrors>({});

  // 변수({ apiKey, ... })가 캐시에 남지 않도록 gcTime 0, 끝나면 reset()
  const save = useMutation({
    mutationFn: (vars: { apiKey: string; currentPassword: string; verify: boolean }) => api.providers.setApiKey(provider.providerId, vars),
    gcTime: 0,
    // 대화상자를 닫은 뒤에 끝나도 목록은 반영되도록 훅 수준에서 처리한다
    onSuccess: (row) => applyRow(queryClient, row),
    onError: (e) => {
      if (isApiError(e) && e.code === 'SECRET_KEY_MISSING') void queryClient.invalidateQueries({ queryKey: qk.providers });
    },
  });

  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    const input = keyRef.current;
    const apiKey = (input?.value ?? '').trim();
    // 성공·실패와 관계없이 입력칸을 바로 비운다
    if (input) input.value = '';

    const next: KeyFormErrors = {};
    if (!apiKey) next.apiKey = 'API 키를 붙여 넣으세요.';
    else if (/\s/.test(apiKey)) next.apiKey = '키 안에 공백이나 줄바꿈이 있어요. 키만 다시 붙여 넣으세요.';
    else if (apiKey.length < KEY_MIN || apiKey.length > KEY_MAX) next.apiKey = `키 길이가 ${KEY_MIN}~${KEY_MAX}자가 아니에요. 키 전체를 다시 붙여 넣으세요.`;
    if (!password) next.currentPassword = '내 비밀번호를 입력하세요.';
    setErrors(next);
    if (next.apiKey || next.currentPassword) {
      (next.apiKey ? keyRef : pwRef).current?.focus();
      return;
    }

    save.mutate(
      { apiKey, currentPassword: password, verify },
      {
        onSuccess: (row) => {
          toast(`${provider.displayName} 키를 저장했어요${row?.keyLast4 ? ` (${masked(row.keyLast4)})` : ''}${verify ? '' : ' · 유효성 확인 안 함'}`);
          onClose();
        },
        onError: (e) => {
          const mapped = toKeyErrors(e);
          setErrors(mapped);
          if (mapped.currentPassword) {
            setPassword('');
            pwRef.current?.focus();
          } else if (mapped.apiKey) {
            keyRef.current?.focus();
          }
        },
        onSettled: () => save.reset(),
      },
    );
  };

  return (
    <Modal
      title={`API 키 입력·교체 · ${provider.displayName}`}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button variant="primary" type="submit" form="api-key-form" busy={save.isPending}>
            {verify ? '확인 후 저장' : '저장'}
          </Button>
        </>
      }
    >
      <form id="api-key-form" className="form" method="post" onSubmit={submit} noValidate autoComplete="off">
        <Notice tone="info">저장한 키는 다시 볼 수 없어요. 서버에 암호화해 보관해요.</Notice>
        {errors.form ? <Notice tone="error">{errors.form}</Notice> : null}
        <Field
          label="API 키"
          required
          error={errors.apiKey}
          hint={
            provider.keySource === 'DB'
              ? `지금 웹 키(${masked(provider.keyLast4)})를 새 키로 바꿉니다.`
              : `저장하면 환경변수 ${provider.apiKeyEnv}보다 이 키를 먼저 씁니다.`
          }
        >
          {/* 비제어 입력: 값을 React 상태에 두지 않는다. name 없음, 보기 토글 없음, 붙여넣기 허용 */}
          <input
            ref={keyRef}
            className="mono"
            type="password"
            autoComplete="off"
            spellCheck={false}
            autoCapitalize="off"
            autoCorrect="off"
            data-1p-ignore=""
            data-lpignore="true"
            data-form-type="other"
            maxLength={1000}
            placeholder="붙여 넣기"
            aria-invalid={!!errors.apiKey}
            autoFocus
          />
        </Field>
        <Field label="내 비밀번호" required error={errors.currentPassword} hint="키를 바꾸려면 로그인한 관리자의 비밀번호를 다시 확인해요.">
          <input
            ref={pwRef}
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={!!errors.currentPassword}
          />
        </Field>
        <label className="check">
          <input type="checkbox" checked={verify} onChange={(e) => setVerify(e.target.checked)} />
          <span>저장 전 유효성 확인 (그 키로 모델 목록을 한 번 조회해 봐요)</span>
        </label>
        {!verify ? <p className="muted small">확인하지 않으면 틀린 키도 그대로 저장되고, 실제 분석 때 실패합니다.</p> : null}
      </form>
    </Modal>
  );
}

/** 웹 키 삭제 확인 대화상자 */
function DeleteKeyModal({ provider, onClose }: { provider: AiProvider; onClose: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const pwRef = useRef<HTMLInputElement>(null);
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<KeyFormErrors>({});

  const remove = useMutation({
    mutationFn: (vars: { currentPassword: string }) => api.providers.deleteApiKey(provider.providerId, vars),
    gcTime: 0,
    onSuccess: (row) => applyRow(queryClient, row),
  });

  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    if (!password) {
      setErrors({ currentPassword: '내 비밀번호를 입력하세요.' });
      pwRef.current?.focus();
      return;
    }
    setErrors({});
    remove.mutate(
      { currentPassword: password },
      {
        onSuccess: (row) => {
          const now = row?.keySource ?? null;
          toast(
            now === 'ENV'
              ? `웹 키를 지웠어요. 이제 환경변수 ${provider.apiKeyEnv}의 키를 씁니다.`
              : now === 'NONE'
                ? `웹 키를 지웠어요. ${provider.apiKeyEnv} 값도 없어 이 공급자는 지금 쓸 수 없어요.`
                : '웹 키를 지웠어요.',
          );
          onClose();
        },
        onError: (e) => {
          const mapped = toKeyErrors(e);
          setErrors(mapped);
          if (mapped.currentPassword) {
            setPassword('');
            pwRef.current?.focus();
          }
        },
        onSettled: () => remove.reset(),
      },
    );
  };

  return (
    <Modal
      title={`웹 키 삭제 · ${provider.displayName}`}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button variant="danger" type="submit" form="api-key-delete-form" busy={remove.isPending}>
            웹 키 삭제
          </Button>
        </>
      }
    >
      <form id="api-key-delete-form" className="form" method="post" onSubmit={submit} noValidate>
        <p>
          웹에서 넣은 키 <span className="mono">{masked(provider.keyLast4)}</span>를 지우고 서버 환경변수 <span className="mono">{provider.apiKeyEnv}</span>의 키로
          되돌립니다. 환경변수가 비어 있으면 이 공급자는 쓸 수 없게 돼요.
        </p>
        {errors.form ? <Notice tone="error">{errors.form}</Notice> : null}
        <Field label="내 비밀번호" required error={errors.currentPassword}>
          <input ref={pwRef} type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} autoFocus />
        </Field>
      </form>
    </Modal>
  );
}
