// 옷장 도우미 (doc/26 6단계): 주인과 돕는 사람의 연결 목록(최근 요청순), 대리 작업 기록, 강제 해제.
// 검색·상태 필터·페이지는 URL 쿼리에 둔다. 강제 해제는 연결을 끊고 변경 이력(PAIRING_FORCE_END)을 남기며 양쪽에 알린다.
import { useState } from 'react';
import type { FormEvent } from 'react';

import { api } from '@/api';
import type { IPairingAction, IPairingQuery, IPairingRow, TPairingEndedBy, TPairingStatus } from '@/api';

import { Badge, Button, Card, Empty, ErrorBox, Loading, Notice, PageHeader, Pagination } from '@/components/ui';

import { useConfirmStore } from '@/stores/useConfirmStore';
import { useToastStore } from '@/stores/useToastStore';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { fmtDateTime } from '@/lib/format';
import { isUnsupportedApi, qk } from '@/lib/queries';

const PAGE_SIZE = 30;

const STATUS_OPTIONS: { value: TPairingStatus; label: string }[] = [
  { value: 'PENDING', label: '승인 대기' },
  { value: 'ACTIVE', label: '연결됨' },
  { value: 'ENDED', label: '끝남' },
];

const ENDED_BY_LABEL: Record<string, string> = {
  OWNER: '주인',
  HELPER: '돕는 사람',
  EXPIRED: '대기 만료',
  ADMIN: '관리자',
  WITHDRAWN: '탈퇴 신청',
};

const ACTION_LABEL: Record<string, string> = {
  ITEM_CREATE: '등록',
  ITEM_UPDATE: '수정',
  ITEM_RETIRE: '사용 중지',
  ITEM_RESTORE: '다시 사용',
};

const PERMISSION_LABEL: Record<string, string> = { VIEW: '보기만', EDIT: '등록·수정' };

const statusTone = (s: TPairingStatus) => (s === 'ACTIVE' ? 'green' : s === 'PENDING' ? 'beige' : 'gray');

const Person = ({ name, email, id }: { name: string; email: string | null; id: string }) => {
  return (
    <>
      <div>
        <strong>{name || '—'}</strong> <span className="small mono muted">#{id}</span>
      </div>
      <div className="small muted">{email ?? '—'}</div>
    </>
  );
};

