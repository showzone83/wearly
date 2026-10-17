// AI 분석 기록: 필터 → 표 → 상세(결과 JSON 전체)
import { useState } from 'react';
import type { FormEvent } from 'react';

import { api } from '@/api';
import type { IAiLogQuery } from '@/api';

import {
  Badge,
  Button,
  Card,
  Drawer,
  Empty,
  ErrorBox,
  JsonView,
  Loading,
  PageHeader,
  Pagination,
} from '@/components/ui';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { fmtBytes, fmtDateTime, fmtMs, fmtNum, todaySeoul } from '@/lib/format';
import { qk, useProviders } from '@/lib/queries';

const PAGE_SIZE = 20;
const FILTER_KEYS = ['providerId', 'status', 'userEmail', 'from', 'to'] as const;

const FEATURE_LABEL: Record<string, string> = { ITEM_ANALYZE: '사진 분석', ADMIN_TEST: '관리자 시험' };

export const AiLogsPage = () => {
  // 순서 예외: 필터 입력값(draft)의 초깃값이 URL 쿼리(params)를 읽는다
  const [params, setParams] = useSearchParams();

  const [openId, setOpenId] = useState<string | null>(null);
  // 필터 입력값 (검색 버튼을 눌러야 적용)
  const [draft, setDraft] = useState(
    () =>
      Object.fromEntries(FILTER_KEYS.map((k) => [k, params.get(k) ?? ''])) as Record<
        (typeof FILTER_KEYS)[number],
        string
      >,
  );

  const providers = useProviders();
  // 순서 예외: 바로 아래 useQuery의 인자가 query를 읽는다
  const query: IAiLogQuery = {
    providerId: params.get('providerId') ?? undefined,
    status: params.get('status') ?? undefined,
    userEmail: params.get('userEmail') ?? undefined,
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
    page: Number(params.get('page') ?? 0) || 0,
    size: PAGE_SIZE,
  };
  const { data, error, isLoading, isFetching, refetch } = useQuery({
    queryKey: qk.aiLogs(query),
    queryFn: () => api.aiLogs.list(query),
    placeholderData: keepPreviousData,
  });

  const providerIds = Array.from(new Set([...(providers.data ?? []).map((p) => p.providerId), 'mock']));

  const apply = (e?: FormEvent) => {
    e?.preventDefault();
    const next = new URLSearchParams();
    for (const k of FILTER_KEYS) if (draft[k]) next.set(k, draft[k]);
    setParams(next);
  };
  const reset = () => {
    setDraft({ providerId: '', status: '', userEmail: '', from: '', to: '' });
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
        title="AI 분석 기록"
        desc="앱의 사진 분석과 관리자 시험 분석 호출. 이미지는 저장하지 않고 결과와 메타만 남습니다."
      />
      <Card>
        <form className="filters" onSubmit={apply}>
          <label>
            <span className="field-label">공급자</span>
            <select value={draft.providerId} onChange={(e) => setDraft({ ...draft, providerId: e.target.value })}>
              <option value="">전체</option>
              {providerIds.map((id) => (
                <option key={id} value={id}>
                  {id}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="field-label">상태</span>
            <select value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value })}>
              <option value="">전체</option>
              <option value="SUCCESS">성공</option>
              <option value="FAILED">실패</option>
            </select>
          </label>
          <label>
            <span className="field-label">회원 이메일</span>
            <input
              type="search"
              value={draft.userEmail}
              onChange={(e) => setDraft({ ...draft, userEmail: e.target.value })}
              placeholder="user@example.com"
            />
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
      </Card>

      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isFetching ? 'is-fetching' : ''}>
          {data.items.length === 0 ? (
            <Empty>조건에 맞는 기록이 없습니다.</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table table-hover">
                <thead>
                  <tr>
                    <th>시각</th>
                    <th>회원</th>
                    <th>기능</th>
                    <th>공급자 · 모델</th>
                    <th>상태</th>
                    <th className="num">지연</th>
                    <th className="num">토큰 (입력/출력)</th>
                    <th className="num">이미지</th>
                    <th>결과 요약 · 오류</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((r) => (
                    <tr key={r.id} onClick={() => setOpenId(r.id)} className="clickable">
                      <td className="nowrap small">{fmtDateTime(r.createdAt)}</td>
                      <td className="small">{r.userEmail ?? <span className="muted">—</span>}</td>
                      <td>
                        {r.feature === 'ADMIN_TEST' ? (
                          <Badge tone="beige">관리자 시험</Badge>
                        ) : (
                          <span className="small">{FEATURE_LABEL[r.feature] ?? r.feature}</span>
                        )}
                      </td>
                      <td>
                        <span className="mono small">{r.providerId}</span>
                        <div className="mono muted small">{r.model ?? '—'}</div>
                      </td>
                      <td>
                        {r.status === 'SUCCESS' ? (
                          <Badge tone="green">성공</Badge>
                        ) : (
                          <Badge tone="red">{r.status === 'FAILED' ? '실패' : r.status}</Badge>
                        )}
                      </td>
                      <td className="num">{fmtMs(r.latencyMs)}</td>
                      <td className="num small">
                        {fmtNum(r.inputTokens)} / {fmtNum(r.outputTokens)}
                      </td>
                      <td className="num small">{fmtBytes(r.imageBytes)}</td>
                      <td className="small ellipsis">
                        {r.errorCode ? <span className="mono text-danger">{r.errorCode}</span> : (r.summary ?? '—')}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination page={data.page} size={data.size || PAGE_SIZE} total={data.total} onPage={setPage} />
        </Card>
      ) : null}

      {openId ? <LogDetail id={openId} onClose={() => setOpenId(null)} /> : null}
    </>
  );
};

const LogDetail = ({ id, onClose }: { id: string; onClose: () => void }) => {
  const { data, error, isLoading, refetch } = useQuery({ queryKey: qk.aiLog(id), queryFn: () => api.aiLogs.get(id) });

  const result = data && 'result' in data ? data.result : undefined;
  return (
    <Drawer title={`분석 기록 #${id}`} onClose={onClose} wide>
      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {data ? (
        <>
          <dl className="kv">
            <dt>시각</dt>
            <dd>{fmtDateTime(data.createdAt)}</dd>
            <dt>회원</dt>
            <dd>{data.userEmail ?? '—'}</dd>
            <dt>기능</dt>
            <dd>{data.feature ? (FEATURE_LABEL[data.feature] ?? data.feature) : '—'}</dd>
            <dt>공급자 · 모델</dt>
            <dd className="mono">
              {String(data.providerId ?? data.provider ?? '—')} · {data.model ?? '—'}
            </dd>
            <dt>상태</dt>
            <dd>
              {data.status ?? '—'} {data.errorCode ? <span className="mono text-danger">{data.errorCode}</span> : null}
            </dd>
            <dt>지연 · 토큰</dt>
            <dd>
              {fmtMs(data.latencyMs)} · {fmtNum(data.inputTokens)} / {fmtNum(data.outputTokens)}
            </dd>
          </dl>
          <h3 className="section-title">결과 JSON</h3>
          <JsonView value={result ?? null} />
          <details className="mt-sm">
            <summary className="muted small">응답 전체</summary>
            <JsonView value={data} />
          </details>
        </>
      ) : null}
    </Drawer>
  );
};
