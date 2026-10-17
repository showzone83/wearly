// 변경 이력: 코드·AI 공급자·회원을 누가 언제 어떻게 바꿨는지 (before/after)
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Fragment, useState } from 'react';
import { useSearchParams } from 'react-router';

import { api, type IAuditLog, type IAuditQuery } from '@/api';
import { Badge, Card, Empty, ErrorBox, JsonView, Loading, PageHeader, Pagination } from '@/components/ui';
import { fmtDateTime } from '@/lib/format';
import { qk } from '@/lib/queries';

const PAGE_SIZE = 30;

/** 서버가 쓰는 targetType 값은 계약에 정해지지 않아, 알려진 후보와 받은 값을 함께 보여 준다 */
const TARGET_TYPES: { value: string; label: string }[] = [
  { value: 'CODE', label: '코드' },
  { value: 'AI_PROVIDER', label: 'AI 공급자' },
  { value: 'USER', label: '회원' },
];

const targetLabel = (t: string): string => {
  return TARGET_TYPES.find((x) => x.value === t)?.label ?? t;
};

/** before/after에서 값이 바뀐 최상위 키 */
const changedKeys = (before: unknown, after: unknown): string[] => {
  const b = (before && typeof before === 'object' ? before : {}) as Record<string, unknown>;
  const a = (after && typeof after === 'object' ? after : {}) as Record<string, unknown>;
  const keys = new Set([...Object.keys(b), ...Object.keys(a)]);
  return [...keys].filter((k) => JSON.stringify(b[k]) !== JSON.stringify(a[k]));
};

export const AuditLogsPage = () => {
  const [params, setParams] = useSearchParams();
  const [open, setOpen] = useState<string | null>(null);
  const query: IAuditQuery = { targetType: params.get('targetType') ?? undefined, page: Number(params.get('page') ?? 0) || 0, size: PAGE_SIZE };
  const { data, error, isLoading, isFetching, refetch } = useQuery({
    queryKey: qk.audit(query),
    queryFn: () => api.audit.list(query),
    placeholderData: keepPreviousData,
  });

  const setType = (t: string) => setParams(t ? { targetType: t } : {});
  const setPage = (p: number) => {
    const next = new URLSearchParams(params);
    next.set('page', String(p));
    setParams(next);
  };

  return (
    <>
      <PageHeader title="변경 이력" desc="관리자가 바꾼 코드·AI 공급자·회원 기록" />
      <div className="tabs">
        {[{ value: '', label: '전체' }, ...TARGET_TYPES].map((t) => (
          <button type="button" key={t.value} className={(query.targetType ?? '') === t.value ? 'tab on' : 'tab'} onClick={() => setType(t.value)}>
            {t.label}
          </button>
        ))}
      </div>
      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isFetching ? 'is-fetching' : ''}>
          {data.items.length === 0 ? (
            <Empty>변경 이력이 없습니다.</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>시각</th>
                    <th>관리자</th>
                    <th>동작</th>
                    <th>대상</th>
                    <th>바뀐 항목</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((r: IAuditLog) => {
                    const keys = changedKeys(r.before, r.after);
                    const isOpen = open === r.id;
                    return (
                      <Fragment key={r.id}>
                        <tr className="clickable" onClick={() => setOpen(isOpen ? null : r.id)}>
                          <td className="nowrap small">{fmtDateTime(r.createdAt)}</td>
                          <td className="small">{r.adminEmail ?? r.adminName ?? (r.adminUserId ? `#${r.adminUserId}` : '—')}</td>
                          <td>
                            <Badge tone={/DELETE/i.test(r.action) ? 'red' : /CREATE/i.test(r.action) ? 'green' : 'gray'}>{r.action}</Badge>
                          </td>
                          <td>
                            {targetLabel(r.targetType)} <span className="mono small">{r.targetId ?? ''}</span>
                          </td>
                          <td className="small">
                            {keys.length ? keys.slice(0, 6).join(', ') + (keys.length > 6 ? ` 외 ${keys.length - 6}` : '') : <span className="muted">—</span>}
                            <span className="muted"> {isOpen ? '▴' : '▾'}</span>
                          </td>
                        </tr>
                        {isOpen ? (
                          <tr className="detail-row">
                            <td colSpan={5}>
                              <div className="diff">
                                <div>
                                  <div className="field-label">변경 전</div>
                                  <JsonView value={r.before ?? null} maxHeight={360} />
                                </div>
                                <div>
                                  <div className="field-label">변경 후</div>
                                  <JsonView value={r.after ?? null} maxHeight={360} />
                                </div>
                              </div>
                            </td>
                          </tr>
                        ) : null}
                      </Fragment>
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
