// 날씨 > 스케줄: 켜기·주기·예보 일수·차단 기준을 바꾸고, 지금 실행하고, 최근 실행을 본다 (doc/17 3·6장).
// 서버는 매 실행 뒤 DB 설정을 다시 읽어 다음 실행 시각을 정하므로 저장하면 재시작 없이 반영된다.
import { useState } from 'react';

import { api, isApiError } from '@/api';
import type { IWeatherSetting, TWeatherSettingUpdate } from '@/api';

import {
  Button,
  Card,
  Empty,
  ErrorBox,
  Field,
  Loading,
  Modal,
  Notice,
  PageHeader,
  Stat,
  Switch,
} from '@/components/ui';
import { RunDetailDrawer, RunStatusBadge, WeatherTabs } from '@/pages/weather/WeatherParts';

import { useToastStore } from '@/stores/useToastStore';

import { useQuery } from '@tanstack/react-query';

import { fieldErrorOf } from '@/lib/errorText';
import { fmtDateTime, fmtMs, fmtNum } from '@/lib/format';
import {
  qk,
  useCodeBook,
  useUpdateWeatherSetting,
  useWeatherProviders,
  useWeatherRefresh,
  useWeatherSchedule,
} from '@/lib/queries';
import { estimateMonthlyCalls, runDurationMs, TRIGGER_TEXT } from '@/lib/weather';

const RECENT_RUNS = 10;

type TDraft = Record<keyof TWeatherSettingUpdate, string | boolean>;

const toDraft = (s: IWeatherSetting): TDraft => {
  return {
    enabled: s.enabled,
    intervalMinutes: String(s.intervalMinutes),
    forecastDays: String(s.forecastDays),
    failureThreshold: String(s.failureThreshold),
    cooldownMinutes: String(s.cooldownMinutes),
  };
};

const toBody = (d: TDraft): TWeatherSettingUpdate => {
  return {
    enabled: d.enabled === true,
    intervalMinutes: Number(d.intervalMinutes),
    forecastDays: Number(d.forecastDays),
    failureThreshold: Number(d.failureThreshold),
    cooldownMinutes: Number(d.cooldownMinutes),
  };
};