/** 연결 하나의 대리 작업 기록 (펼쳤을 때만 받는다) */
const ActionsRow = ({ pairingId }: { pairingId: string }) => {
  const { data, error, isLoading, refetch } = useQuery({
    queryKey: qk.pairingActions(pairingId),
    queryFn: () => api.pairings.actions(pairingId),
    staleTime: 0,
  });

  return (
    <tr>
      <td colSpan={6}>
        <ErrorBox error={error} onRetry={() => void refetch()} />
        {isLoading ? <Loading /> : null}
        {data ? (
          data.length === 0 ? (
            <Empty>돕는 사람이 한 일이 아직 없습니다.</Empty>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>시각</th>
                  <th>한 일</th>
                  <th>옷</th>
                </tr>
              </thead>
              <tbody>
                {data.map((a: IPairingAction) => (
                  <tr key={a.id}>
                    <td className="nowrap small">{fmtDateTime(a.createdAt)}</td>
                    <td>
                      <Badge tone="beige">{ACTION_LABEL[a.action] ?? a.action}</Badge>
                    </td>
                    <td>
                      {a.itemName || '—'} <span className="small mono muted">#{a.itemId}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )
        ) : null}
      </td>
    </tr>
  );
};

export const PairingsPage = () => {
  /** 작업 기록을 펼친 연결 */
  const [openId, setOpenId] = useState<string | null>(null);
  // 순서 예외: 검색어 입력값(draft)의 초깃값이 URL 쿼리(params)를 읽는다
  const [params, setParams] = useSearchParams();

  const [draft, setDraft] = useState(() => params.get('q') ?? '');

  const queryClient = useQueryClient();
  const confirm = useConfirmStore((s) => s.ask);
  const toast = useToastStore((s) => s.show);
  const toastError = useToastStore((s) => s.showError);

  // 순서 예외: 바로 아래 useQuery의 인자가 query를 읽는다
  const query: IPairingQuery = {
    q: params.get('q') || undefined,
    status: params.get('status') || undefined,
    page: Number(params.get('page') ?? 0) || 0,
    size: PAGE_SIZE,
  };

  const { data, error, isLoading, isPlaceholderData, refetch } = useQuery({
    queryKey: qk.pairings(query),
    queryFn: () => api.pairings.list(query),
    placeholderData: keepPreviousData,
    // 앱에서 계속 바뀌므로 캐시를 바로 낡은 것으로 본다
    staleTime: 0,
  });

  // 순서 예외: useQuery가 읽는 query 값(파생 값)이 앞에 있어야 한다
  const forceEnd = useMutation({
    mutationFn: (row: IPairingRow) => api.pairings.end(row.id),
    onSuccess: async (_r, row) => {
      await queryClient.invalidateQueries({ queryKey: ['pairings'] });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
      toast(`${row.ownerName} · ${row.helperName}: 연결을 해제했습니다. 양쪽에 알림이 갑니다.`);
    },
    onError: (e) => {
      toastError(e);
      void queryClient.invalidateQueries({ queryKey: ['pairings'] });
    },
  });

  const unsupported = isUnsupportedApi(error);
  const status = params.get('status') ?? '';
  const q = params.get('q') ?? '';

  const apply = (next: { q: string; status: string }) => {
    const p = new URLSearchParams();
    if (next.q.trim()) p.set('q', next.q.trim());
    if (next.status) p.set('status', next.status);
    if (p.toString() === params.toString()) void queryClient.invalidateQueries({ queryKey: ['pairings'] });
    else setParams(p);
  };
  const submit = (e: FormEvent) => {
    e.preventDefault();
    apply({ q: draft, status });
  };
  const reset = () => {
    setDraft('');
    apply({ q: '', status: '' });
  };
  const setPage = (p: number) => {
    const next = new URLSearchParams(params);
    next.set('page', String(p));
    setParams(next);
  };
  const confirmEnd = (row: IPairingRow) => {
    void confirm({
      title: '연결 강제 해제',
      message:
        `주인 ${row.ownerName}(${row.ownerEmail ?? '—'})와 돕는 사람 ${row.helperName}(${row.helperEmail ?? '—'})의 연결을 끊을까요?\n` +
        '돕는 사람은 더 이상 주인 옷장을 볼 수 없고, 이미 등록한 옷은 주인 옷장에 남습니다. 양쪽에 알림이 가고 변경 이력에 남습니다.',
      confirmLabel: '해제',
      tone: 'danger',
    }).then((ok) => {
      if (ok) forceEnd.mutate(row);
    });
  };

  return (
    <>
      <PageHeader
        title="옷장 도우미"
        desc="주인과 돕는 사람의 연결(최근 요청순)과 돕는 사람이 한 일. 승인 대기는 7일이 지나면 앱에서 사라지지만 여기에는 승인 대기로 남습니다. 강제 해제는 변경 이력에 남고 양쪽에 알림이 갑니다."
      />

      <Card>
        <form className="filters" onSubmit={submit}>
          <label>
            <span className="field-label">검색</span>
            <input
              type="search"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="주인·돕는 사람 이메일·이름"
            />
          </label>
          <label>
            <span className="field-label">상태</span>
            <select value={status} onChange={(e) => apply({ q: draft, status: e.target.value })}>
              <option value="">전체</option>
              {STATUS_OPTIONS.map((o) => (
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
            <Button onClick={reset}>초기화</Button>
          </div>
        </form>
      </Card>

      {unsupported ? (
        <Notice tone="warn">
          서버가 아직 이 기능을 모릅니다. 백엔드에 옷장 도우미 관리 API(<span className="mono">GET /admin/pairings</span>, doc/26)가
          들어가면 이 화면이 채워집니다.
        </Notice>
      ) : (
        <ErrorBox error={error} onRetry={() => void refetch()} />
      )}
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isPlaceholderData ? 'is-fetching' : ''}>
          {data.items.length === 0 ? (
            <Empty>{q || status ? '조건에 맞는 연결이 없습니다.' : '옷장 도우미 연결이 없습니다.'}</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>요청 시각</th>
                    <th>주인</th>
                    <th>돕는 사람</th>
                    <th>상태</th>
                    <th>작업</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((r) => (
                    <FragmentRows
                      key={r.id}
                      row={r}
                      open={openId === r.id}
                      busy={forceEnd.isPending && forceEnd.variables?.id === r.id}
                      onToggle={() => setOpenId(openId === r.id ? null : r.id)}
                      onEnd={() => confirmEnd(r)}
                    />
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

/** 연결 한 줄과, 펼쳤을 때 그 아래 작업 기록 */
const FragmentRows = ({
  row,
  open,
  busy,
  onToggle,
  onEnd,
}: {
  row: IPairingRow;
  open: boolean;
  busy: boolean;
  onToggle: () => void;
  onEnd: () => void;
}) => {
  const endedBy: TPairingEndedBy | null = row.endedBy;
  return (
    <>
      <tr>
        <td className="nowrap small">{fmtDateTime(row.createdAt)}</td>
        <td>
          <Person id={row.ownerId} name={row.ownerName} email={row.ownerEmail} />
        </td>
        <td>
          <Person id={row.helperId} name={row.helperName} email={row.helperEmail} />
        </td>
        <td>
          <Badge tone={statusTone(row.status)}>
            {STATUS_OPTIONS.find((o) => o.value === row.status)?.label ?? row.status}
          </Badge>
          {row.permission ? <div className="small muted">{PERMISSION_LABEL[row.permission] ?? row.permission}</div> : null}
          {row.status === 'ENDED' ? (
            <div className="small muted">
              {endedBy ? (ENDED_BY_LABEL[endedBy] ?? endedBy) : '—'} · {row.endedAt ? fmtDateTime(row.endedAt) : '—'}
            </div>
          ) : null}
        </td>
        <td className="small">
          <div>{row.actionCount}건</div>
          <div className="muted">{row.lastActionAt ? `마지막 ${fmtDateTime(row.lastActionAt)}` : '—'}</div>
        </td>
        <td className="nowrap">
          {row.actionCount > 0 ? (
            <Button size="sm" onClick={onToggle}>
              {open ? '기록 닫기' : '작업 기록'}
            </Button>
          ) : null}{' '}
          {row.status !== 'ENDED' ? (
            <Button size="sm" variant="danger" busy={busy} onClick={onEnd}>
              강제 해제
            </Button>
          ) : null}
        </td>
      </tr>
      {open ? <ActionsRow pairingId={row.id} /> : null}
    </>
  );
};
