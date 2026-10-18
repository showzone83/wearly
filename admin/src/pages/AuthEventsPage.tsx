// 관리자 로그인 기록: 지금 관리자인 회원의 로그인·2단계 인증 이벤트 + 관리자 사이트에서 일어난 모든 이벤트 (doc/22)
// 필터(회원 ID·종류·기간)는 URL 쿼리에 둔다 — 회원 화면의 "로그인 기록"이 ?userId=로 넘어온다.
import { useState } from 'react';
import type { FormEvent } from 'react';

import { api } from '@/api';
import type { IAuthEventQuery } from '@/api';

import { AuthEventBadge, ClientBadge, UaSummary } from '@/components/Sessions';
import { Badge, Button, Card, Empty, ErrorBox, Loading, Notice, PageHeader, Pagination } from '@/components/ui';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { fmtDateTime, todaySeoul } from '@/lib/format';
import { isUnsupportedApi, qk } from '@/lib/queries';
import { AUTH_EVENT_OPTIONS } from '@/lib/sessions';

const PAGE_SIZE = 30;
const FILTER_KEYS = ['userId', 'type', 'from', 'to'] as const;

type TFilterKey = (typeof FILTER_KEYS)[number];

export const AuthEventsPage = () => {
  // 순서 예외: 필터 입력값(draft)의 초깃값이 URL 쿼리(params)를 읽는다
  const [params, setParams] = useSearchParams();

  // 필터 입력값 (검색 버튼을 눌러야 적용. 종류는 고르면 바로 적용)
  const [draft, setDraft] = useState(
    () => Object.fromEntries(FILTER_KEYS.map((k) => [k, params.get(k) ?? ''])) as Record<TFilterKey, string>,
  );
  const [draftError, setDraftError] = useState<string | null>(null);

  // 순서 예외: 바로 아래 useQuery의 인자가 query를 읽는다
  const query: IAuthEventQuery = {
    userId: params.get('userId') ?? undefined,
    type: params.get('type') ?? undefined,
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
    page: Number(params.get('page') ?? 0) || 0,
    size: PAGE_SIZE,
  };
  const { data, error, isLoading, isPlaceholderData, isFetching, refetch } = useQuery({
    queryKey: qk.authEvents(query),
    queryFn: () => api.authEvents.list(query),
    placeholderData: keepPreviousData,
  });

  const unsupported = isUnsupportedApi(error);
  const filtered = FILTER_KEYS.some((k) => !!query[k]);

  const applyDraft = (d: Record<TFilterKey, string>) => {
    const userId = d.userId.trim();
    if (userId && !/^\d+$/.test(userId)) {
      setDraftError('회원 ID는 숫자로 입력해 주세요.');
      return;
    }
    if (d.from && d.to && d.from > d.to) {
      setDraftError('시작일이 종료일보다 늦어요.');
      return;
    }
    setDraftError(null);
    const next = new URLSearchParams();
    for (const k of FILTER_KEYS) {
      const v = k === 'userId' ? userId : d[k];
      if (v) next.set(k, v);
    }
    setParams(next);
  };
  const apply = (e?: FormEvent) => {
    e?.preventDefault();
    applyDraft(draft);
  };
  const setType = (type: string) => {
    const d = { ...draft, type };
    setDraft(d);
    applyDraft(d);
  };
  const reset = () => {
    setDraft({ userId: '', type: '', from: '', to: '' });
    setDraftError(null);
    setParams(new URLSearchParams());
  };
  const setPage = (p: number) => {
    const next = new URLSearchParams(params);
    next.set('page', String(p));
    setParams(next);
  };
  const quickRange = (days: number) => setDraft((d) => ({ ...d, from: todaySeoul(-(days - 1)), to: todaySeoul() }));

  return (
    <>
      <PageHeader
        title="관리자 로그인 기록"
        desc="관리자 계정의 로그인·로그아웃·2단계 인증 기록과, 관리자 사이트에서 일어난 모든 로그인 시도(최신순)."
        actions={
          <Button onClick={() => void refetch()} disabled={isFetching}>
            새로 고침
          </Button>
        }
      />
      <Card>
        <form className="filters" onSubmit={apply}>
          <label>
            <span className="field-label">회원 ID</span>
            <input
              type="search"
              inputMode="numeric"
              value={draft.userId}
              onChange={(e) => setDraft({ ...draft, userId: e.target.value })}
              placeholder="예: 12"
            />
          </label>
          <label>
            <span className="field-label">종류</span>
            <select value={draft.type} onChange={(e) => setType(e.target.value)}>
              <option value="">전체</option>
              {AUTH_EVENT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="field-label">시작일</span>
            <input type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
          </label>
          <label>
            <span className="field-label">종료일</span>
            <input type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} />
          </label>
          <div className="filters-actions">
            <button type="button" className="link-btn" onClick={() => quickRange(1)}>
              오늘
            </button>
            <button type="button" className="link-btn" onClick={() => quickRange(7)}>
              7일
            </button>
            <Button type="submit" variant="primary">
              검색
            </Button>
            <Button onClick={reset}>초기화</Button>
          </div>
        </form>
        {draftError ? (
          <div className="mt-sm">
            <Notice tone="error">{draftError}</Notice>
          </div>
        ) : null}
      </Card>

      {unsupported ? (
        <Notice tone="warn">
          서버가 아직 이 기능을 모릅니다. 백엔드에 관리자 로그인 기록 API(
          <span className="mono">GET /admin/auth-events</span>, doc/22)가 들어가면 이 화면이 채워집니다.
        </Notice>
      ) : (
        <ErrorBox error={error} onRetry={() => void refetch()} />
      )}
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isPlaceholderData ? 'is-fetching' : ''}>
          {data.items.length === 0 ? (
            <Empty>{filtered ? '조건에 맞는 기록이 없습니다.' : '기록이 없습니다.'}</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>시각</th>
                    <th>회원</th>
                    <th>역할</th>
                    <th>종류</th>
                    <th>기기</th>
                    <th>기기 요약</th>
                    <th>IP</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((ev) => (
                    <tr key={ev.id || `${ev.createdAt}-${ev.type}-${ev.userId}`}>
                      <td className="nowrap small">{fmtDateTime(ev.createdAt)}</td>
                      <td>
                        <div>
                          <strong>{ev.name || '—'}</strong>{' '}
                          {ev.userId ? (
                            <button
                              type="button"
                              className="link-btn small mono"
                              title="이 회원의 기록만 보기"
                              onClick={() => {
                                setDraft({ ...draft, userId: ev.userId });
                                applyDraft({ ...draft, userId: ev.userId });
                              }}
                            >
                              #{ev.userId}
                            </button>
                          ) : null}
                        </div>
                        <div className="small muted">{ev.email ?? '—'}</div>
                      </td>
                      <td>
                        {ev.role === 'ADMIN' ? (
                          <Badge tone="ink">관리자</Badge>
                        ) : (
                          <span className="muted small">{ev.role ? '일반' : '—'}</span>
                        )}
                      </td>
                      <td>
                        <AuthEventBadge type={ev.type} />
                        {ev.ssoProvider ? (
                          <div
                            className="small muted"
                            title={ev.ssoAmr ? `IdP 인증 방법(amr): ${ev.ssoAmr}` : undefined}
                          >
                            {ev.ssoProvider}
                            {ev.ssoAmr ? ` · ${ev.ssoAmr}` : ''}
                          </div>
                        ) : null}
                      </td>
                      <td>
                        <ClientBadge client={ev.client} />
                      </td>
                      <td className="small">
                        <UaSummary userAgent={ev.userAgent} />
                      </td>
                      <td className="mono small">{ev.ip ?? '—'}</td>
                    </tr>
                  ))}
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
