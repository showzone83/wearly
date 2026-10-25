// 탈퇴 기록 (doc/27 4단계): 최근 N일 요약(상태별·사유별), 탈퇴 기록 목록(최근 신청순), 탈퇴 예정 회원의 탈퇴 취소·바로 삭제.
// 검색·필터·페이지는 URL 쿼리에 둔다. 기록에는 회원 ID·사유·시각만 있고, 이메일·이름은 회원이 아직 남아 있을 때만 보인다.
import { useState } from 'react';
import type { FormEvent } from 'react';

import { api } from '@/api';
import type { IWithdrawalQuery, IWithdrawalRow, TWithdrawalState } from '@/api';

import { Badge, Button, Card, Empty, ErrorBox, Loading, Notice, PageHeader, Pagination, Stat } from '@/components/ui';

import { useConfirmStore } from '@/stores/useConfirmStore';
import { useToastStore } from '@/stores/useToastStore';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { fmtDate, fmtDateTime, fmtNum } from '@/lib/format';
import { isUnsupportedApi, qk } from '@/lib/queries';

const PAGE_SIZE = 30;

const DAY_OPTIONS = [7, 30, 90, 365];

/** 사유 코드 → 앱에서 보이는 문구 (mobile/src/lib/withdrawRules.ts와 같다) */
const REASON_LABEL: Record<string, string> = {
  NOT_USEFUL: '자주 안 써요',
  BAD_RECOMMEND: '추천이 마음에 안 들어요',
  HARD_TO_USE: '쓰기 어려워요',
  PRIVACY: '개인정보가 걱정돼요',
  OTHER_APP: '다른 앱을 쓸래요',
  OTHER: '기타',
};

const STATE_OPTIONS: { value: string; label: string }[] = [
  { value: 'OPEN', label: '탈퇴 예정' },
  { value: 'OVERDUE', label: '삭제 밀림' },
  { value: 'CANCELED', label: '취소' },
  { value: 'PURGED', label: '삭제됨' },
];

const BY_LABEL: Record<string, string> = { USER: '회원', ADMIN: '관리자', SCHEDULER: '예약 삭제' };

const stateBadge = (r: IWithdrawalRow) => {
  const s: TWithdrawalState = r.state;
  if (s === 'OPEN') return r.overdue ? <Badge tone="red">삭제 밀림</Badge> : <Badge tone="beige">탈퇴 예정</Badge>;
  if (s === 'CANCELED') return <Badge tone="green">취소</Badge>;
  if (s === 'PURGED') return <Badge tone="gray">삭제됨</Badge>;
  return <Badge>{s}</Badge>;
};

