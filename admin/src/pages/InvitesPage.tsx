// 친구 초대 이력 (doc/25): 초대 코드로 가입한 이력과 앱에서 초대를 보낸 이력. 탭·검색·필터·페이지는 URL 쿼리에 둔다.
import { useState } from 'react';
import type { FormEvent } from 'react';

import { api } from '@/api';
import type { IInviteShareQuery, IInviteSignupQuery, TInviteChannel, TInviteSignupResult } from '@/api';

import { Badge, Button, Card, Empty, ErrorBox, Loading, Notice, PageHeader, Pagination, Segmented } from '@/components/ui';

import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { fmtDateTime } from '@/lib/format';
import { isUnsupportedApi, qk } from '@/lib/queries';

const PAGE_SIZE = 30;

type TTab = 'signups' | 'shares';

const RESULT_OPTIONS: { value: TInviteSignupResult; label: string }[] = [
  { value: 'LINKED', label: '연결됨' },
  { value: 'UNKNOWN_CODE', label: '코드 없음' },
];

const CHANNEL_OPTIONS: { value: TInviteChannel; label: string }[] = [
  { value: 'SHARE', label: '공유 시트' },
  { value: 'SMS', label: '문자' },
  { value: 'COPY', label: '코드 복사' },
];

const labelOf = (options: { value: string; label: string }[], value: string) => {
  return options.find((o) => o.value === value)?.label ?? value;
};

/** 8자 코드를 화면처럼 XXXX-XXXX로 (그 밖의 입력은 그대로) */
const fmtCode = (code: string) => {
  return /^[A-Z0-9]{8}$/.test(code) ? `${code.slice(0, 4)}-${code.slice(4)}` : code;
};

const Person = ({ id, name, email }: { id: string | null; name: string | null; email: string | null }) => {
  if (!id) return <span className="muted">—</span>;
  return (
    <>
      <div>
        <strong>{name || '—'}</strong> <span className="small mono muted">#{id}</span>
      </div>
      <div className="small muted">{email ?? '—'}</div>
    </>
  );
};

/**
 * 탭 안의 검색어·필터: 입력값은 검색 버튼을 눌러야, 필터는 고르면 바로 적용. 조건이 그대로면 URL·쿼리 키가 같아 다시 받지 않으므로
 * 그 탭의 목록 쿼리(queryKeyHead)를 무효화해 다시 받는다 — 앱에서 방금 보낸 초대를 같은 조건으로 다시 검색해도 바로 보이게
 */
const useHistoryFilter = (filterKey: 'result' | 'channel', queryKeyHead: string) => {
  // 순서 예외: 검색어 입력값(draft)의 초깃값이 URL 쿼리(params)를 읽는다
  const [params, setParams] = useSearchParams();

  const [draft, setDraft] = useState(() => params.get('q') ?? '');

  const queryClient = useQueryClient();

  const filter = params.get(filterKey) ?? '';
  const page = Number(params.get('page') ?? 0) || 0;
  const q = params.get('q') ?? '';

  const onSame = () => void queryClient.invalidateQueries({ queryKey: [queryKeyHead] });

  const apply = (next: { q: string; filter: string }) => {
    const p = new URLSearchParams({ tab: params.get('tab') ?? 'signups' });
    if (next.q.trim()) p.set('q', next.q.trim());
    if (next.filter) p.set(filterKey, next.filter);
    if (p.toString() === params.toString()) onSame();
    else setParams(p);
  };
  const submit = (e: FormEvent) => {
    e.preventDefault();
    apply({ q: draft, filter });
  };
  const setFilter = (v: string) => apply({ q: draft, filter: v });
  const reset = () => {
    setDraft('');
    apply({ q: '', filter: '' });
  };
  const setPage = (p: number) => {
    const next = new URLSearchParams(params);
    next.set('page', String(p));
    setParams(next);
  };

  return { draft, setDraft, filter, page, q, submit, setFilter, reset, setPage };
};