export const WeatherSchedulePage = () => {
  const [draft, setDraft] = useState<TDraft | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [region, setRegion] = useState('');
  const [openRunId, setOpenRunId] = useState<string | null>(null);

  const schedule = useWeatherSchedule();
  const providers = useWeatherProviders();
  const codes = useCodeBook();
  const runs = useQuery({
    queryKey: qk.weatherRuns({ size: RECENT_RUNS }),
    queryFn: () => api.weather.runs({ size: RECENT_RUNS }),
    refetchInterval: 30_000,
  });
  const save = useUpdateWeatherSetting();
  const refresh = useWeatherRefresh();
  const toast = useToastStore((s) => s.show);
  const toastError = useToastStore((s) => s.showError);

  const data = schedule.data;
  const form = draft ?? (data ? toDraft(data.setting) : null);
  const primary = (providers.data ?? []).find((p) => p.enabled) ?? null;
  const body = form ? toBody(form) : null;
  const estimate =
    body && data ? estimateMonthlyCalls(data.activeRegions, primary?.callsPerFetch ?? 1, body.intervalMinutes) : 0;
  const overQuota = !!primary && primary.monthlyQuota !== null && estimate > primary.monthlyQuota;
  const dirty = !!draft && !!data && JSON.stringify(toBody(draft)) !== JSON.stringify(toBody(toDraft(data.setting)));
  const regions = codes.group('REGION');
  const runningNow = !!data?.running || refresh.isPending;

  const fieldError = (name: string) =>
    isApiError(save.error) ? fieldErrorOf(save.error.fieldErrors, name) : undefined;
  const set = (key: keyof TDraft, value: string | boolean) => {
    if (form) setDraft({ ...form, [key]: value });
  };
  const confirmSave = () => {
    if (!body) return;
    save.mutate(body, {
      onSuccess: () => {
        setConfirming(false);
        setDraft(null);
        toast('날씨 스케줄을 저장했어요. 다음 실행부터 반영돼요.');
      },
      onError: () => setConfirming(false),
    });
  };
  const runNow = () => {
    refresh.mutate(region || undefined, {
      onSuccess: (r) =>
        toast(
          r.status === 'RUNNING'
            ? '아직 받는 중이에요. 최근 실행에서 확인하세요.'
            : `날씨 받기 끝: 성공 ${r.successCount} · 실패 ${r.failCount}`,
          r.status === 'SUCCESS' || r.status === 'RUNNING' ? 'success' : 'error',
        ),
      onError: (e) => toastError(e, '실행하지 못했어요'),
    });
  };

  return (
    <>
      <PageHeader
        title="날씨"
        desc="서버가 정해진 주기로 지역(REGION)별 날씨를 받아 저장하고, 앱은 저장된 값을 읽습니다. 지역 목록 밖 장소(좌표)·장소 검색·평년값만 앱 요청 때 공급자를 부르고 결과를 캐시합니다 (호출 이력의 '종류')."
      />
      <WeatherTabs />
      <ErrorBox error={schedule.error} onRetry={() => void schedule.refetch()} />
      {schedule.isLoading ? <Loading /> : null}
      {data && form ? (
        <>
          {!data.schedulerEnabled ? (
            <Notice tone="warn">
              서버 설정에서 주기 실행이 꺼져 있습니다 (
              <span className="mono">wearly.weather.scheduler-enabled=false</span>
              ). 아래 &quot;지금 실행&quot;으로만 받습니다.
            </Notice>
          ) : null}
          <div className="stats">
            <Stat
              label="스케줄"
              value={data.setting.enabled ? '켜짐' : '꺼짐'}
              sub={`${data.setting.intervalMinutes}분마다 · 활성 지역 ${data.activeRegions}개`}
            />
            <Stat
              label="다음 실행 예정"
              value={<span className="small">{data.nextRunAt ? fmtDateTime(data.nextRunAt) : '—'}</span>}
              sub={runningNow ? '지금 실행 중…' : undefined}
            />
            <Stat
              label="마지막 실행"
              value={data.lastRun ? <RunStatusBadge run={data.lastRun} /> : '—'}
              sub={
                data.lastRun
                  ? `${fmtDateTime(data.lastRun.finishedAt)} · 성공 ${data.lastRun.successCount} / 실패 ${data.lastRun.failCount} · ${fmtMs(runDurationMs(data.lastRun.startedAt, data.lastRun.finishedAt))}`
                  : '아직 실행한 적이 없습니다.'
              }
            />
          </div>

          <Card title="설정" className="mt-md">
            <div className="form weather-form">
              <Field label="주기 실행">
                <Switch checked={form.enabled === true} onChange={(v) => set('enabled', v)} label="사용" />
              </Field>
              <Field label="주기 (분)" hint="10~1440분" error={fieldError('intervalMinutes')} required>
                <input
                  type="number"
                  min={10}
                  max={1440}
                  value={String(form.intervalMinutes)}
                  onChange={(e) => set('intervalMinutes', e.target.value)}
                />
              </Field>
              <Field
                label="예보 일수"
                hint={
                  primary && body && body.forecastDays > primary.maxForecastDays
                    ? `${primary.displayName}는 최대 ${primary.maxForecastDays}일이라 ${primary.maxForecastDays}일만 받습니다.`
                    : '오늘 포함 1~14일. 그 뒤 날짜는 앱에서 체감 온도를 직접 고릅니다.'
                }
                error={fieldError('forecastDays')}
                required
              >
                <input
                  type="number"
                  min={1}
                  max={14}
                  value={String(form.forecastDays)}
                  onChange={(e) => set('forecastDays', e.target.value)}
                />
              </Field>
              <Field label="차단 기준 (연속 실패)" hint="1~20회" error={fieldError('failureThreshold')} required>
                <input
                  type="number"
                  min={1}
                  max={20}
                  value={String(form.failureThreshold)}
                  onChange={(e) => set('failureThreshold', e.target.value)}
                />
              </Field>
              <Field
                label="차단 시간 (분)"
                hint="끝나면 한 지역으로 다시 시험합니다"
                error={fieldError('cooldownMinutes')}
                required
              >
                <input
                  type="number"
                  min={1}
                  max={1440}
                  value={String(form.cooldownMinutes)}
                  onChange={(e) => set('cooldownMinutes', e.target.value)}
                />
              </Field>
            </div>
            <Notice tone={overQuota ? 'warn' : 'info'}>
              <div>
                예상 월 호출 수: <strong>{fmtNum(estimate)}회</strong> (활성 지역 {data.activeRegions}개 × 지역당{' '}
                {primary?.callsPerFetch ?? 1}회 × 하루 {body ? Math.round(1440 / Math.max(1, body.intervalMinutes)) : 0}
                번 × 30일)
                {primary?.monthlyQuota !== null && primary?.monthlyQuota !== undefined
                  ? ` · ${primary.displayName} 월 한도 ${fmtNum(primary.monthlyQuota)}회`
                  : ''}
                {overQuota ? ' — 한도를 넘습니다. 주기를 늘리거나 한도를 확인하세요.' : ''}
              </div>
              <div className="small">
                정기 실행만 센 값이에요. 앱의 다른 장소 날씨·장소 검색 호출은 사용자 수에 따라 더 들어요 (회원당 하루
                한도 있음).
              </div>
            </Notice>
            {save.error && !fieldError('intervalMinutes') ? <ErrorBox error={save.error} /> : null}
            <div className="row gap-sm mt-sm">
              <Button variant="primary" disabled={!dirty} onClick={() => setConfirming(true)}>
                저장
              </Button>
              <Button disabled={!dirty} onClick={() => setDraft(null)}>
                되돌리기
              </Button>
              {data.setting.updatedAt ? (
                <span className="muted small">마지막 변경 {fmtDateTime(data.setting.updatedAt)}</span>
              ) : null}
            </div>
          </Card>

          <Card title="지금 실행" className="mt-md">
            <div className="row gap-sm wrap">
              <select value={region} onChange={(e) => setRegion(e.target.value)} aria-label="지역">
                <option value="">활성 지역 전체</option>
                {regions.map((r) => (
                  <option key={r.code} value={r.code}>
                    {r.label} ({r.code}){r.active ? '' : ' · 비활성'}
                  </option>
                ))}
              </select>
              <Button variant="primary" busy={runningNow} onClick={runNow}>
                {runningNow ? '받는 중…' : '지금 실행'}
              </Button>
              <span className="muted small">스케줄이 꺼져 있어도 실행됩니다. 공급자 호출 수에 들어갑니다.</span>
            </div>
          </Card>
        </>
      ) : null}

      <Card title={`최근 실행 ${RECENT_RUNS}건`} className="mt-md">
        <ErrorBox error={runs.error} onRetry={() => void runs.refetch()} />
        {runs.data && runs.data.items.length === 0 ? <Empty>아직 실행 기록이 없습니다.</Empty> : null}
        {runs.data && runs.data.items.length > 0 ? (
          <div className="table-wrap">
            <table className="table table-hover">
              <thead>
                <tr>
                  <th>시작</th>
                  <th>트리거</th>
                  <th>요청자</th>
                  <th>지역</th>
                  <th>상태</th>
                  <th className="num">성공 / 실패 / 예비</th>
                  <th className="num">소요</th>
                </tr>
              </thead>
              <tbody>
                {runs.data.items.map((r) => (
                  <tr key={r.runId} className="clickable" onClick={() => setOpenRunId(r.runId)}>
                    <td className="nowrap small">{fmtDateTime(r.startedAt)}</td>
                    <td className="small">{TRIGGER_TEXT[r.trigger] ?? r.trigger}</td>
                    <td className="small">{r.requestedByEmail ?? <span className="muted">—</span>}</td>
                    <td className="mono small">{r.regionCode ?? '전체'}</td>
                    <td>
                      <RunStatusBadge run={r} />
                    </td>
                    <td className="num small">
                      {r.successCount} / {r.failCount} / {r.fallbackCount}
                    </td>
                    <td className="num small">{fmtMs(runDurationMs(r.startedAt, r.finishedAt))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </Card>

      {confirming && body ? (
        <Modal
          title="날씨 스케줄 저장"
          onClose={() => !save.isPending && setConfirming(false)}
          footer={
            <>
              <Button onClick={() => setConfirming(false)} disabled={save.isPending}>
                취소
              </Button>
              <Button variant="primary" busy={save.isPending} onClick={confirmSave}>
                저장
              </Button>
            </>
          }
        >
          <p>
            주기 실행 <strong>{body.enabled ? '켜짐' : '꺼짐'}</strong>, {body.intervalMinutes}분마다, 예보{' '}
            {body.forecastDays}일, 연속 {body.failureThreshold}번 실패하면 {body.cooldownMinutes}분 차단.
          </p>
          <p className="muted small">다음 실행부터 반영됩니다. 예상 월 호출 수 {fmtNum(estimate)}회.</p>
        </Modal>
      ) : null}
      {openRunId ? <RunDetailDrawer runId={openRunId} onClose={() => setOpenRunId(null)} /> : null}
    </>
  );
};