/** 최근 days일 요약 */
const Summary = ({ days, onDays }: { days: number; onDays: (d: number) => void }) => {
  const { data, error, refetch } = useQuery({
    queryKey: qk.withdrawalSummary(days),
    queryFn: () => api.withdrawals.summary(days),
    staleTime: 0,
  });

  const unsupported = isUnsupportedApi(error);

  if (unsupported) return null;
  return (
    <Card>
      <div className="row gap-sm" style={{ justifyContent: 'space-between', marginBottom: 12 }}>
        <strong style={{ whiteSpace: 'nowrap' }}>최근 {days}일 탈퇴 신청</strong>
        <select
          value={days}
          onChange={(e) => onDays(Number(e.target.value))}
          aria-label="기간"
          style={{ width: 'auto' }}
        >
          {DAY_OPTIONS.map((d) => (
            <option key={d} value={d}>
              최근 {d}일
            </option>
          ))}
        </select>
      </div>
      <ErrorBox error={error} onRetry={() => void refetch()} />
      {data ? (
        <>
          <div className="stats">
            <Stat label="신청" value={fmtNum(data.total)} sub={`${fmtDate(data.from)}부터`} />
            <Stat label="탈퇴 예정" value={fmtNum(data.open)} />
            <Stat label="취소" value={fmtNum(data.canceled)} />
            <Stat label="삭제됨" value={fmtNum(data.purged)} />
          </div>
          {data.overdue > 0 ? (
            <Notice tone="warn">
              삭제 예정 시각이 지났는데 아직 지우지 않은 탈퇴 예정 회원이 {fmtNum(data.overdue)}명 있습니다. 예약
              삭제(매일 04:30)가 실패했을 수 있으니 서버 로그를 확인하거나 아래 "삭제 밀림"에서 바로 삭제하세요.
            </Notice>
          ) : null}
          {data.reasons.length ? (
            <table className="table" style={{ marginTop: 8 }}>
              <thead>
                <tr>
                  <th>사유</th>
                  <th className="num">신청</th>
                  <th className="num">비율</th>
                </tr>
              </thead>
              <tbody>
                {data.reasons.map((r) => (
                  <tr key={r.reason}>
                    <td>{REASON_LABEL[r.reason] ?? r.reason}</td>
                    <td className="num">{fmtNum(r.count)}</td>
                    <td className="num">{data.total ? `${Math.round((r.count / data.total) * 100)}%` : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <Empty>이 기간에 탈퇴 신청이 없습니다.</Empty>
          )}
        </>
      ) : null}
    </Card>
  );
};

export const WithdrawalsPage = () => {
  const [days, setDays] = useState(30);
  // 순서 예외: 검색어 입력값(draft)의 초깃값이 URL 쿼리(params)를 읽는다
  const [params, setParams] = useSearchParams();

  const [draft, setDraft] = useState(() => params.get('q') ?? '');

  const queryClient = useQueryClient();
  const confirm = useConfirmStore((s) => s.ask);
  const toast = useToastStore((s) => s.show);
  const toastError = useToastStore((s) => s.showError);

  // 순서 예외: 바로 아래 useQuery의 인자가 query를 읽는다
  const query: IWithdrawalQuery = {
    q: params.get('q') || undefined,
    state: params.get('state') || undefined,
    reason: params.get('reason') || undefined,
    page: Number(params.get('page') ?? 0) || 0,
    size: PAGE_SIZE,
  };

  const { data, error, isLoading, isPlaceholderData, refetch } = useQuery({
    queryKey: qk.withdrawals(query),
    queryFn: () => api.withdrawals.list(query),
    placeholderData: keepPreviousData,
    // 회원이 앱에서 탈퇴·취소하고 예약 삭제도 돌므로 캐시를 바로 낡은 것으로 본다
    staleTime: 0,
  });

  // 순서 예외: useQuery가 읽는 query 값(파생 값)이 앞에 있어야 한다
  const cancel = useMutation({
    mutationFn: (row: IWithdrawalRow) => api.withdrawals.cancel(row.userId),
    onSuccess: async (_r, row) => {
      await queryClient.invalidateQueries({ queryKey: ['withdrawals'] });
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
      toast(`${row.email ?? `#${row.userId}`}: 탈퇴를 취소했습니다. 회원은 다시 로그인해 그대로 쓸 수 있습니다.`);
    },
    onError: (e) => {
      toastError(e);
      void queryClient.invalidateQueries({ queryKey: ['withdrawals'] });
    },
  });
  const purge = useMutation({
    mutationFn: (row: IWithdrawalRow) => api.withdrawals.purge(row.userId),
    onSuccess: async (_r, row) => {
      await queryClient.invalidateQueries({ queryKey: ['withdrawals'] });
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
      toast(`${row.email ?? `#${row.userId}`}: 회원과 데이터를 삭제했습니다.`);
    },
    onError: (e) => {
      toastError(e);
      void queryClient.invalidateQueries({ queryKey: ['withdrawals'] });
    },
  });

  const unsupported = isUnsupportedApi(error);
  const state = params.get('state') ?? '';
  const reason = params.get('reason') ?? '';
  const q = params.get('q') ?? '';

  const apply = (next: { q: string; state: string; reason: string }) => {
    const p = new URLSearchParams();
    if (next.q.trim()) p.set('q', next.q.trim());
    if (next.state) p.set('state', next.state);
    if (next.reason) p.set('reason', next.reason);
    if (p.toString() === params.toString()) void queryClient.invalidateQueries({ queryKey: ['withdrawals'] });
    else setParams(p);
  };
  const submit = (e: FormEvent) => {
    e.preventDefault();
    apply({ q: draft, state, reason });
  };
  const reset = () => {
    setDraft('');
    apply({ q: '', state: '', reason: '' });
  };
  const setPage = (p: number) => {
    const next = new URLSearchParams(params);
    next.set('page', String(p));
    setParams(next);
  };
  const who = (r: IWithdrawalRow) => (r.email ? `${r.name ?? ''}(${r.email})` : `회원 #${r.userId}`);
  const confirmCancel = (r: IWithdrawalRow) => {
    void confirm({
      title: '탈퇴 취소',
      message:
        `${who(r)}의 탈퇴를 취소할까요?\n` +
        '회원은 다시 로그인해 옷장과 설정을 그대로 쓸 수 있습니다. 끊긴 옷장 도우미 연결은 되살아나지 않습니다. 변경 이력에 남습니다.',
      confirmLabel: '탈퇴 취소',
    }).then((ok) => {
      if (ok) cancel.mutate(r);
    });
  };
  const confirmPurge = (r: IWithdrawalRow) => {
    void confirm({
      title: '바로 삭제',
      message:
        `${who(r)}을(를) 지금 삭제할까요? 삭제 예정일(${fmtDate(r.purgeAt)})을 기다리지 않습니다.\n` +
        '계정과 옷장·사진·코디·설정이 모두 지워지고 되돌릴 수 없습니다. 탈퇴 기록과 변경 이력은 남습니다.',
      confirmLabel: '삭제',
      tone: 'danger',
    }).then((ok) => {
      if (ok) purge.mutate(r);
    });
  };

  return (
    <>
      <PageHeader
        title="탈퇴 기록"
        desc="탈퇴를 신청하면 30일 동안 탈퇴 예정으로 두고, 그 전에 회원이 다시 로그인하면 취소됩니다. 지나면 매일 04:30 예약 작업이 지웁니다. 기록에는 회원 ID·사유·시각만 남고, 이메일·이름은 회원이 남아 있을 때만 보입니다."
      />

      {unsupported ? null : <Summary days={days} onDays={setDays} />}

      <Card>
        <form className="filters" onSubmit={submit}>
          <label>
            <span className="field-label">검색</span>
            <input
              type="search"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="이메일·이름·회원 ID"
            />
          </label>
          <label>
            <span className="field-label">상태</span>
            <select value={state} onChange={(e) => apply({ q: draft, state: e.target.value, reason })}>
              <option value="">전체</option>
              {STATE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="field-label">사유</span>
            <select value={reason} onChange={(e) => apply({ q: draft, state, reason: e.target.value })}>
              <option value="">전체</option>
              {Object.entries(REASON_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <div className="filters-actions">
            <Button type="submit" variant="primary">
              검색
            </Button>
            <Button onClick={reset}>초기화</Button>
          </div>
        </form>
      </Card>

      {unsupported ? (
        <Notice tone="warn">
          서버가 아직 이 기능을 모릅니다. 백엔드에 탈퇴 기록 API(<span className="mono">GET /admin/withdrawals</span>,
          doc/27)가 들어가면 이 화면이 채워집니다.
        </Notice>
      ) : (
        <ErrorBox error={error} onRetry={() => void refetch()} />
      )}
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isPlaceholderData ? 'is-fetching' : ''}>
          {data.items.length === 0 ? (
            <Empty>{q || state || reason ? '조건에 맞는 탈퇴 기록이 없습니다.' : '탈퇴 기록이 없습니다.'}</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>신청 시각</th>
                    <th>회원</th>
                    <th>사유</th>
                    <th>상태</th>
                    <th>삭제 예정</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((r) => {
                    const open = r.state === 'OPEN';
                    const busy =
                      (cancel.isPending && cancel.variables?.id === r.id) ||
                      (purge.isPending && purge.variables?.id === r.id);
                    return (
                      <tr key={r.id} className={open ? '' : 'row-inactive'}>
                        <td className="nowrap small">{fmtDateTime(r.requestedAt)}</td>
                        <td>
                          <div>
                            <strong>{r.name ?? '—'}</strong> <span className="small mono muted">#{r.userId}</span>
                          </div>
                          <div className="small muted">{r.email ?? (r.state === 'PURGED' ? '삭제됨' : '—')}</div>
                        </td>
                        <td>
                          <div>{REASON_LABEL[r.reason] ?? r.reason}</div>
                          {r.reasonText ? (
                            <div className="small muted" title={r.reasonText} style={{ maxWidth: 320, whiteSpace: 'pre-wrap' }}>
                              {r.reasonText}
                            </div>
                          ) : null}
                        </td>
                        <td>
                          {stateBadge(r)}
                          {r.canceledAt ? (
                            <div className="small muted">
                              {BY_LABEL[r.canceledBy ?? ''] ?? r.canceledBy} · {fmtDateTime(r.canceledAt)}
                            </div>
                          ) : null}
                          {r.purgedAt ? (
                            <div className="small muted">
                              {BY_LABEL[r.purgedBy ?? ''] ?? r.purgedBy} · {fmtDateTime(r.purgedAt)}
                            </div>
                          ) : null}
                        </td>
                        <td className="nowrap small">{open ? fmtDateTime(r.purgeAt) : <span className="muted">—</span>}</td>
                        <td className="nowrap">
                          {open ? (
                            <>
                              <Button size="sm" busy={busy && cancel.isPending} disabled={busy} onClick={() => confirmCancel(r)}>
                                탈퇴 취소
                              </Button>{' '}
                              <Button
                                size="sm"
                                variant="danger"
                                busy={busy && purge.isPending}
                                disabled={busy}
                                onClick={() => confirmPurge(r)}
                              >
                                바로 삭제
                              </Button>
                            </>
                          ) : null}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
          <Pagination page={data.page} size={data.size || PAGE_SIZE} total={data.total} onPage={setPage} />
        </Card>
      ) : null}
    </>
  );
};
