// 날씨 > 호출 이력: 공급자 HTTP 호출 한 번이 한 줄. 요약 칩 → 필터(URL 쿼리) → 표 → 행 상세 (doc/17 6장)
// 실행 밖 호출(다른 장소 날씨·장소 검색·평년값, doc/19)은 runId·trigger가 없어 '종류' 칸으로 구분한다.
import { useState } from 'react';
import type { FormEvent } from 'react';

import { api } from '@/api';
import type { IWeatherCall, IWeatherCallQuery } from '@/api';

import { Badge, Button, Card, Drawer, Empty, ErrorBox, Loading, PageHeader, Pagination, Stat } from '@/components/ui';
import { RunDetailDrawer, WeatherTabs } from '@/pages/weather/WeatherParts';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { fmtBytes, fmtDateTime, fmtMs, fmtNum, todaySeoul } from '@/lib/format';
import { qk, useCodeBook, useWeatherProviders } from '@/lib/queries';
import {
  AUX_PROVIDERS,
  CALL_KIND_DESC,
  CALL_KIND_TEXT,
  CALL_KINDS,
  callKindOf,
  callKindText,
  callRegionText,
  ERROR_TEXT,
  gridKeyText,
  OPEN_METEO_TERMS,
  TRIGGER_TEXT,
  usageLevel,
} from '@/lib/weather';
import type { TCallKind } from '@/lib/weather';

const PAGE_SIZE = 30;
/** 서버로 보내는 필터 */
const FILTER_KEYS = [
  'providerCode',
  'callKind',
  'regionCode',
  'success',
  'fallback',
  'errorCode',
  'from',
  'to',
] as const;
const URL_KEYS = FILTER_KEYS;
type TFilterKey = (typeof URL_KEYS)[number];

const emptyDraft = (): Record<TFilterKey, string> => {
  return Object.fromEntries(URL_KEYS.map((k) => [k, ''])) as Record<TFilterKey, string>;
};

/** 종류 칸: 실행 호출이면 트리거(주기·지금 실행…)를 작게 붙인다 */
const KindCell = ({ c }: { c: IWeatherCall }) => {
  const kind = callKindOf(c);
  return (
    <span className="nowrap" title={CALL_KIND_DESC[kind as TCallKind]}>
      {callKindText(kind)}
      {kind === 'FORECAST' && c.trigger ? (
        <span className="muted small"> · {TRIGGER_TEXT[c.trigger] ?? c.trigger}</span>
      ) : null}
    </span>
  );
};

