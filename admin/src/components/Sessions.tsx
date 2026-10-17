// 로그인 세션 화면(pages/SessionsPage)과 회원 세션 패널(pages/UserSessionsDrawer)이 함께 쓰는 부품 (doc/16 5장)
import type { IAuthEvent } from '@/api';

import { Badge, Button, ErrorBox, Modal } from '@/components/ui';

import { useToastStore } from '@/stores/useToastStore';

import { isUnsupportedApi, useRevokeSessions } from '@/lib/queries';
import { AUTH_EVENTS, SESSION_CLIENTS } from '@/lib/sessions';
import { summarizeUserAgent } from '@/lib/userAgent';
import type { TRevokeTarget } from '@/lib/queries';

/** 로그인한 곳 배지. 모르는 값이면 원문 */
export const ClientBadge = ({ client }: { client: string | null }) => {
  if (!client) return <span className="muted">—</span>;
  const c = SESSION_CLIENTS[client];
  return (
    <Badge tone={c?.tone ?? 'gray'} title={client}>
      {c ? (
        <>
          <span aria-hidden>{c.icon}</span> {c.label}
        </>
      ) : (
        client
      )}
    </Badge>
  );
};

/** User-Agent 요약. 마우스를 올리면 원문 */
export const UaSummary = ({ userAgent }: { userAgent: string | null }) => {
  return <span title={userAgent ?? undefined}>{summarizeUserAgent(userAgent).label}</span>;
};

export const AuthEventBadge = ({ type }: { type: IAuthEvent['type'] }) => {
  const e = AUTH_EVENTS[type];
  return (
    <Badge tone={e?.tone ?? 'gray'} title={type}>
      {e?.label ?? type}
    </Badge>
  );
};

/** 강제 로그아웃 확인 → 실행 → 토스트. 성공하면 닫는다 */
export const RevokeSessionModal = ({ target, onClose }: { target: TRevokeTarget; onClose: () => void }) => {
  const toast = useToastStore((s) => s.show);
  const revoke = useRevokeSessions();

  const isUser = target.kind === 'user';
  const error = revoke.error && !isUnsupportedApi(revoke.error) ? revoke.error : null;

  const handleConfirm = () => {
    revoke.mutate(target, {
      onSuccess: () => {
        toast(
          isUser
            ? `${target.who}: 모든 기기에서 로그아웃시켰습니다.`
            : `${target.who}: ${target.device} 세션을 로그아웃시켰습니다.`,
        );
        onClose();
      },
      onError: (e) => {
        if (!isUnsupportedApi(e)) return;
        toast('서버가 아직 이 기능을 모릅니다. 백엔드를 업데이트해 주세요.', 'error');
        onClose();
      },
    });
  };

  return (
    <Modal
      title={isUser ? '모든 기기에서 로그아웃' : '로그아웃 시키기'}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button variant="danger" busy={revoke.isPending} onClick={handleConfirm}>
            {isUser ? '모두 로그아웃' : '로그아웃 시키기'}
          </Button>
        </>
      }
    >
      {target.kind === 'user' ? (
        <p>
          <strong>{target.who}</strong> 회원의 로그인 세션
          {target.count !== null ? ` ${target.count}개를` : '을'} 모두 끊습니다. 앱·웹·관리자 사이트 모두 다음 요청에서
          로그인 화면으로 돌아갑니다.
        </p>
      ) : (
        <p>
          <strong>{target.who}</strong> 회원의 <strong>{target.device}</strong> 세션을 끊습니다. 그 기기는 다음 요청에서
          로그인 화면으로 돌아가고, 다른 기기의 로그인은 그대로입니다.
        </p>
      )}
      {error ? <ErrorBox error={error} /> : null}
    </Modal>
  );
};
