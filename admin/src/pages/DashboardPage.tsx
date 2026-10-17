import { api } from '@/api';

import { Badge, Card, Empty, ErrorBox, Loading, PageHeader, Stat } from '@/components/ui';

import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { fmtMs, fmtNum, fmtPercent } from '@/lib/format';
import { qk, useProviders } from '@/lib/queries';

export const DashboardPage = () => {
  const { data, error, isLoading, refetch, isFetching } = useQuery({
    queryKey: qk.dashboard,
    queryFn: () => api.dashboard(),
  });
  const providers = useProviders();

  const usable = providers.data?.filter((p) => p.usable) ?? [];

  const nameOf = (id: string) => providers.data?.find((p) => p.providerId === id)?.displayName ?? id;

  return (
    <>
      <PageHeader
        title="대시보드"
        desc="회원·옷장·AI 분석 현황"
        actions={
          <button type="button" className="btn btn-secondary" onClick={() => void refetch()} disabled={isFetching}>
            새로 고침
          </button>
        }
      />
      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {data ? (
        <>
          <div className="stats">
            <Stat label="회원" value={fmtNum(data.users)} sub={`최근 7일 신규 ${fmtNum(data.newUsers7d)}명`} />
            <Stat
              label="옷장 아이템"
              value={fmtNum(data.items)}
              sub={data.users ? `회원당 ${(data.items / data.users).toFixed(1)}개` : undefined}
            />
            <Stat
              label="오늘 AI 호출"
              value={fmtNum(data.aiToday.total)}
              sub={`성공 ${fmtNum(data.aiToday.success)} · 성공률 ${fmtPercent(data.aiToday.success, data.aiToday.total)}`}
            />
            <Stat
              label="오늘 AI 실패"
              value={
                <span className={data.aiToday.failed > 0 ? 'text-danger' : undefined}>
                  {fmtNum(data.aiToday.failed)}
                </span>
              }
              sub={
                <Link to="/ai/logs?status=FAILED" className="link">
                  실패 기록 보기
                </Link>
              }
            />
            {/* doc/16 4장. 예전 서버는 이 값을 주지 않으므로 없으면 카드를 그리지 않는다 */}
            {typeof data.activeSessions === 'number' ? (
              <Stat
                label="로그인 세션"
                value={fmtNum(data.activeSessions)}
                sub={
                  <Link to="/sessions" className="link">
                    로그인 사용자 보기
                  </Link>
                }
              />
            ) : null}
            {typeof data.activeUsers24h === 'number' ? (
              <Stat
                label="24시간 활동 회원"
                value={fmtNum(data.activeUsers24h)}
                sub="최근 24시간 안에 앱·웹을 쓴 회원"
              />
            ) : null}
          </div>

          <Card title="공급자별 최근 7일">
            {data.aiByProvider7d.length === 0 ? (
              <Empty>최근 7일 AI 호출이 없습니다.</Empty>
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>공급자</th>
                      <th className="num">성공</th>
                      <th className="num">실패</th>
                      <th className="num">성공률</th>
                      <th className="num">평균 지연</th>
                      <th className="num">입력 토큰</th>
                      <th className="num">출력 토큰</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.aiByProvider7d.map((p) => (
                      <tr key={p.providerId}>
                        <td>
                          <strong>{nameOf(p.providerId)}</strong> <span className="mono muted">{p.providerId}</span>
                        </td>
                        <td className="num">{fmtNum(p.success)}</td>
                        <td className={`num ${p.failed ? 'text-danger' : ''}`}>{fmtNum(p.failed)}</td>
                        <td className="num">{fmtPercent(p.success, p.success + p.failed)}</td>
                        <td className="num">{fmtMs(p.avgLatencyMs)}</td>
                        <td className="num">{fmtNum(p.inputTokens)}</td>
                        <td className="num">{fmtNum(p.outputTokens)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      ) : null}

      <Card
        title="지금 쓸 수 있는 AI 공급자"
        actions={
          <Link to="/ai/providers" className="link">
            공급자 관리
          </Link>
        }
      >
        {providers.isLoading ? (
          <Loading />
        ) : usable.length === 0 ? (
          <Empty>
            쓸 수 있는 공급자가 없습니다. 사용 켜기·모델·API 키(웹 입력 또는 서버 설정 키)를 확인하세요. (서버를
            목업으로 띄웠다면 목업이 쓰입니다)
          </Empty>
        ) : (
          <div className="row gap-sm wrap">
            {usable
              .slice()
              .sort((a, b) => a.priority - b.priority)
              .map((p, i) => (
                <Badge key={p.providerId} tone={i === 0 ? 'green' : 'gray'}>
                  {i + 1}. {p.displayName} · {p.model}
                </Badge>
              ))}
          </div>
        )}
      </Card>
    </>
  );
};