export const WeatherCallsPage = () => {
  // 순서 예외: 필터 입력값(draft)의 초깃값이 URL 쿼리(params)를 읽는다
  const [params, setParams] = useSearchParams();

  const [draft, setDraft] = useState(
    () => Object.fromEntries(URL_KEYS.map((k) => [k, params.get(k) ?? ''])) as Record<TFilterKey, string>,
  );
  const [open, setOpen] = useState<IWeatherCall | null>(null);
  const [runId, setRunId] = useState<string | null>(null);

  const codes = useCodeBook();
  const providers = useWeatherProviders();
  const summary = useQuery({ queryKey: qk.weatherSummary, queryFn: () => api.weather.callSummary() });
  // 순서 예외: 바로 아래 useQuery의 인자가 query를 읽는다
  const query: IWeatherCallQuery = {
    ...Object.fromEntries(FILTER_KEYS.map((k) => [k, params.get(k) ?? undefined])),
    page: Number(params.get('page') ?? 0) || 0,
    size: PAGE_SIZE,
  };
  const { data, error, isLoading, isFetching, refetch } = useQuery({
    queryKey: qk.weatherCalls(query),
    queryFn: () => api.weather.calls(query),
    placeholderData: keepPreviousData,
  });

  const today = summary.data?.today;
  const rows = data?.items ?? [];
  const providerNames = Object.fromEntries((providers.data ?? []).map((p) => [p.code, p.displayName]));
  const auxProviders = Object.entries(AUX_PROVIDERS).filter(([code]) => !providerNames[code]);

  const apply = (e?: FormEvent) => {
    e?.preventDefault();
    const next = new URLSearchParams();
    for (const k of URL_KEYS) if (draft[k]) next.set(k, draft[k]);
    setParams(next);
  };
  const reset = () => {
    setDraft(emptyDraft());
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
        title="날씨"
        desc="공급자 HTTP 호출 기록. 오류 문구에 키는 남지 않습니다. 보관 기간이 지나면 매일 04:00에 지웁니다."
      />
      <WeatherTabs />
      <ErrorBox error={summary.error} onRetry={() => void summary.refetch()} />
      {today ? (
        <div className="stats">
          <Stat
            label="오늘 호출"
            value={fmtNum(today.calls)}
            sub={`실패 ${fmtNum(today.failures)} · 실행 밖 호출(다른 장소·검색·평년값) 포함`}
          />
          <Stat label="오늘 예비 전환" value={fmtNum(today.fallbacks)} sub="1순위가 아닌 공급자로 받은 호출" />
          <Stat label="오늘 평균 소요" value={fmtMs(today.avgDurationMs)} />
          {summary.data?.months.map((m) => (
            <Stat
              key={m.providerCode}
              label={`${m.displayName} 이번 달`}
              value={fmtNum(m.used)}
              sub={
                (m.quota === null
                  ? '한도 없음'
                  : `한도 ${fmtNum(m.quota)}회${usageLevel(m.used, m.quota) === 'over' ? ' · 도달' : ''}`) +
                ' · 다른 장소·검색 포함'
              }
            />
          ))}
          {summary.data?.auxiliaryMonths?.length ? (
            summary.data.auxiliaryMonths.map((m) => (
              <Stat
                key={m.providerCode}
                label={`${m.displayName} 이번 달`}
                value={fmtNum(m.used)}
                sub={`월 한도 없음 · 키 없음 · ${AUX_PROVIDERS[m.providerCode]?.use ?? '보조'}`}
              />
            ))
          ) : (
            <Stat
              label="Open-Meteo (평년값·장소 검색)"
              value="월 한도 없음"
              sub="키 없음 · 공급자 표 밖 · 사용량은 호출 이력에서"
            />
          )}
        </div>
      ) : null}
      {today ? <p className="muted small mt-sm">Open-Meteo: {OPEN_METEO_TERMS}</p> : null}

      <Card className="mt-md">
        <form className="filters" onSubmit={apply}>
          <label>
            <span className="field-label">공급자</span>
            <select value={draft.providerCode} onChange={(e) => setDraft({ ...draft, providerCode: e.target.value })}>
              <option value="">전체</option>
              {(providers.data ?? []).map((p) => (
                <option key={p.code} value={p.code}>
                  {p.displayName}
                </option>
              ))}
              {auxProviders.map(([code, a]) => (
                <option key={code} value={code}>
                  {a.label} ({a.use})
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="field-label">종류</span>
            <select value={draft.callKind} onChange={(e) => setDraft({ ...draft, callKind: e.target.value })}>
              <option value="">전체</option>
              {CALL_KINDS.map((k) => (
                <option key={k} value={k}>
                  {CALL_KIND_TEXT[k]}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="field-label">지역</span>
            <select value={draft.regionCode} onChange={(e) => setDraft({ ...draft, regionCode: e.target.value })}>
              <option value="">전체</option>
              {codes.group('REGION').map((r) => (
                <option key={r.code} value={r.code}>
                  {r.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="field-label">결과</span>
            <select value={draft.success} onChange={(e) => setDraft({ ...draft, success: e.target.value })}>
              <option value="">전체</option>
              <option value="true">성공</option>
              <option value="false">실패</option>
            </select>
          </label>
          <label>
            <span className="field-label">예비</span>
            <select value={draft.fallback} onChange={(e) => setDraft({ ...draft, fallback: e.target.value })}>
              <option value="">전체</option>
              <option value="true">예비 공급자</option>
              <option value="false">1순위</option>
            </select>
          </label>
          <label>
            <span className="field-label">오류</span>
            <select value={draft.errorCode} onChange={(e) => setDraft({ ...draft, errorCode: e.target.value })}>
              <option value="">전체</option>
              {Object.entries(ERROR_TEXT).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
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
      </Card>

      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isFetching ? 'is-fetching' : ''}>
          {rows.length === 0 ? (
            <Empty>조건에 맞는 호출이 없습니다.</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table table-hover">
                <thead>
                  <tr>
                    <th>시각</th>
                    <th>지역</th>
                    <th>공급자</th>
                    <th>종류</th>
                    <th className="num">HTTP</th>
                    <th>결과</th>
                    <th className="num">소요</th>
                    <th className="num">응답</th>
                    <th>오류</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((c) => (
                    <tr key={c.id} className="clickable" onClick={() => setOpen(c)}>
                      <td className="nowrap small">{fmtDateTime(c.calledAt)}</td>
                      <td className="small nowrap">{callRegionText(c, (code) => codes.label('REGION', code))}</td>
                      <td className="mono small">
                        {c.providerCode} {c.fallback ? <Badge tone="beige">예비</Badge> : null}
                      </td>
                      <td className="small">
                        <KindCell c={c} />
                      </td>
                      <td className="num small">{c.httpStatus ?? '—'}</td>
                      <td>{c.success ? <Badge tone="green">성공</Badge> : <Badge tone="red">실패</Badge>}</td>
                      <td className="num small">{fmtMs(c.durationMs)}</td>
                      <td className="num small">{fmtBytes(c.responseBytes)}</td>
                      <td className="small ellipsis">
                        {c.errorCode ? (
                          <span className="text-danger">{ERROR_TEXT[c.errorCode] ?? c.errorCode}</span>
                        ) : (
                          '—'
                        )}
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

      {open ? (
        <Drawer title={`호출 #${open.id}`} onClose={() => setOpen(null)}>
          <dl className="kv">
            <dt>시각</dt>
            <dd>{fmtDateTime(open.calledAt)}</dd>
            <dt>종류</dt>
            <dd>
              {callKindText(callKindOf(open))} <span className="mono muted small">{open.callKind}</span>
              {CALL_KIND_DESC[callKindOf(open) as TCallKind] ? (
                <div className="muted small">{CALL_KIND_DESC[callKindOf(open) as TCallKind]}</div>
              ) : null}
            </dd>
            <dt>지역</dt>
            <dd>
              {callRegionText(open, (code) => codes.label('REGION', code))}{' '}
              {open.regionCode && !gridKeyText(open.regionCode) ? (
                <span className="mono muted small">{open.regionCode}</span>
              ) : null}
              {gridKeyText(open.regionCode) ? (
                <div className="muted small">격자 키(소수 첫째 자리, 약 11km)</div>
              ) : null}
            </dd>
            <dt>공급자</dt>
            <dd>
              <span className="mono">{open.providerCode}</span>{' '}
              {AUX_PROVIDERS[open.providerCode] ? (
                <span className="muted small">{AUX_PROVIDERS[open.providerCode].label} · 월 한도 없음</span>
              ) : null}
              {open.fallback ? <Badge tone="beige">예비</Badge> : null}
            </dd>
            <dt>결과</dt>
            <dd>
              {open.success ? <Badge tone="green">성공</Badge> : <Badge tone="red">실패</Badge>} HTTP{' '}
              {open.httpStatus ?? '—'} · {fmtMs(open.durationMs)} · {fmtBytes(open.responseBytes)}
            </dd>
            {open.errorCode ? (
              <>
                <dt>오류</dt>
                <dd className="text-danger">
                  {ERROR_TEXT[open.errorCode] ?? open.errorCode} <span className="mono small">{open.errorCode}</span>
                  <div className="small">{open.errorMessage}</div>
                </dd>
              </>
            ) : null}
            <dt>실행</dt>
            <dd>
              {open.runId ? (
                <button type="button" className="link-btn" onClick={() => setRunId(open.runId)}>
                  실행 #{open.runId} ({open.trigger ? (TRIGGER_TEXT[open.trigger] ?? open.trigger) : '—'}) 열기
                </button>
              ) : (
                <span className="muted">없음 (앱 요청 때 부른 실행 밖 호출)</span>
              )}
            </dd>
          </dl>
        </Drawer>
      ) : null}
      {runId ? <RunDetailDrawer runId={runId} onClose={() => setRunId(null)} /> : null}
    </>
  );
};