const SignupHistory = () => {
  const f = useHistoryFilter('result', qk.inviteSignups({})[0]);
  // 순서 예외: 바로 아래 useQuery의 인자가 query를 읽는다
  const query: IInviteSignupQuery = { q: f.q || undefined, result: f.filter || undefined, page: f.page, size: PAGE_SIZE };
  // 이력은 앱에서 계속 쌓이므로 캐시를 바로 낡은 것으로 본다 (탭을 다시 열면 다시 받는다)
  const { data, error, isLoading, isPlaceholderData, refetch } = useQuery({
    queryKey: qk.inviteSignups(query),
    queryFn: () => api.invites.signups(query),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });

  const unsupported = isUnsupportedApi(error);

  return (
    <>
      <Card>
        <form className="filters" onSubmit={f.submit}>
          <label>
            <span className="field-label">검색</span>
            <input
              type="search"
              value={f.draft}
              onChange={(e) => f.setDraft(e.target.value)}
              placeholder="가입자·추천인 이메일·이름, 입력 코드"
            />
          </label>
          <label>
            <span className="field-label">결과</span>
            <select value={f.filter} onChange={(e) => f.setFilter(e.target.value)}>
              <option value="">전체</option>
              {RESULT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <div className="filters-actions">
            <Button type="submit" variant="primary">
              검색
            </Button>
            <Button onClick={f.reset}>초기화</Button>
          </div>
        </form>
      </Card>

      {unsupported ? (
        <Notice tone="warn">
          서버가 아직 이 기능을 모릅니다. 백엔드에 친구 초대 API(
          <span className="mono">GET /admin/invites/signups</span>, doc/25)가 들어가면 이 화면이 채워집니다.
        </Notice>
      ) : (
        <ErrorBox error={error} onRetry={() => void refetch()} />
      )}
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isPlaceholderData ? 'is-fetching' : ''}>
          {data.items.length === 0 ? (
            <Empty>{f.q || f.filter ? '조건에 맞는 기록이 없습니다.' : '초대 코드로 가입한 기록이 없습니다.'}</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>가입 시각</th>
                    <th>가입자</th>
                    <th>입력 코드</th>
                    <th>결과</th>
                    <th>추천인</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((r) => (
                    <tr key={r.id}>
                      <td className="nowrap small">{fmtDateTime(r.createdAt)}</td>
                      <td>
                        <Person id={r.userId} name={r.userName} email={r.userEmail} />
                      </td>
                      <td className="mono">{fmtCode(r.enteredCode)}</td>
                      <td>
                        <Badge tone={r.result === 'LINKED' ? 'green' : 'gray'}>{labelOf(RESULT_OPTIONS, r.result)}</Badge>
                      </td>
                      <td>
                        <Person id={r.referrerId} name={r.referrerName} email={r.referrerEmail} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination page={data.page} size={data.size || PAGE_SIZE} total={data.total} onPage={f.setPage} />
        </Card>
      ) : null}
    </>
  );
};

const ShareHistory = () => {
  const f = useHistoryFilter('channel', qk.inviteShares({})[0]);
  // 순서 예외: 바로 아래 useQuery의 인자가 query를 읽는다
  const query: IInviteShareQuery = { q: f.q || undefined, channel: f.filter || undefined, page: f.page, size: PAGE_SIZE };
  // 이력은 앱에서 계속 쌓이므로 캐시를 바로 낡은 것으로 본다 (탭을 다시 열면 다시 받는다)
  const { data, error, isLoading, isPlaceholderData, refetch } = useQuery({
    queryKey: qk.inviteShares(query),
    queryFn: () => api.invites.shares(query),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });

  const unsupported = isUnsupportedApi(error);

  return (
    <>
      <Card>
        <form className="filters" onSubmit={f.submit}>
          <label>
            <span className="field-label">검색</span>
            <input
              type="search"
              value={f.draft}
              onChange={(e) => f.setDraft(e.target.value)}
              placeholder="회원 이메일·이름"
            />
          </label>
          <label>
            <span className="field-label">방법</span>
            <select value={f.filter} onChange={(e) => f.setFilter(e.target.value)}>
              <option value="">전체</option>
              {CHANNEL_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <div className="filters-actions">
            <Button type="submit" variant="primary">
              검색
            </Button>
            <Button onClick={f.reset}>초기화</Button>
          </div>
        </form>
      </Card>

      {unsupported ? (
        <Notice tone="warn">
          서버가 아직 이 기능을 모릅니다. 백엔드에 친구 초대 API(
          <span className="mono">GET /admin/invites/shares</span>, doc/25)가 들어가면 이 화면이 채워집니다.
        </Notice>
      ) : (
        <ErrorBox error={error} onRetry={() => void refetch()} />
      )}
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isPlaceholderData ? 'is-fetching' : ''}>
          {data.items.length === 0 ? (
            <Empty>{f.q || f.filter ? '조건에 맞는 기록이 없습니다.' : '초대한 기록이 없습니다.'}</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>시각</th>
                    <th>회원</th>
                    <th>방법</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((r) => (
                    <tr key={r.id}>
                      <td className="nowrap small">{fmtDateTime(r.createdAt)}</td>
                      <td>
                        <Person id={r.userId} name={r.userName} email={r.userEmail} />
                      </td>
                      <td>
                        <Badge tone="beige">{labelOf(CHANNEL_OPTIONS, r.channel)}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination page={data.page} size={data.size || PAGE_SIZE} total={data.total} onPage={f.setPage} />
        </Card>
      ) : null}
    </>
  );
};

export const InvitesPage = () => {
  const [params, setParams] = useSearchParams();

  const tab: TTab = params.get('tab') === 'shares' ? 'shares' : 'signups';

  const changeTab = (t: TTab) => setParams(new URLSearchParams({ tab: t }));

  return (
    <>
      <PageHeader
        title="친구 초대"
        desc="초대 코드로 가입한 이력과 앱에서 초대를 보낸 이력(최신순). 없는 코드로 가입해도 가입은 되고 '코드 없음'으로 남습니다. Android 공유 시트는 실제로 보냈는지 알 수 없어 연 기록입니다."
      />
      <div className="mb-md">
        <Segmented
          value={tab}
          options={[
            { value: 'signups', label: '가입 이력' },
            { value: 'shares', label: '초대한 이력' },
          ]}
          onChange={changeTab}
        />
      </div>
      {/* 탭마다 필터 입력값을 새로 시작하도록 key를 준다 */}
      {tab === 'signups' ? <SignupHistory key="signups" /> : <ShareHistory key="shares" />}
    </>
  );
};
