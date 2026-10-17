// 회원 화면 > 로그인 기기: 그 회원의 세션과 최근 로그인 이력, 강제 로그아웃 (doc/16 5장)
import { useState } from 'react';

import type { IAdminSession, IAdminUser } from '@/api';

import { AuthEventBadge, ClientBadge, RevokeSessionModal, UaSummary } from '@/components/Sessions';
import { Badge, Button, Drawer, Empty, ErrorBox, Loading, Notice } from '@/components/ui';

import { fmtDateTime, fmtRelative } from '@/lib/format';
import { isUnsupportedApi, useUserSessions } from '@/lib/queries';
import { sessionDeviceText } from '@/lib/sessions';
import { summarizeUserAgent } from '@/lib/userAgent';
import type { TRevokeTarget } from '@/lib/queries';

export const UserSessionsDrawer = ({
  user,
  isMe,
  onClose,
}: {
  user: IAdminUser;
  /** 로그인한 관리자 자신 — "모든 기기에서 로그아웃"을 막는다 */
  isMe: boolean;
  onClose: () => void;
}) => {
  const [target, setTarget] = useState<TRevokeTarget | null>(null);

  const { data, error, isLoading, refetch, dataUpdatedAt } = useUserSessions(user.id);

  const who = user.name || user.email || `#${user.id}`;
  const unsupported = isUnsupportedApi(error);
  const sessions = data?.sessions ?? [];

  const revokeSession = (s: IAdminSession) => {
    setTarget({
      kind: 'session',
      sessionId: s.sessionId,
      who,
      device: sessionDeviceText(s.client, summarizeUserAgent(s.userAgent).label),
    });
  };
  const revokeAll = () => {
    setTarget({ kind: 'user', userId: user.id, who, count: sessions.length });
  };

  return (
    <Drawer title={`${who} · 로그인 기기`} onClose={onClose} wide>
      <dl className="kv">
        <dt>이메일</dt>
        <dd>{user.email ?? '—'}</dd>
        <dt>회원 ID</dt>
        <dd className="mono">{user.id}</dd>
      </dl>

      {unsupported ? (
        <Notice tone="warn">
          서버가 아직 이 기능을 모릅니다. 백엔드에 회원별 세션 API(
          <span className="mono">GET /admin/users/{'{id}'}/sessions</span>, doc/16)가 들어가면 여기에 보입니다.
        </Notice>
      ) : (
        <ErrorBox error={error} onRetry={() => void refetch()} />
      )}
      {isLoading ? <Loading /> : null}

      {data ? (
        <>
          <div className="row gap-sm">
            <h3 className="section-title grow">로그인 세션 {sessions.length}개</h3>
            <Button
              size="sm"
              variant="ghost"
              className="text-danger"
              disabled={isMe || sessions.length === 0}
              title={isMe ? '자기 자신은 모든 기기에서 로그아웃할 수 없습니다' : undefined}
              onClick={revokeAll}
            >
              모든 기기에서 로그아웃
            </Button>
          </div>
          {sessions.length === 0 ? (
            <Empty>로그인한 기기가 없습니다.</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>기기</th>
                    <th>기기 요약</th>
                    <th>IP</th>
                    <th>로그인</th>
                    <th>마지막 활동</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {sessions.map((s) => (
                    <tr key={s.sessionId}>
                      <td>
                        <ClientBadge client={s.client} />
                      </td>
                      <td className="small">
                        <UaSummary userAgent={s.userAgent} />
                      </td>
                      <td className="mono small">{s.ip ?? '—'}</td>
                      <td className="nowrap small">{fmtDateTime(s.createdAt)}</td>
                      <td className="nowrap small" title={fmtDateTime(s.lastSeenAt ?? s.lastRefreshAt ?? s.createdAt)}>
                        {fmtRelative(s.lastSeenAt ?? s.lastRefreshAt ?? s.createdAt, dataUpdatedAt)}
                      </td>
                      <td className="actions">
                        {s.current ? <Badge tone="green">현재 세션</Badge> : null}{' '}
                        <Button
                          size="sm"
                          variant="ghost"
                          className="text-danger"
                          disabled={s.current}
                          title={s.current ? '지금 쓰고 있는 내 세션은 끊을 수 없습니다' : undefined}
                          onClick={() => revokeSession(s)}
                        >
                          로그아웃 시키기
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <h3 className="section-title mt-md">최근 로그인 이력</h3>
          {data.events.length === 0 ? (
            <Empty>기록이 없습니다.</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>시각</th>
                    <th>종류</th>
                    <th>기기</th>
                    <th>기기 요약</th>
                    <th>IP</th>
                  </tr>
                </thead>
                <tbody>
                  {data.events.map((ev, i) => (
                    <tr key={`${ev.createdAt}-${ev.type}-${i}`}>
                      <td className="nowrap small">{fmtDateTime(ev.createdAt)}</td>
                      <td>
                        <AuthEventBadge type={ev.type} />
                      </td>
                      <td>
                        <ClientBadge client={ev.client} />
                      </td>
                      <td className="small">
                        <UaSummary userAgent={ev.userAgent} />
                      </td>
                      <td className="mono small">{ev.ip ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      ) : null}

      {target ? <RevokeSessionModal target={target} onClose={() => setTarget(null)} /> : null}
    </Drawer>
  );
};
