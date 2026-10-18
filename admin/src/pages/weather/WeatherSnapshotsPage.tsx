// 날씨 > 저장된 날씨: 지역별로 DB에 저장된 최신 값. 앱은 이 값만 읽는다 (doc/17 2·5장).
// 상세에서 앱 응답 미리보기(GET /weather 모양)와 공급자 원본을 비교하고, 그 지역만 새로 받을 수 있다.
import { useState } from 'react';

import { api } from '@/api';
import type { IWeatherSnapshot } from '@/api';

import { Badge, Button, Card, Drawer, Empty, ErrorBox, JsonView, Loading, PageHeader } from '@/components/ui';
import { CurrentLine, DaysTable, WeatherTabs } from '@/pages/weather/WeatherParts';

import { useToastStore } from '@/stores/useToastStore';

import { useQuery } from '@tanstack/react-query';

import { fmtDateTime } from '@/lib/format';
import { qk, useWeatherRefresh } from '@/lib/queries';

/** 앱이 받는 모양 (GET /api/v1/weather). 공급자 이름은 앱에 주지 않는다 */
const appPreview = (s: IWeatherSnapshot) => {
  return {
    region: { code: s.regionCode, label: s.regionLabel },
    current: s.current,
    days: s.days,
    fetchedAt: s.fetchedAt,
    stale: s.stale,
  };
};

const SnapshotDrawer = ({ region, onClose }: { region: string; onClose: () => void }) => {
  const { data, error, isLoading, refetch } = useQuery({
    queryKey: qk.weatherSnapshot(region),
    queryFn: () => api.weather.snapshot(region),
  });
  const refresh = useWeatherRefresh();
  const toast = useToastStore((s) => s.show);
  const toastError = useToastStore((s) => s.showError);

  const reload = () => {
    refresh.mutate(region, {
      onSuccess: (r) =>
        r.status === 'SUCCESS'
          ? toast('새로 받았어요.')
          : toast('받지 못했어요. 호출 이력을 확인하세요.', 'error'),
      onError: (e) => toastError(e, '실행하지 못했어요'),
    });
  };

  return (
    <Drawer
      title={data ? `${data.regionLabel} 저장된 날씨` : '저장된 날씨'}
      onClose={onClose}
      wide
      footer={
        <Button variant="primary" busy={refresh.isPending} onClick={reload}>
          이 지역 새로 받기
        </Button>
      }
    >
      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {data ? (
        <>
          <dl className="kv">
            <dt>지역</dt>
            <dd>
              {data.regionLabel} <span className="mono muted small">{data.regionCode}</span>
              {data.regionActive ? null : <Badge tone="gray">비활성</Badge>}
            </dd>
            <dt>공급자</dt>
            <dd className="mono">{data.providerCode ?? '—'}</dd>
            <dt>저장 시각</dt>
            <dd>
              {fmtDateTime(data.fetchedAt)} {data.stale ? <Badge tone="red">오래됨</Badge> : null}
            </dd>
            <dt>지금</dt>
            <dd>
              <CurrentLine current={data.current} />
              {data.current?.observedAt ? (
                <div className="muted small">공급자 관측 {fmtDateTime(data.current.observedAt)}</div>
              ) : null}
            </dd>
            {data.lastError ? (
              <>
                <dt>마지막 오류</dt>
                <dd className="text-danger small">
                  {data.lastError} <span className="muted">({fmtDateTime(data.lastErrorAt)})</span>
                </dd>
              </>
            ) : null}
          </dl>
          <h3 className="section-title">일별 예보</h3>
          <DaysTable days={data.days} />
          <h3 className="section-title">앱 응답 미리보기</h3>
          <JsonView value={appPreview(data)} maxHeight={280} />
          <details className="mt-sm">
            <summary className="muted small">공급자 원본 (시간별 예보 제외)</summary>
            <JsonView value={data.raw ?? {}} maxHeight={420} />
          </details>
        </>
      ) : null}
    </Drawer>
  );
};

export const WeatherSnapshotsPage = () => {
  const [open, setOpen] = useState<string | null>(null);

  const { data, error, isLoading, isFetching, refetch } = useQuery({
    queryKey: qk.weatherSnapshots,
    queryFn: () => api.weather.snapshots(),
  });

  return (
    <>
      <PageHeader
        title="날씨"
        desc="지역(코드 관리의 '날씨 지역')별로 저장된 최신 날씨. 받기에 실패하면 이전 값을 그대로 두고 오류만 남깁니다."
      />
      <WeatherTabs />
      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isFetching ? 'is-fetching' : ''}>
          {data.length === 0 ? (
            <Empty>지역이 없습니다. 코드 관리에서 &apos;날씨 지역&apos;을 확인하세요.</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table table-hover">
                <thead>
                  <tr>
                    <th>지역</th>
                    <th>지금</th>
                    <th>공급자</th>
                    <th className="num">예보</th>
                    <th>저장 시각</th>
                    <th>마지막 오류</th>
                  </tr>
                </thead>
                <tbody>
                  {data.map((s) => (
                    <tr
                      key={s.regionCode}
                      className={s.regionActive ? 'clickable' : 'clickable row-inactive'}
                      onClick={() => setOpen(s.regionCode)}
                    >
                      <td>
                        {s.regionLabel} <span className="mono muted small">{s.regionCode}</span>
                        {s.regionActive ? null : <Badge tone="gray">비활성</Badge>}
                      </td>
                      <td>
                        <CurrentLine current={s.current} />
                      </td>
                      <td className="mono small">{s.providerCode ?? '—'}</td>
                      <td className="num small">{s.days.length}일</td>
                      <td className="nowrap small">
                        {s.fetchedAt ? fmtDateTime(s.fetchedAt) : <span className="muted">받은 적 없음</span>}{' '}
                        {s.fetchedAt && s.stale ? <Badge tone="red">오래됨</Badge> : null}
                      </td>
                      <td className="small ellipsis text-danger" title={s.lastError ?? undefined}>
                        {s.lastError ?? ''}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      ) : null}
      {open ? <SnapshotDrawer region={open} onClose={() => setOpen(null)} /> : null}
    </>
  );
};
