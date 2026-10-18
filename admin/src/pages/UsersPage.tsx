// 회원: 검색, 정지·해제, 관리자 지정·해제, 로그인 기기(세션·이력·강제 로그아웃)
import { useState } from 'react';
import type { FormEvent } from 'react';

import { api } from '@/api';
import type { IAdminUser, IUserPatch, IUserQuery } from '@/api';

import { Badge, Button, Card, Empty, ErrorBox, Loading, PageHeader, Pagination } from '@/components/ui';
import { UserSessionsDrawer } from '@/pages/UserSessionsDrawer';

import { useAuthStore } from '@/stores/useAuthStore';
import { useToastStore } from '@/stores/useToastStore';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { fmtDate, fmtNum } from '@/lib/format';
import { qk } from '@/lib/queries';

const PAGE_SIZE = 20;

export const UsersPage = () => {
  // 순서 예외: 검색어(q) 초깃값이 읽는 query가 URL 쿼리(params)로 만들어진다
  const [params, setParams] = useSearchParams();
  // 순서 예외: 검색어(q) 초깃값과 아래 useQuery의 인자가 query를 읽는다
  const query: IUserQuery = {
    q: params.get('q') ?? undefined,
    status: params.get('status') ?? undefined,
    role: params.get('role') ?? undefined,
    page: Number(params.get('page') ?? 0) || 0,
    size: PAGE_SIZE,
  };

  const [q, setQ] = useState(query.q ?? '');
  /** 로그인 기기 패널을 연 회원 */
  const [sessionsOf, setSessionsOf] = useState<IAdminUser | null>(null);

  const me = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();
  const toast = useToastStore((s) => s.show);
  const toastError = useToastStore((s) => s.showError);
  const { data, error, isLoading, isFetching, refetch } = useQuery({
    queryKey: qk.users(query),
    queryFn: () => api.users.list(query),
    placeholderData: keepPreviousData,
  });

  const update = useMutation({
    mutationFn: ({ user, patch }: { user: IAdminUser; patch: IUserPatch }) => api.users.update(user.id, patch),
    onSuccess: async (_r, { user, patch }) => {
      await queryClient.invalidateQueries({ queryKey: ['users'] });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      const what = patch.status
        ? patch.status === 'SUSPENDED'
          ? '정지했습니다'
          : '정지를 해제했습니다'
        : patch.role === 'ADMIN'
          ? '관리자로 지정했습니다'
          : '관리자에서 해제했습니다';
      toast(`${user.email ?? user.name}: ${what}.`);
    },
    onError: (e) => toastError(e),
  });

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

  const act = (u: IAdminUser, patch: IUserPatch, message: string) => {
    if (window.confirm(message)) update.mutate({ user: u, patch });
  };

  return (
    <>
      <PageHeader title="회원" desc="정지하면 로그인이 막히고, 이미 받은 토큰도 바로 무효가 됩니다." />
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
            <select value={query.status ?? ''} onChange={(e) => setFilter('status', e.target.value)} aria-label="상태">
              <option value="">모든 상태</option>
              <option value="ACTIVE">정상</option>
              <option value="SUSPENDED">정지</option>
            </select>
            <select value={query.role ?? ''} onChange={(e) => setFilter('role', e.target.value)} aria-label="역할">
              <option value="">모든 역할</option>
              <option value="USER">일반</option>
              <option value="ADMIN">관리자</option>
            </select>
          </div>
        </div>
      </Card>

      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isFetching ? 'is-fetching' : ''}>
          {data.items.length === 0 ? (
            <Empty>회원이 없습니다.</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>이메일</th>
                    <th>이름</th>
                    <th>역할</th>
                    <th>상태</th>
                    <th>가입일</th>
                    <th className="num">아이템</th>
                    <th className="num">오늘 AI</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((u) => {
                    const isMe = u.id === me?.id;
                    const busy = update.isPending && update.variables?.user.id === u.id;
                    return (
                      <tr key={u.id} className={u.status === 'SUSPENDED' ? 'row-inactive' : ''}>
                        <td className="mono small">{u.id}</td>
                        <td>
                          {u.email ?? <span className="muted">—</span>} {isMe ? <Badge tone="beige">나</Badge> : null}
                        </td>
                        <td>{u.name}</td>
                        <td>
                          {u.role === 'ADMIN' ? (
                            <Badge tone="ink">관리자</Badge>
                          ) : (
                            <span className="muted small">일반</span>
                          )}
                        </td>
                        <td>
                          {u.status === 'SUSPENDED' ? <Badge tone="red">정지</Badge> : <Badge tone="green">정상</Badge>}
                        </td>
                        <td className="small">{fmtDate(u.createdAt)}</td>
                        <td className="num">{fmtNum(u.itemCount)}</td>
                        <td className="num">{fmtNum(u.aiToday)}</td>
                        <td className="actions">
                          <Button size="sm" variant="ghost" onClick={() => setSessionsOf(u)}>
                            로그인 기기
                          </Button>
                          {u.status === 'SUSPENDED' ? (
                            <Button
                              size="sm"
                              busy={busy}
                              onClick={() =>
                                act(u, { status: 'ACTIVE' }, `${u.email ?? u.name} 회원의 정지를 해제할까요?`)
                              }
                            >
                              정지 해제
                            </Button>
                          ) : (
                            <Button
                              size="sm"
                              variant="ghost"
                              className="text-danger"
                              busy={busy}
                              disabled={isMe}
                              title={isMe ? '자기 자신은 정지할 수 없습니다' : undefined}
                              onClick={() =>
                                act(
                                  u,
                                  { status: 'SUSPENDED' },
                                  `${u.email ?? u.name} 회원을 정지할까요? 로그인이 막히고 지금 쓰는 앱에서도 로그아웃됩니다.`,
                                )
                              }
                            >
                              정지
                            </Button>
                          )}
                          {u.role === 'ADMIN' ? (
                            <Button
                              size="sm"
                              variant="ghost"
                              disabled={isMe || busy}
                              title={isMe ? '자기 자신의 역할은 내릴 수 없습니다' : undefined}
                              onClick={() =>
                                act(u, { role: 'USER' }, `${u.email ?? u.name} 회원을 관리자에서 해제할까요?`)
                              }
                            >
                              관리자 해제
                            </Button>
                          ) : (
                            <Button
                              size="sm"
                              variant="ghost"
                              disabled={busy}
                              onClick={() =>
                                act(
                                  u,
                                  { role: 'ADMIN' },
                                  `${u.email ?? u.name} 회원을 관리자로 지정할까요? 이 사이트의 모든 기능을 쓸 수 있게 됩니다.`,
                                )
                              }
                            >
                              관리자 지정
                            </Button>
                          )}
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

      {sessionsOf ? (
        <UserSessionsDrawer user={sessionsOf} isMe={sessionsOf.id === me?.id} onClose={() => setSessionsOf(null)} />
      ) : null}
    </>
  );
};
