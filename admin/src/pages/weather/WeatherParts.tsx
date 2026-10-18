// 날씨 화면 4개가 함께 쓰는 부품: 탭, 지금 날씨 한 줄, 일별 예보 표, 실행 상세 패널 (doc/17 6장)
import type { IWeatherCurrent, IWeatherDay, IWeatherRun } from '@/api';

import { Badge, Drawer, Empty, ErrorBox, Loading } from '@/components/ui';

import { NavLink } from 'react-router';

import { fmtDateTime, fmtMs } from '@/lib/format';
import { useWeatherRun } from '@/lib/queries';
import {
  COND_ICON,
  COND_TEXT,
  ERROR_TEXT,
  fmtTemp,
  RUN_STATUS_TEXT,
  RUN_STATUS_TONE,
  runDurationMs,
  SKIP_TEXT,
  TRIGGER_TEXT,
} from '@/lib/weather';

const TABS = [
  { to: '/weather/schedule', label: '스케줄' },
  { to: '/weather/providers', label: '공급자' },
  { to: '/weather/calls', label: '호출 이력' },
  { to: '/weather/snapshots', label: '저장된 날씨' },
];

export const WeatherTabs = () => {
  return (
    <div className="tabs mb-md" role="tablist">
      {TABS.map((t) => (
        <NavLink key={t.to} to={t.to} className={({ isActive }) => (isActive ? 'tab on' : 'tab')} role="tab">
          {t.label}
        </NavLink>
      ))}
    </div>
  );
};

export const RunStatusBadge = ({ run }: { run: Pick<IWeatherRun, 'status' | 'skipReason'> }) => {
  const title = run.skipReason ? (SKIP_TEXT[run.skipReason] ?? run.skipReason) : undefined;
  return (
    <Badge tone={RUN_STATUS_TONE[run.status] ?? 'gray'} title={title}>
      {RUN_STATUS_TEXT[run.status] ?? run.status}
      {run.skipReason ? ` · ${SKIP_TEXT[run.skipReason] ?? run.skipReason}` : ''}
    </Badge>
  );
};

/** 지금 날씨 한 줄: ☀ 18.3° 맑음 (체감 17.6°) */
export const CurrentLine = ({ current }: { current: IWeatherCurrent | null }) => {
  if (!current) return <span className="muted">—</span>;
  return (
    <span className="nowrap">
      <span aria-hidden>{COND_ICON[current.cond] ?? ''}</span> <strong>{fmtTemp(current.temp)}</strong>{' '}
      {current.condText ?? COND_TEXT[current.cond] ?? current.cond}
      {current.feelsLike !== null ? <span className="muted small"> (체감 {fmtTemp(current.feelsLike)})</span> : null}
    </span>
  );
};

/** 일별 예보 표. 원문 상태 → 앱 상태(맑음·흐림·비)를 함께 보여 매핑을 확인한다 */
export const DaysTable = ({ days }: { days: IWeatherDay[] }) => {
  if (days.length === 0) return <Empty>예보가 없습니다.</Empty>;
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>날짜</th>
            <th className="num">최저</th>
            <th className="num">평균</th>
            <th className="num">최고</th>
            <th className="num">강수확률</th>
            <th>원문 상태 → 앱 상태</th>
          </tr>
        </thead>
        <tbody>
          {days.map((d) => (
            <tr key={d.date}>
              <td className="mono small">{d.date}</td>
              <td className="num">{fmtTemp(d.min)}</td>
              <td className="num">{fmtTemp(d.temp)}</td>
              <td className="num">{fmtTemp(d.max)}</td>
              <td className="num">{d.rainProb}%</td>
              <td className="small">
                {d.condText ?? '—'} → {COND_ICON[d.cond]} {COND_TEXT[d.cond] ?? d.cond}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

/** 실행 하나의 상세: 요약 + 그 실행의 호출 (호출한 순서). 실행 중이면 1초마다 다시 읽는다 */
export const RunDetailDrawer = ({ runId, onClose }: { runId: string; onClose: () => void }) => {
  const { data, error, isLoading, refetch } = useWeatherRun(runId);

  const run = data?.run;
  return (
    <Drawer title={`날씨 실행 #${runId}`} onClose={onClose} wide>
      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {run ? (
        <>
          <dl className="kv">
            <dt>상태</dt>
            <dd>
              <RunStatusBadge run={run} />
            </dd>
            <dt>트리거</dt>
            <dd>
              {TRIGGER_TEXT[run.trigger] ?? run.trigger}
              {run.requestedByEmail ? <span className="muted small"> · {run.requestedByEmail}</span> : null}
            </dd>
            <dt>지역</dt>
            <dd>{run.regionCode ?? '활성 지역 전체'}</dd>
            <dt>결과</dt>
            <dd>
              성공 {run.successCount} · 실패 {run.failCount}
              {run.fallbackCount > 0 ? ` · 예비 ${run.fallbackCount}` : ''}
            </dd>
            <dt>시각</dt>
            <dd>
              {fmtDateTime(run.startedAt)} → {run.finishedAt ? fmtDateTime(run.finishedAt) : '실행 중'} (
              {fmtMs(runDurationMs(run.startedAt, run.finishedAt))})
            </dd>
          </dl>
          <h3 className="section-title">호출 {data.calls.length}건</h3>
          {data.calls.length === 0 ? (
            <Empty>호출이 없습니다 (건너뛴 실행이거나 아직 시작 전).</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>시각</th>
                    <th>지역</th>
                    <th>공급자</th>
                    <th>결과</th>
                    <th className="num">HTTP</th>
                    <th className="num">소요</th>
                    <th>오류</th>
                  </tr>
                </thead>
                <tbody>
                  {data.calls.map((c) => (
                    <tr key={c.id}>
                      <td className="nowrap small">{fmtDateTime(c.calledAt)}</td>
                      <td className="mono small">{c.regionCode}</td>
                      <td className="mono small">
                        {c.providerCode} {c.fallback ? <Badge tone="beige">예비</Badge> : null}
                      </td>
                      <td>{c.success ? <Badge tone="green">성공</Badge> : <Badge tone="red">실패</Badge>}</td>
                      <td className="num small">{c.httpStatus ?? '—'}</td>
                      <td className="num small">{fmtMs(c.durationMs)}</td>
                      <td className="small" title={c.errorMessage ?? undefined}>
                        {c.errorCode ? (
                          <span className="text-danger">{ERROR_TEXT[c.errorCode] ?? c.errorCode}</span>
                        ) : (
                          '—'
                        )}
                        {c.errorMessage ? <div className="muted small">{c.errorMessage}</div> : null}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      ) : null}
    </Drawer>
  );
};
