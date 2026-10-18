// 날씨 > 공급자: 사용 여부·월 한도·최대 예보 일수, 상태(정상·차단·키 확인 필요), 이번 달 사용량, 차단 해제, 시험 호출 (doc/17 4·6장).
// 지금은 WeatherAPI.com 하나다. 예비 공급자(OpenWeatherMap)는 다음 단계에서 같은 목록에 붙는다.
import { useState } from 'react';

import { api, isApiError } from '@/api';
import type { IWeatherProvider, IWeatherTestResult } from '@/api';

import { Badge, Button, Card, Drawer, Empty, ErrorBox, Field, JsonView, Loading, Notice, PageHeader, Switch } from '@/components/ui';
import { CurrentLine, DaysTable, WeatherTabs } from '@/pages/weather/WeatherParts';

import { useToastStore } from '@/stores/useToastStore';

import { useMutation } from '@tanstack/react-query';

import { fmtDateTime, fmtMs, fmtNum } from '@/lib/format';
import { useCodeBook, useResetWeatherProvider, useUpdateWeatherProvider, useWeatherProviders } from '@/lib/queries';
import {
  ERROR_TEXT,
  KEY_TEXT,
  STATUS_TEXT,
  STATUS_TONE,
  statusDetail,
  usageLevel,
  usagePercent,
} from '@/lib/weather';

const UsageBar = ({ used, quota }: { used: number; quota: number | null }) => {
  const level = usageLevel(used, quota);
  return (
    <div className="usage">
      <div className="usage-text">
        이번 달 <strong>{fmtNum(used)}</strong>회{quota !== null ? ` / ${fmtNum(quota)}회` : ' (한도 없음)'}
        {level === 'over' ? <Badge tone="red">한도 도달 · 건너뜀</Badge> : null}
      </div>
      {quota !== null ? (
        <div className="usage-track" aria-hidden>
          <div className={`usage-fill usage-${level}`} style={{ width: `${usagePercent(used, quota)}%` }} />
        </div>
      ) : null}
    </div>
  );
};

const ProviderCard = ({ p, onEdit }: { p: IWeatherProvider; onEdit: (p: IWeatherProvider) => void }) => {
  const update = useUpdateWeatherProvider();
  const reset = useResetWeatherProvider();
  const toast = useToastStore((s) => s.show);

  const detail = statusDetail(p.status, p.keyState, p.circuitOpenUntil ? fmtDateTime(p.circuitOpenUntil) : null);

  const toggle = (enabled: boolean) => {
    update.mutate(
      { code: p.code, patch: { enabled } },
      {
        onSuccess: () => toast(`${p.displayName}을(를) ${enabled ? '켰어요' : '껐어요'}.`),
        onError: (e) => toast(isApiError(e) ? e.message : '저장하지 못했어요.', 'error'),
      },
    );
  };
  const unblock = () => {
    reset.mutate(p.code, {
      onSuccess: () => toast(`${p.displayName} 차단을 풀었어요. 다음 실행부터 다시 부릅니다.`),
      onError: (e) => toast(isApiError(e) ? e.message : '차단을 풀지 못했어요.', 'error'),
    });
  };

  return (
    <Card
      title={
        <>
          {p.displayName} <span className="mono muted small">{p.code}</span>{' '}
          <Badge tone="ink" title="작을수록 먼저 부른다">
            우선순위 {p.priority}
          </Badge>
        </>
      }
      actions={
        <div className="row gap-sm">
          <Switch checked={p.enabled} disabled={update.isPending} onChange={toggle} label="사용" />
          <Button size="sm" onClick={() => onEdit(p)}>
            수정
          </Button>
        </div>
      }
    >
      <div className="row gap-sm wrap">
        <Badge tone={STATUS_TONE[p.status]}>{STATUS_TEXT[p.status] ?? p.status}</Badge>
        <Badge tone={p.keyState === 'MISSING' || p.keyState === 'UNSUPPORTED' ? 'red' : 'gray'}>
          {KEY_TEXT[p.keyState] ?? p.keyState}
        </Badge>
        {p.usable ? <Badge tone="green">지금 부를 수 있음</Badge> : <Badge tone="gray">건너뜀</Badge>}
        {p.status !== 'OK' ? (
          <Button size="sm" variant="danger" busy={reset.isPending} onClick={unblock}>
            차단 해제
          </Button>
        ) : null}
      </div>
      {detail ? <p className="small mt-sm">{detail}</p> : null}
      <UsageBar used={p.monthUsage} quota={p.monthlyQuota} />
      <dl className="kv mt-sm">
        <dt>서버 설정 키</dt>
        <dd className="mono">wearly.weather.keys.{p.keyName}</dd>
        <dt>최대 예보 일수</dt>
        <dd>{p.maxForecastDays}일 (한 지역에 {p.callsPerFetch}회 호출)</dd>
        <dt>연속 실패</dt>
        <dd>{p.consecutiveFailures}회</dd>
        <dt>마지막 성공</dt>
        <dd>{fmtDateTime(p.lastSuccessAt)}</dd>
        <dt>마지막 실패</dt>
        <dd>
          {fmtDateTime(p.lastFailureAt)}
          {p.lastError ? <div className="small text-danger">{p.lastError}</div> : null}
        </dd>
      </dl>
    </Card>
  );
};

