// 로그인 사용자: 지금 살아 있는 로그인 세션, 30초마다 새로 고침, 세션·회원 단위 강제 로그아웃 (doc/16 5장)
import { useState } from 'react';
import type { FormEvent } from 'react';

import { api } from '@/api';
import type { IAdminSession, ISessionQuery } from '@/api';

import { ClientBadge, RevokeSessionModal, UaSummary } from '@/components/Sessions';
import { Badge, Button, Card, Empty, ErrorBox, Loading, Notice, PageHeader, Pagination } from '@/components/ui';

import { useAuthStore } from '@/stores/useAuthStore';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { fmtDateTime, fmtNum, fmtRelative } from '@/lib/format';
import { isUnsupportedApi, qk, sessionRefetchInterval } from '@/lib/queries';
import { SESSION_CLIENT_OPTIONS, sessionDeviceText } from '@/lib/sessions';
import { summarizeUserAgent } from '@/lib/userAgent';
import type { TRevokeTarget } from '@/lib/queries';

const PAGE_SIZE = 50;

const whoOf = (s: Pick<IAdminSession, 'name' | 'email' | 'userId'>): string => {
  return s.name || s.email || `#${s.userId}`;
};

export const SessionsPage = () => {
  // 순서 예외: 검색어(q) 초깃값이 읽는 query가 URL 쿼리(params)로 만들어진다
  const [params, setParams] = useSearchParams();
  // 순서 예외: 검색어(q) 초깃값과 아래 useQuery의 인자가 query를 읽는다
  const query: ISessionQuery = {
    q: params.get('q') ?? undefined,
    client: params.get('client') ?? undefined,
    page: Number(params.get('page') ?? 0) || 0,
    size: PAGE_SIZE,
  };

  const [q, setQ] = useState(query.q ?? '');
  const [target, setTarget] = useState<TRevokeTarget | null>(null);

  const me = useAuthStore((s) => s.user);
  const { data, error, isLoading, isFetching, isPlaceholderData, refetch, dataUpdatedAt } = useQuery({
    queryKey: qk.sessions(query),
    queryFn: () => api.sessions.list(query),
    placeholderData: keepPreviousData,
    refetchInterval: sessionRefetchInterval,
  });

  const unsupported = isUnsupportedApi(error);
  /** 한 페이지 안에서 회원별 세션 수 ("모든 기기에서 로그아웃" 확인 문구용) */
  const countByUser = new Map<string, number>();
  for (const s of data?.items ?? []) countByUser.set(s.userId, (countByUser.get(s.userId) ?? 0) + 1);

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    next.delete('page');
    setParams(next);
  };
  const search = (e: FormEvent) => {
    e.preventDefault();
    setFilter('q', q.trim());
  };
  const setPage = (p: number) => {
    const next = new URLSearchParams(params);
    next.set('page', String(p));
    setParams(next);
  };
  const revokeSession = (s: IAdminSession) => {
    setTarget({
      kind: 'session',
      sessionId: s.sessionId,
      who: whoOf(s),
      device: sessionDeviceText(s.client, summarizeUserAgent(s.userAgent).label),
    });
  };
  const revokeUser = (s: IAdminSession) => {
    // 이 페이지에 보이는 수일 뿐이라 다른 페이지에 더 있을 수 있다 → 페이지가 하나일 때만 개수를 적는다
    const single = data ? data.total <= data.items.length : false;
    setTarget({
      kind: 'user',
      userId: s.userId,
      who: whoOf(s),
      count: single ? (countByUser.get(s.userId) ?? null) : null,
    });
  };

  return (
    <>
      <PageHeader
        title="로그인 사용자"
        desc="지금 로그인돼 있는 세션(로그인 한 번 = 세션 하나). 30초마다 새로 고칩니다."
        actions={
          <Button onClick={() => void refetch()} disabled={isFetching}>
            새로 고침
          </Button>
        }
      />
      <Card>
        <div className="toolbar">
          <form onSubmit={search} className="row gap-sm">
            <input
              type="search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="이메일·이름 검색"
              className="w-search"
            />
            <Button type="submit" variant="primary">
              검색
            </Button>
          </form>
          <div className="row gap-sm">
            {data ? <span className="muted small">세션 {fmtNum(data.total)}개</span> : null}
            <select value={query.client ?? ''} onChange={(e) => setFilter('client', e.target.value)} aria-label="기기">
              <option value="">모든 기기</option>
              {SESSION_CLIENT_OPTIONS.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </Card>

      {unsupported ? (
        <Notice tone="warn">
          서버가 아직 이 기능을 모릅니다. 백엔드에 로그인 세션 API(<span className="mono">GET /admin/sessions</span>,
          doc/16)가 들어가면 이 화면이 채워집니다.
        </Notice>
      ) : (
        <ErrorBox error={error} onRetry={() => void refetch()} />
      )}
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isPlaceholderData ? 'is-fetching' : ''}>
          {data.items.length === 0 ? (
            <Empty>
              {query.q || query.client ? '조건에 맞는 로그인 세션이 없습니다.' : '로그인한 사용자가 없습니다.'}
            </Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>회원</th>
                    <th>역할</th>
                    <th>기기</th>
                    <th>기기 요약</th>
                    <th>IP</th>
                    <th>로그인</th>
                    <th>마지막 활동</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((s) => {
                    const isMe = s.userId === me?.id;
                    return (
                      <tr key={s.sessionId}>
                        <td>
                          <div className="row gap-xs wrap">
                            <strong>{s.name || '—'}</strong>
                            {isMe ? <Badge tone="beige">나</Badge> : null}
                            <button
                              type="button"
                              className="link-btn small"
                              disabled={isMe}
                              title={
                                isMe
                                  ? '자기 자신은 모든 기기에서 로그아웃할 수 없습니다'
                                  : '이 회원의 모든 세션을 끊습니다'
                              }
                              onClick={() => revokeUser(s)}
                            >
                              모든 기기에서 로그아웃
                            </button>
                          </div>
                          <div className="small muted">{s.email ?? `#${s.userId}`}</div>
                        </td>
                        <td>
                          {s.role === 'ADMIN' ? (
                            <Badge tone="ink">관리자</Badge>
                          ) : (
                            <span className="muted small">{s.role ? '일반' : '—'}</span>
                          )}
                        </td>
                        <td>
                          <ClientBadge client={s.client} />
                        </td>
                        <td className="small">
                          <UaSummary userAgent={s.userAgent} />
                        </td>
                        <td className="mono small">{s.ip ?? '—'}</td>
                        <td className="nowrap small">{fmtDateTime(s.createdAt)}</td>
                        <td
                          className="nowrap small"
                          title={fmtDateTime(s.lastSeenAt ?? s.lastRefreshAt ?? s.createdAt)}
                        >
                          {fmtRelative(s.lastSeenAt ?? s.lastRefreshAt ?? s.createdAt, dataUpdatedAt)}
                        </td>
                        <td className="actions">
                          {s.current ? <Badge tone="green">현재 세션</Badge> : null}{' '}
                          <Button
                            size="sm"
                            variant="ghost"
                            className="text-danger"
                            disabled={s.current}
                            title={s.current ? '지금 쓰고 있는 내 세션은 끊을 수 없습니다' : undefined}
                            onClick={() => revokeSession(s)}
                          >
                            로그아웃 시키기
                          </Button>
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

      {target ? <RevokeSessionModal target={target} onClose={() => setTarget(null)} /> : null}
    </>
  );
};
