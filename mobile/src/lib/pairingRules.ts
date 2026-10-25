// 옷장 도우미 규칙 (doc/26). 화면·스토어·expo 없이 순수 함수만 둔다 (단위 점검 대상).
// 라벨·문구, 코드 입력 정리, 코드 남은 시간, 알림을 눌렀을 때 열 화면.
import type { PairingDto, PairingPermission, ProxyAction } from '@/api/types';

/** 서버 푸시의 안드로이드 알림 채널 (backend PairingNotices.CHANNEL_ID와 같다) */
export const HELPER_CHANNEL = 'closet-helper';
/** 서버 푸시 data.type 접두사 (closet-pairing-request · -accepted · -rejected · -ended, closet-helper-digest) */
export const HELPER_TYPE_PREFIX = 'closet-';

export const PERMISSION_LABEL: Record<PairingPermission, string> = {
  VIEW: '보기만',
  EDIT: '등록·수정',
};

export const PERMISSION_DESC: Record<PairingPermission, string> = {
  VIEW: '내 옷을 볼 수만 있어요.',
  EDIT: '옷을 등록·수정하고 사용 중지할 수 있어요. 완전 삭제는 나만 할 수 있어요.',
};

export const PERMISSIONS: PairingPermission[] = ['VIEW', 'EDIT'];

export const ACTION_LABEL: Record<ProxyAction, string> = {
  ITEM_CREATE: '등록',
  ITEM_UPDATE: '수정',
  ITEM_RETIRE: '사용 중지',
  ITEM_RESTORE: '다시 사용',
};

/** 알림 data.type이 옷장 도우미 알림인지 */
export const isHelperNotice = (data: unknown): boolean => {
  const type = (data as { type?: unknown } | null | undefined)?.type;
  return typeof type === 'string' && type.startsWith(HELPER_TYPE_PREFIX);
};

/** 코드 입력·표시 정리: 대문자, 공백·하이픈을 빼고 8자까지, 4자 뒤에 하이픈 (ABCD-EFGH) */
export const formatPairingCode = (raw: string): string => {
  const n = raw.replace(/[\s-]/g, '').toUpperCase().slice(0, 8);
  return n.length > 4 ? `${n.slice(0, 4)}-${n.slice(4)}` : n;
};

/** 코드를 보낼 수 있는 길이(8자)인지 */
export const isCompleteCode = (raw: string): boolean => raw.replace(/[\s-]/g, '').length === 8;

/** 코드 남은 시간: "23시간 59분 남음" · "45분 남음" · "곧 사라져요" · "시간이 지났어요" */
export const expiresInText = (expiresAt: string, now: number): string => {
  const left = Date.parse(expiresAt) - now;
  if (!Number.isFinite(left) || left <= 0) return '시간이 지났어요';
  const minutes = Math.floor(left / 60000);
  if (minutes < 1) return '곧 사라져요';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m}분 남음`;
  return m === 0 ? `${h}시간 남음` : `${h}시간 ${m}분 남음`;
};

/** 연결 한 줄 요약: 상태·권한 */
export const pairingStatusText = (p: PairingDto): string => {
  if (p.status === 'PENDING') return p.role === 'OWNER' ? '승인을 기다려요' : '주인이 승인하길 기다려요';
  return p.permission ? PERMISSION_LABEL[p.permission] : '연결됨';
};

/** 연결 해제를 확인받을 때 문구. 끝나도 옷은 주인 옷장에 남는다 */
export const endConfirmText = (p: PairingDto): { title: string; message: string; ok: string } => {
  if (p.status === 'PENDING') {
    return { title: '요청을 취소할까요?', message: `${p.partnerName}님과의 연결 요청이 없어져요.`, ok: '취소하기' };
  }
  return {
    title: '연결을 끊을까요?',
    message: `${p.partnerName}님과의 옷장 도우미 연결이 끊겨요. 이미 등록한 옷은 주인 옷장에 그대로 남아요.`,
    ok: '끊기',
  };
};

/** 알림을 눌렀을 때 열 화면. 옷장 도우미 알림이 아니면 null */
export const helperNoticeRoute = (data: unknown): string | null => (isHelperNotice(data) ? '/helper' : null);