const EditDrawer = ({ p, onClose }: { p: IWeatherProvider; onClose: () => void }) => {
  const [quota, setQuota] = useState(p.monthlyQuota === null ? '' : String(p.monthlyQuota));
  const [days, setDays] = useState(String(p.maxForecastDays));
  const [priority, setPriority] = useState(String(p.priority));

  const update = useUpdateWeatherProvider();
  const toast = useToastStore((s) => s.show);

  const fieldError = (name: string) =>
    isApiError(update.error) ? update.error.fieldErrors.find((f) => f.field === name)?.message : undefined;
  const submit = () => {
    update.mutate(
      {
        code: p.code,
        patch: {
          monthlyQuota: quota.trim() === '' ? null : Number(quota),
          maxForecastDays: Number(days),
          priority: Number(priority),
        },
      },
      {
        onSuccess: () => {
          toast(`${p.displayName} 설정을 저장했어요.`);
          onClose();
        },
      },
    );
  };

  return (
    <Drawer
      title={`${p.displayName} 수정`}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button variant="primary" busy={update.isPending} onClick={submit}>
            저장
          </Button>
        </>
      }
    >
      <div className="form">
        <Field label="월 호출 한도" hint="가입한 플랜의 월 한도. 비우면 한도 없음 (실패한 호출도 셉니다)" error={fieldError('monthlyQuota')}>
          <input type="number" min={0} value={quota} onChange={(e) => setQuota(e.target.value)} placeholder="한도 없음" />
        </Field>
        <Field label="최대 예보 일수" hint="WeatherAPI.com 무료 플랜은 3일" error={fieldError('maxForecastDays')} required>
          <input type="number" min={1} max={14} value={days} onChange={(e) => setDays(e.target.value)} />
        </Field>
        <Field label="우선순위" hint="작을수록 먼저 부릅니다 (예비 공급자를 붙이면 의미가 생깁니다)" error={fieldError('priority')} required>
          <input type="number" min={0} max={10000} value={priority} onChange={(e) => setPriority(e.target.value)} />
        </Field>
        {update.error && !isApiError(update.error) ? <ErrorBox error={update.error} /> : null}
        {isApiError(update.error) && update.error.fieldErrors.length === 0 ? <ErrorBox error={update.error} /> : null}
      </div>
    </Drawer>
  );
};

const TestCard = ({ providers }: { providers: IWeatherProvider[] }) => {
  const [code, setCode] = useState(providers[0]?.code ?? '');
  const [region, setRegion] = useState('SEOUL');

  const codes = useCodeBook();
  const test = useMutation({ mutationFn: () => api.weather.test(code, region) });

  const result: IWeatherTestResult | undefined = test.data;
  return (
    <Card title="시험 호출" className="mt-md">
      <p className="muted small">
        공급자 하나로 한 지역을 불러 봅니다. 저장된 날씨와 공급자 상태는 바뀌지 않고, 호출 이력에는 &quot;시험 호출&quot;로 남습니다.
        사용 여부·차단과 상관없이 부르므로 키를 고친 뒤 확인할 때 씁니다.
      </p>
      <div className="row gap-sm wrap">
        <select value={code} onChange={(e) => setCode(e.target.value)} aria-label="공급자">
          {providers.map((p) => (
            <option key={p.code} value={p.code}>
              {p.displayName}
            </option>
          ))}
        </select>
        <select value={region} onChange={(e) => setRegion(e.target.value)} aria-label="지역">
          {codes.group('REGION').map((r) => (
            <option key={r.code} value={r.code}>
              {r.label} ({r.code})
            </option>
          ))}
        </select>
        <Button variant="primary" busy={test.isPending} disabled={!code || !region} onClick={() => test.mutate()}>
          불러 보기
        </Button>
      </div>
      <ErrorBox error={test.error} />
      {result ? (
        <div className="mt-sm">
          <dl className="kv">
            <dt>결과</dt>
            <dd>
              {result.success ? <Badge tone="green">성공</Badge> : <Badge tone="red">실패</Badge>}{' '}
              <span className="muted small">
                HTTP {result.httpStatus ?? '—'} · {fmtMs(result.durationMs)} · 실행 #{result.runId}
              </span>
            </dd>
            {result.success ? (
              <>
                <dt>장소</dt>
                <dd>{result.locationName ?? '—'}</dd>
                <dt>지금</dt>
                <dd>
                  <CurrentLine current={result.current} />
                </dd>
              </>
            ) : (
              <>
                <dt>오류</dt>
                <dd className="text-danger">
                  {result.errorCode ? (ERROR_TEXT[result.errorCode] ?? result.errorCode) : '—'}
                  <div className="small">{result.errorMessage}</div>
                </dd>
              </>
            )}
          </dl>
          {result.success ? <DaysTable days={result.days} /> : null}
          <details className="mt-sm">
            <summary className="muted small">원본 응답</summary>
            <JsonView value={result.raw} maxHeight={360} />
          </details>
        </div>
      ) : null}
    </Card>
  );
};

export const WeatherProvidersPage = () => {
  const [editing, setEditing] = useState<IWeatherProvider | null>(null);

  const { data, error, isLoading, refetch } = useWeatherProviders();

  return (
    <>
      <PageHeader title="날씨" desc="날씨 공급자. 키 값은 서버 설정에만 있고 화면에는 키 이름과 상태만 보입니다." />
      <WeatherTabs />
      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {data && data.length === 0 ? <Empty>공급자가 없습니다.</Empty> : null}
      {data?.map((p) => (
        <ProviderCard key={p.code} p={p} onEdit={setEditing} />
      ))}
      {data && data.length === 1 ? (
        <Notice tone="info">
          지금은 공급자가 하나라 실패하면 그 지역은 이전 값을 그대로 둡니다. 예비 공급자(OpenWeatherMap)는 다음 단계에서
          붙입니다.
        </Notice>
      ) : null}
      {data && data.length > 0 ? <TestCard providers={data} /> : null}
      {editing ? <EditDrawer p={editing} onClose={() => setEditing(null)} /> : null}
    </>
  );
};
