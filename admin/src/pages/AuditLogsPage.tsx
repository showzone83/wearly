// 변경 이력: 코드·AI 공급자·회원·앱 설정·로그인 세션을 누가 언제 어떻게 바꿨는지 (before/after)
import { Fragment, useState } from 'react';

import { api } from '@/api';
import type { IAuditLog, IAuditQuery } from '@/api';

import { Badge, Card, Empty, ErrorBox, JsonView, Loading, PageHeader, Pagination } from '@/components/ui';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { themeName } from '@/lib/appTheme';
import { fmtDateTime } from '@/lib/format';
import { qk } from '@/lib/queries';

const PAGE_SIZE = 30;

/** 서버가 쓰는 targetType 값은 계약에 정해지지 않아, 알려진 후보와 받은 값을 함께 보여 준다 */
const TARGET_TYPES: { value: string; label: string }[] = [
  { value: 'CODE', label: '코드' },
  { value: 'AI_PROVIDER', label: 'AI 공급자' },
  { value: 'USER', label: '회원' },
  // 앱 테마 변경(APP_THEME_UPDATE, doc/12 3장). 서버가 다른 값을 쓰면 아래 extraTypes로 탭이 생긴다
  { value: 'APP_SETTING', label: '앱 설정' },
  // 강제 로그아웃(doc/16 3장). 회원 단위(USER_SESSIONS_REVOKE)는 서버가 USER로 남길 수도 있다
  { value: 'SESSION', label: '로그인 세션' },
];

/** 동작 라벨. 없는 동작은 원문(SESSION_REVOKE 같은 값) 그대로 보여 준다 */
const ACTION_LABELS: Record<string, string> = {
  SESSION_REVOKE: '세션 로그아웃',
  USER_SESSIONS_REVOKE: '모든 기기 로그아웃',
};

const actionTone = (action: string): 'red' | 'green' | 'gray' => {
  return /DELETE|REVOKE/i.test(action) ? 'red' : /CREATE/i.test(action) ? 'green' : 'gray';
};

const targetLabel = (t: string): string => {
  return TARGET_TYPES.find((x) => x.value === t)?.label ?? t;
};

/** 값 하나짜리 변경. 테마 변경은 before·after가 themeId 문자열이거나 { themeId } 객체다 (doc/12 3장) */
const unwrap = (v: unknown): unknown => {
  if (!v || typeof v !== 'object' || Array.isArray(v)) return v;
  const keys = Object.keys(v);
  return keys.length === 1 && keys[0] === 'themeId' ? (v as { themeId: unknown }).themeId : v;
};

/** before/after가 객체가 아닌 값(테마 id 등)일 때 '전 → 후' 한 줄. 테마 변경은 테마 이름으로 */
const valueChange = (r: IAuditLog): string | null => {
  const before = unwrap(r.before);
  const after = unwrap(r.after);
  const simple = (v: unknown) => v === null || v === undefined || typeof v !== 'object';
  if (!simple(before) || !simple(after) || before === after) return null;
  const show = (v: unknown) =>
    v === null || v === undefined ? '—' : /THEME/i.test(r.action) ? themeName(String(v)) : String(v);
  return `${show(before)} → ${show(after)}`;
};

/** before/after에서 값이 바뀐 최상위 키 */
const changedKeys = (before: unknown, after: unknown): string[] => {
  const b = (before && typeof before === 'object' ? before : {}) as Record<string, unknown>;
  const a = (after && typeof after === 'object' ? after : {}) as Record<string, unknown>;
  const keys = new Set([...Object.keys(b), ...Object.keys(a)]);
  return [...keys].filter((k) => JSON.stringify(b[k]) !== JSON.stringify(a[k]));
};

export const AuditLogsPage = () => {
  const [open, setOpen] = useState<string | null>(null);

  const [params, setParams] = useSearchParams();
  // 순서 예외: 바로 아래 useQuery의 인자가 query를 읽는다
  const query: IAuditQuery = {
    targetType: params.get('targetType') ?? undefined,
    page: Number(params.get('page') ?? 0) || 0,
    size: PAGE_SIZE,
  };
  const { data, error, isLoading, isFetching, refetch } = useQuery({
    queryKey: qk.audit(query),
    queryFn: () => api.audit.list(query),
    placeholderData: keepPreviousData,
  });

  // 알려진 후보에 없는 대상 종류(지금 고른 값, 받은 행의 값)도 탭으로 보여 준다
  const extraTypes = [query.targetType, ...(data?.items.map((r) => r.targetType) ?? [])].filter(
    (t, i, all): t is string => !!t && !TARGET_TYPES.some((x) => x.value === t) && all.indexOf(t) === i,
  );

  const setType = (t: string) => setParams(t ? { targetType: t } : {});
  const setPage = (p: number) => {
    const next = new URLSearchParams(params);
    next.set('page', String(p));
    setParams(next);
  };

  return (
    <>
      <PageHeader title="변경 이력" desc="관리자가 바꾼 코드·AI 공급자·회원·앱 설정 기록과 강제 로그아웃" />
      <div className="tabs">
        {[{ value: '', label: '전체' }, ...TARGET_TYPES, ...extraTypes.map((t) => ({ value: t, label: t }))].map(
          (t) => (
            <button
              type="button"
              key={t.value}
              className={(query.targetType ?? '') === t.value ? 'tab on' : 'tab'}
              onClick={() => setType(t.value)}
            >
              {t.label}
            </button>
          ),
        )}
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
                    const change = valueChange(r);
                    const isOpen = open === r.id;
                    return (
                      <Fragment key={r.id}>
                        <tr className="clickable" onClick={() => setOpen(isOpen ? null : r.id)}>
                          <td className="nowrap small">{fmtDateTime(r.createdAt)}</td>
                          <td className="small">
                            {r.adminEmail ?? r.adminName ?? (r.adminUserId ? `#${r.adminUserId}` : '—')}
                          </td>
                          <td>
                            <Badge tone={actionTone(r.action)} title={r.action}>
                              {ACTION_LABELS[r.action] ?? r.action}
                            </Badge>
                          </td>
                          <td>
                            {targetLabel(r.targetType)} <span className="mono small">{r.targetId ?? ''}</span>
                          </td>
                          <td className="small">
                            {change ? (
                              change
                            ) : keys.length ? (
                              keys.slice(0, 6).join(', ') + (keys.length > 6 ? ` 외 ${keys.length - 6}` : '')
                            ) : (
                              <span className="muted">—</span>
                            )}
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
